package com.mafuyu404.taczaddon.inventory;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

/**
 * Shared loaded-only physical inventory source policy for Gunsmith crafting and attachment
 * refitting.
 *
 * <p>Resolution priority:
 *
 * <pre>
 * Sophisticated Backpack block
 *     -> existing isolated Sophisticated facade
 *
 * generic NeoForge BLOCK ItemHandler capability
 *     -> request-scoped capability cache
 *
 * vanilla Container fallback
 *     -> ContainerMaster / InvWrapper
 * </pre>
 *
 * <p>The returned handler always implements {@link IItemHandlerModifiable}. For a native modifiable
 * handler, setStackInSlot delegates directly. For a generic IItemHandler, the wrapper implements
 * rollback by restoring only the missing delta into the original slot. Slots that cannot accept
 * their own current stack are treated as read-ineligible/mutation-ineligible, preventing
 * output-only machine slots from entering transactions that could not safely compensate an
 * extraction.
 */
public final class NearbyInventorySourceResolver {

    private static final String SOPHISTICATED_BACKPACKS_NAMESPACE = "sophisticatedbackpacks";

    private NearbyInventorySourceResolver() {}

    public enum SourceKind {
        CONTAINER,
        SOPHISTICATED_BACKPACK
    }

    public record Source(BlockPos pos, SourceKind kind, IItemHandler handler)
            implements InventorySource {
        public void synchronize(ServerPlayer player) {
            markChanged();
        }

        public boolean isValid() {
            if (handler instanceof PhysicalSourceFilter filter) return filter.isValid();
            return handler instanceof BlockInventoryHandler block && block.isValid();
        }

        public Object backendIdentity() {
            if (handler instanceof PhysicalSourceFilter filter) return filter.backendIdentity();
            if (!(handler instanceof BlockInventoryHandler block)) {
                return handler;
            }

            return block.backendIdentity();
        }

        public void markChanged() {
            if (handler instanceof PhysicalSourceFilter filter) {
                filter.markChanged();
                return;
            }
            if (handler instanceof BlockInventoryHandler block) {
                block.markChanged();
            }
        }
    }

    public static boolean inRange(
            BlockPos anchor, BlockPos pos, int horizontalRadius, int verticalRadius) {
        return Math.abs((long) pos.getX() - anchor.getX()) <= horizontalRadius
                && Math.abs((long) pos.getZ() - anchor.getZ()) <= horizontalRadius
                && Math.abs((long) pos.getY() - anchor.getY()) <= verticalRadius;
    }

