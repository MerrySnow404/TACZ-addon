package com.mafuyu404.taczaddon.storage;

import com.mafuyu404.taczaddon.storage.api.CraftingItemSource;
import com.mafuyu404.taczaddon.storage.api.CraftingSourceKey;
import com.mafuyu404.taczaddon.storage.api.PhysicalSlot;

import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.slf4j.Logger;

import java.util.*;

/**
 * Shared server-side scanner for nearby loaded block inventories.
 *
 * This class owns only the generic physical-source discovery contract. It
 * never enforces a Gunsmith presentation limit and never loads chunks. Callers
 * are responsible for their own display policies and authoritative anchors.
 */
public final class NearbyInventorySourceResolver {
    private static final Logger LOGGER = LogUtils.getLogger();

    private NearbyInventorySourceResolver() {
    }

    public static List<CraftingItemSource> resolve(
            ServerPlayer player,
            BlockPos anchor,
            int horizontalRadius,
            int verticalRadius
    ) {
        return resolve(player, anchor, horizontalRadius, verticalRadius, false);
    }

    public static List<CraftingItemSource> resolveGeneric(
            ServerPlayer player, BlockPos anchor, int horizontalRadius, int verticalRadius
    ) {
        return resolve(player, anchor, horizontalRadius, verticalRadius, true);
    }

    private static List<CraftingItemSource> resolve(
            ServerPlayer player, BlockPos anchor, int horizontalRadius,
            int verticalRadius, boolean genericOnly
    ) {
        Level level = player.level();
        BlockPos origin = anchor.immutable();
        int horizontal = Math.max(0, horizontalRadius);
        int vertical = Math.max(0, verticalRadius);

        BlockPos min = origin.offset(
                -horizontal,
                -vertical,
                -horizontal
        );
        BlockPos max = origin.offset(
                horizontal,
                vertical,
                horizontal
        );

        ArrayList<BlockPos> positions = new ArrayList<>();
        for (BlockPos mutable : BlockPos.betweenClosed(min, max)) {
            positions.add(mutable.immutable());
        }
        positions.sort(Comparator.comparingLong(BlockPos::asLong));

        ArrayList<CraftingItemSource> sources = new ArrayList<>();
        SourceIdentities identities = new SourceIdentities();
        Set<PhysicalSlot> physicalSlots = new HashSet<>();

        for (BlockPos pos : positions) {
            if ((!genericOnly && pos.equals(origin)) || !level.isLoaded(pos)) {
                continue;
            }

            try {
                ContainerItemSource source =
                        genericOnly ? ContainerItemSource.generic(level, pos)
                                : new ContainerItemSource(level, pos);
                if (!source.hasUsableBackend()
                        || !identities.add(source.key(), source.backendIdentity(),
                        source.linkedStorageGroup())) {
                    continue;
                }

                if (genericOnly || source.claimPhysicalSlots(physicalSlots)) sources.add(source);
            } catch (RuntimeException | LinkageError failure) {
                LOGGER.warn(
                        "Skipping unreadable or binary-incompatible "
                                + "nearby container source at {}",
                        pos,
                        failure
                );
            }
        }

        return List.copyOf(sources);
    }

    static final class SourceIdentities {
        private final Set<CraftingSourceKey> keys = new HashSet<>();
        private final Set<Object> backends = Collections.newSetFromMap(new IdentityHashMap<>());
        private final Set<UUID> linkedGroups = new HashSet<>();

        boolean add(CraftingSourceKey key, Object backend, UUID group) {
            // Linked barrels/chests can expose distinct filtered handlers for one inventory.
            if (keys.contains(key) || backends.contains(backend)
                    || (group != null && linkedGroups.contains(group))) return false;
            keys.add(key);
            backends.add(backend);
            if (group != null) linkedGroups.add(group);
            return true;
        }
    }

    /**
     * Read-only helper retained for resolver callers that need display-only
     * copies. It is deliberately independent of Gunsmith stack limits.
     */
    public static List<ItemStack> readAllDisplayStacks(
            CraftingItemSource source
    ) {
        ArrayList<ItemStack> stacks = new ArrayList<>();
        int slots = source.slotCount();
        for (int slot = 0; slot < slots; slot++) {
            ItemStack stack = source.getStackInSlot(slot);
            if (stack != null && !stack.isEmpty()) {
                stacks.add(stack.copy());
            }
        }
        return List.copyOf(stacks);
    }
}