    public static List<Source> resolve(
            ServerPlayer player, BlockPos anchor, int horizontalRadius, int verticalRadius) {
        int radius = Math.max(0, Math.min(horizontalRadius, 16));

        int vertical = Math.max(0, Math.min(verticalRadius, 1));

        ServerLevel level = player.serverLevel();

        BlockPos origin = anchor.immutable();

        List<BlockPos> positions = orderedPositions(origin, radius, vertical);

        ArrayList<Source> result = new ArrayList<>();

        Set<Object> seenBackends = Collections.newSetFromMap(new IdentityHashMap<>());

        for (BlockPos pos : positions) {
            /*
             * Match the 1.20.1 source policy:
             * the Gunsmith block / player position itself is not a nearby
             * external inventory source.
             */
            if (pos.equals(origin) || !level.isLoaded(pos)) {
                continue;
            }

            try {
                BlockState state = level.getBlockState(pos);

                BlockEntity blockEntity = level.getBlockEntity(pos);

                /*
                 * Sophisticated Backpacks MUST remain behind its existing
                 * compatibility/circuit-breaker facade.
                 *
                 * Never fall through to the generic NeoForge capability for a
                 * sophisticatedbackpacks block when the facade reports it as
                 * unavailable, otherwise an ABI-broken Soph integration could
                 * accidentally be bypassed.
                 */
                boolean sophisticatedBackpack = InventoryIntegrations.isBackpackBlock(level, pos);

                if (sophisticatedBackpack) {
                    if (blockEntity == null || blockEntity.isRemoved()) {
                        continue;
                    }

                    InventoryIntegrations.forEachBlockBackpackHandler(
                            player,
                            pos,
                            handler -> {
                                if (handler == null) {
                                    return;
                                }

                                BlockInventoryHandler wrapped =
                                        BlockInventoryHandler.forSophisticated(
                                                player, pos, state, blockEntity, handler);

                                addIfUsable(
                                        result,
                                        seenBackends,
                                        new Source(
                                                pos, SourceKind.SOPHISTICATED_BACKPACK, wrapped));
                            });

                    continue;
                }

                /*
                 * If the actual block belongs to Sophisticated Backpacks but
                 * the facade could not recognize/use it, fail closed instead
                 * of reaching the mod through generic block capabilities.
                 */
                if (isSophisticatedBackpacksBlock(state)) {
                    continue;
                }

                /*
                 * Primary generic NeoForge path.
                 *
                 * Query first so we only allocate a BlockCapabilityCache for
                 * positions that actually expose an item handler.
                 */
                IItemHandler capability =
                        level.getCapability(Capabilities.ItemHandler.BLOCK, pos, null);

                if (capability != null) {
                    BlockInventoryHandler wrapped =
                            BlockInventoryHandler.forCapability(player, pos, state, blockEntity);

                    if (wrapped != null) {
                        addIfUsable(
                                result,
                                seenBackends,
                                new Source(pos, SourceKind.CONTAINER, wrapped));
                    }

                    /*
                     * A capability existed at resolution time. If it vanished
                     * before the cache could bind, fail closed for this request
                     * rather than silently switching backend type.
                     */
                    continue;
                }

                /*
                 * Conservative vanilla Container fallback.
                 */
                if (blockEntity == null || blockEntity.isRemoved()) {
                    continue;
                }

                ContainerMaster.getContainerHandler(level, pos)
                        .ifPresent(
                                handler -> {
                                    BlockInventoryHandler wrapped =
                                            BlockInventoryHandler.forContainer(
                                                    player, pos, state, blockEntity, handler);

                                    addIfUsable(
                                            result,
                                            seenBackends,
                                            new Source(pos, SourceKind.CONTAINER, wrapped));
                                });
            } catch (RuntimeException exception) {
                com.mojang.logging.LogUtils.getLogger()
                        .warn("Skipping unavailable nearby inventory source at {}", pos, exception);
            }
        }

        return PhysicalSourceFilter.filter(player, result);
    }

    private static List<BlockPos> orderedPositions(
            BlockPos origin, int horizontalRadius, int verticalRadius) {
        BlockPos min = origin.offset(-horizontalRadius, -verticalRadius, -horizontalRadius);

        BlockPos max = origin.offset(horizontalRadius, verticalRadius, horizontalRadius);

        ArrayList<BlockPos> positions = new ArrayList<>();

        for (BlockPos mutable : BlockPos.betweenClosed(min, max)) {
            positions.add(mutable.immutable());
        }

        /*
         * Match 1.20.1 deterministic source ordering.
         *
         * This affects both which inventory is consumed first and the order
         * external attachment candidates are displayed in.
         */
        positions.sort(Comparator.comparingLong(BlockPos::asLong));

        return positions;
    }

    private static void addIfUsable(List<Source> result, Set<Object> seenBackends, Source source) {
        try {
            if (!source.isValid() || source.handler().getSlots() <= 0) {
                return;
            }

            Object identity = source.backendIdentity();

            if (!seenBackends.add(identity)) {
                return;
            }

            result.add(source);
        } catch (RuntimeException exception) {
            com.mojang.logging.LogUtils.getLogger()
                    .warn("Skipping unusable nearby source at {}", source.pos(), exception);
        }
    }

    private static boolean isSophisticatedBackpacksBlock(BlockState state) {
        var id = BuiltInRegistries.BLOCK.getKey(state.getBlock());

        return id != null && SOPHISTICATED_BACKPACKS_NAMESPACE.equals(id.getNamespace());
    }
}
