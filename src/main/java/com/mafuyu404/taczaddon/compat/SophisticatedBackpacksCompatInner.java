package com.mafuyu404.taczaddon.compat;

import com.mojang.logging.LogUtils;
import com.mafuyu404.taczaddon.init.crafting.BackpackItemSource;
import com.mafuyu404.taczaddon.init.crafting.CraftingItemSource;
import com.mafuyu404.taczaddon.init.crafting.CraftingSourceKey;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.IItemHandler;
import net.p3pp3rf1y.sophisticatedbackpacks.backpack.BackpackStorage;
import net.p3pp3rf1y.sophisticatedbackpacks.backpack.wrapper.IBackpackWrapper;
import net.p3pp3rf1y.sophisticatedbackpacks.common.gui.BackpackContext;
import net.p3pp3rf1y.sophisticatedbackpacks.network.BackpackContentsMessage;
import net.p3pp3rf1y.sophisticatedbackpacks.network.SBPPacketHandler;
import net.p3pp3rf1y.sophisticatedbackpacks.util.PlayerInventoryProvider;
import net.p3pp3rf1y.sophisticatedcore.inventory.InventoryHandler;
import org.slf4j.Logger;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;

public final class SophisticatedBackpacksCompatInner {
    private static final Logger LOGGER = LogUtils.getLogger();

    private static final AtomicBoolean CLASSIFICATION_WARNING_LOGGED =
            new AtomicBoolean();

    private SophisticatedBackpacksCompatInner() {
    }

    /** Only enumerate currently carried, interactable backpacks; never look up arbitrary UUIDs. */
    public static List<CraftingItemSource> resolveRefitSources(ServerPlayer player) {
        List<CraftingItemSource> sources = new ArrayList<>();
        Set<UUID> seenContents = new HashSet<>();
        PlayerInventoryProvider.get().runOnBackpacks(player, (backpack, handlerName, identifier, slot) -> {
            try {
                BackpackContext.Item context = new BackpackContext.Item(handlerName, identifier, slot);
                if (!context.canInteractWith(player)) return false;
                IBackpackWrapper wrapper = context.getBackpackWrapper(player);
                if (wrapper == IBackpackWrapper.Noop.INSTANCE || !accessFor(wrapper).mutationAllowed()) {
                    return false;
                }
                UUID contentsId = wrapper.getContentsUuid().orElse(null);
                if (contentsId == null || !seenContents.add(contentsId)) return false;
                InventoryHandler handler = wrapper.getInventoryHandler();
                ItemStack expectedBackpack = wrapper.getBackpack();
                CraftingSourceKey.Backpack key = new CraftingSourceKey.Backpack(
                        player.getUUID(), handlerName, identifier, slot, contentsId);
                sources.add(new BackpackItemSource(key, handler, () -> {
                    if (!context.canInteractWith(player)) return false;
                    IBackpackWrapper current = context.getBackpackWrapper(player);
                    return current != IBackpackWrapper.Noop.INSTANCE
                            && current.getBackpack() == expectedBackpack
                            && current.getContentsUuid().filter(contentsId::equals).isPresent()
                            && accessFor(current).mutationAllowed();
                }, () -> {
                    handler.saveInventory();
                    player.getInventory().setChanged();
                }, () -> {
                    syncBackpackContents(player, wrapper);
                    player.containerMenu.broadcastChanges();
                }));
            } catch (RuntimeException failure) {
                LOGGER.warn("Skipping inaccessible refit backpack", failure);
            }
            return false;
        });
        return List.copyOf(sources);
    }

    public static boolean visitInventoryBackpacks(
            Player player,
            Predicate<IItemHandler> visitor
    ) {
        boolean[] stopped = {false};
        PlayerInventoryProvider.get().runOnBackpacks(
                player,
                (ignoredBackpack, handlerName, identifier, slot) -> {
                    BackpackContext.Item context =
                            new BackpackContext.Item(
                                    handlerName,
                                    identifier,
                                    slot
                            );
                    IBackpackWrapper wrapper =
                            context.getBackpackWrapper(player);
                    if (wrapper == IBackpackWrapper.Noop.INSTANCE) {
                        return false;
                    }
                    SophisticatedBackpackClassifier.Access access =
                            accessFor(wrapper);
                    if (!access.readable()) {
                        /*
                         * Fail closed for this backpack only. Counting ammo
                         * that can never be consumed would leave the HUD and
                         * the consumption path disagreeing.
                         */
                        warnBlockedWrapper(access, wrapper);
                        return false;
                    }
                    InventoryHandler handler = getFreshInventoryHandler(
                            player,
                            wrapper
                    );
                    if (visitor.test(handler)) {
                        stopped[0] = true;
                        return true;
                    }
                    return false;
                }
        );
        return stopped[0];
    }

    /**
     * Storage access policy for one wrapper: ordinary contents, known healthy
     * linked contents, or a blocked state for anything unverified.
     */
    private static SophisticatedBackpackClassifier.Access accessFor(
            IBackpackWrapper wrapper
    ) {
        if (wrapper == null) {
            return SophisticatedBackpackClassifier.Access.UNKNOWN;
        }
        return SophisticatedBackpackClassifier.resolveAccess(
                SophisticatedBackpackClassifier.classify(wrapper),
                SophisticatedLinkedStorageCompat.resolve(
                        wrapper.getBackpack()
                ),
                SophisticatedLinkedStorageCompat.generation()
        );
    }

    private static void warnBlockedWrapper(
            SophisticatedBackpackClassifier.Access access,
            IBackpackWrapper wrapper
    ) {
        if (CLASSIFICATION_WARNING_LOGGED.compareAndSet(
                false,
                true
        )) {
            LOGGER.warn(
                    "[TACZ-addon/SophisticatedBackpacks] backpack wrapper "
                            + "{} is not usable ({}); it is skipped by both "
                            + "the ammo query and the ammo mutation path",
                    wrapper.getClass().getName(),
                    access
            );
        }
    }

    private static InventoryHandler getFreshInventoryHandler(
            Player player,
            IBackpackWrapper wrapper
    ) {
        InventoryHandler handler =
                wrapper.getInventoryHandler();

        SophisticatedLinkedStorageCompat.EndpointResolution linked =
                SophisticatedLinkedStorageCompat.resolve(
                        wrapper.getBackpack()
                );

        /*
         * Normal linked path.
         */
        if (linked.linked()) {
            /*
             * Linked wrappers delegate to
             * ClientLinkedStorageBackpackContents.
             *
             * Never compare linked contents with ordinary BackpackStorage.
             */
            return handler;
        }

        /*
         * Never route a linked or unclassified wrapper through the ordinary
         * BackpackStorage sync. Classification is structural, so it also
         * covers linked generations whose concrete wrapper class name the
         * addon has never seen. Ordinary wrappers keep working even when the
         * optional linked backend itself is ABI-broken.
         */
        if (!SophisticatedBackpackClassifier.resolveAccess(
                SophisticatedBackpackClassifier.classify(wrapper),
                linked,
                SophisticatedLinkedStorageCompat.generation()
        ).ordinaryContents()) {
            return handler;
        }

        if (player == null
                || !player.level().isClientSide) {
            return handler;
        }

        Optional<UUID> contentsUuid = wrapper.getContentsUuid();
        if (contentsUuid.isEmpty()) {
            return handler;
        }

        UUID uuid = contentsUuid.get();
        CompoundTag contents =
                BackpackStorage.get()
                        .getOrCreateBackpackContents(uuid);
        if (!hasSynchronizedInventoryTag(contents)) {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug(
                        "[TACZ-addon/SophisticatedBackpacks] "
                                + "clientInventoryRefresh=false "
                                + "reason=inventory_tag_missing uuid={}",
                        uuid
                );
            }
            return handler;
        }

        CompoundTag synchronizedInventory =
                contents.getCompound(
                        InventoryHandler.INVENTORY_TAG
                );
        CompoundTag cachedInventory = handler.serializeNBT();
        if (!needsClientInventoryRefresh(
                synchronizedInventory,
                cachedInventory
        )) {
            return handler;
        }

        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug(
                    "[TACZ-addon/SophisticatedBackpacks] "
                            + "clientInventoryRefresh=true uuid={} "
                            + "cachedHash={} syncedHash={}",
                    uuid,
                    Integer.toHexString(
                            cachedInventory.hashCode()
                    ),
                    Integer.toHexString(
                            synchronizedInventory.hashCode()
                    )
            );
        }

        wrapper.onContentsNbtUpdated();
        return wrapper.getInventoryHandler();
    }

    static boolean hasSynchronizedInventoryTag(
            CompoundTag contents
    ) {
        return contents != null
                && contents.contains(
                        InventoryHandler.INVENTORY_TAG,
                        Tag.TAG_COMPOUND
                );
    }

    static boolean needsClientInventoryRefresh(
            CompoundTag synchronizedInventory,
            CompoundTag cachedInventory
    ) {
        if (Objects.equals(
                synchronizedInventory,
                cachedInventory
        )) {
            return false;
        }
        if (synchronizedInventory == null
                || cachedInventory == null) {
            return true;
        }
        if (synchronizedInventory.getInt("Size")
                != cachedInventory.getInt("Size")) {
            return true;
        }
        return !sameItemsBySlot(
                synchronizedInventory.getList(
                        "Items",
                        Tag.TAG_COMPOUND
                ),
                cachedInventory.getList(
                        "Items",
                        Tag.TAG_COMPOUND
                )
        );
    }

    private static boolean sameItemsBySlot(
            ListTag synchronizedItems,
            ListTag cachedItems
    ) {
        if (synchronizedItems.size() != cachedItems.size()) {
            return false;
        }

        Map<Integer, CompoundTag> bySlot =
                new HashMap<>();
        for (Tag tag : synchronizedItems) {
            CompoundTag item = (CompoundTag) tag;
            bySlot.put(item.getInt("Slot"), item);
        }

        for (Tag tag : cachedItems) {
            CompoundTag item = (CompoundTag) tag;
            CompoundTag synchronizedItem =
                    bySlot.get(item.getInt("Slot"));
            if (synchronizedItem == null
                    || !synchronizedItem.equals(item)) {
                return false;
            }
        }
        return true;
    }

    public static boolean mutateInventoryBackpacks(
            ServerPlayer player,
            Predicate<IItemHandler> visitor
    ) {
        boolean[] stopped = {false};
        PlayerInventoryProvider.get().runOnBackpacks(
                player,
                (ignoredBackpack, handlerName, identifier, slot) -> {
                    BackpackContext.Item context =
                            new BackpackContext.Item(
                                    handlerName,
                                    identifier,
                                    slot
                            );
                    if (!context.canInteractWith(player)) {
                        return false;
                    }
                    IBackpackWrapper wrapper =
                            context.getBackpackWrapper(player);
                    if (wrapper == IBackpackWrapper.Noop.INSTANCE) {
                        return false;
                    }
                    SophisticatedBackpackClassifier.Access access =
                            accessFor(wrapper);
                    if (!access.mutationAllowed()) {
                        /*
                         * Fail closed: an unknown, malformed or ABI-broken
                         * wrapper must not be mutated through assumptions about
                         * either storage family. A known healthy linked wrapper
                         * stays mutable: its inventory handler proxies to the
                         * canonical linked contents.
                         */
                        warnBlockedWrapper(access, wrapper);
                        return false;
                    }
                    InventoryHandler handler =
                            wrapper.getInventoryHandler();
                    boolean stop = mutateBackpackHandler(
                            player,
                            wrapper,
                            handler,
                            visitor
                    );
                    if (stop) {
                        stopped[0] = true;
                    }
                    return stop;
                }
        );
        return stopped[0];
    }

    public static void syncAllBackpack(
            Player player
    ) {
        Set<UUID> requestedStorageUuids =
                new HashSet<>();

        Set<UUID> requestedLinkedGroups =
                new HashSet<>();

        PlayerInventoryProvider.get()
                .runOnBackpacks(
                        player,
                        (
                                backpack,
                                handlerName,
                                identifier,
                                slot
                        ) -> {
                            SophisticatedLinkedStorageCompat
                                    .EndpointResolution linked =
                                    SophisticatedLinkedStorageCompat
                                            .resolve(backpack);

                            if (linked.linked()) {
                                linked.groupIdOptional()
                                        .filter(
                                                requestedLinkedGroups::add
                                        )
                                        .ifPresent(
                                                groupId -> SophisticatedLinkedStorageCompat
                                                        .requestLocatedSnapshot(groupId, handlerName, identifier, slot, false)
                                        );

                                /*
                                 * Includes malformed linked endpoints.
                                 * Never let those fall through to ordinary
                                 * BackpackStorage.
                                 */
                                return false;
                            }

                            BackpackContext.Item context =
                                    new BackpackContext.Item(
                                            handlerName,
                                            identifier,
                                            slot
                                    );

                            IBackpackWrapper wrapper =
                                    context.getBackpackWrapper(
                                            player
                                    );

                            if (wrapper
                                    == IBackpackWrapper.Noop.INSTANCE) {
                                return false;
                            }

                            /*
                             * Preserve ordinary backpack bootstrap, but never
                             * route a linked or unclassified wrapper through
                             * the ordinary UUID protocol.
                             */
                            if (!SophisticatedBackpackClassifier.resolveAccess(
                                    SophisticatedBackpackClassifier.classify(
                                            wrapper
                                    ),
                                    linked,
                                    SophisticatedLinkedStorageCompat
                                            .generation()
                            ).ordinaryContents()) {
                                return false;
                            }

                            wrapper.getContentsUuid()
                                    .filter(
                                            requestedStorageUuids::add
                                    )
                                    .ifPresent(uuid ->
                                            SBPPacketHandler.INSTANCE
                                                    .sendToServer(
                                                            SophisticatedBackpackContentsRequest.create(
                                                                    uuid, handlerName, identifier, slot
                                                            )
                                                    )
                                    );

                            return false;
                        }
                );
    }

    public static void refreshLinkedBackpackSnapshots(
            Player player
    ) {
        Set<UUID> requestedLinkedGroups =
                new HashSet<>();

        PlayerInventoryProvider.get()
                .runOnBackpacks(
                        player,
                        (
                                backpack,
                                handlerName,
                                identifier,
                                slot
                        ) -> {
                            SophisticatedLinkedStorageCompat
                                    .EndpointResolution linked =
                                    SophisticatedLinkedStorageCompat
                                            .resolve(backpack);

                            if (!linked.linked()) {
                                return false;
                            }

                            linked.groupIdOptional()
                                    .filter(
                                            requestedLinkedGroups::add
                                    )
                                    .ifPresent(
                                            groupId -> SophisticatedLinkedStorageCompat
                                                    .requestLocatedSnapshot(groupId, handlerName, identifier, slot, true)
                                    );

                            return false;
                        }
                );
    }

    private static boolean mutateBackpackHandler(
            ServerPlayer player,
            IBackpackWrapper wrapper,
            InventoryHandler inventoryHandler,
            Predicate<IItemHandler> visitor
    ) {
        List<ItemStack> before = new ArrayList<>(
                inventoryHandler.getSlots()
        );
        for (int slot = 0;
             slot < inventoryHandler.getSlots();
             slot++) {
            before.add(
                    inventoryHandler.getStackInSlot(slot).copy()
            );
        }

        boolean stop = false;
        Throwable failure = null;

        try {
            stop = visitor.test(inventoryHandler);
        } catch (LinkageError | RuntimeException visitorFailure) {
            /*
             * The visitor may already have extracted rounds from this
             * backpack. Record the failure, finish the confirmed write-back
             * below and rethrow the original failure afterwards.
             */
            failure = visitorFailure;
            stop = true;
        }

        try {
            boolean changed = pushChangedStacks(
                    inventoryHandler,
                    before
            );

            if (changed) {
                inventoryHandler.saveInventory();
                player.getInventory().setChanged();
                player.containerMenu.broadcastChanges();
                syncBackpackContents(player, wrapper);
            }
        } catch (RuntimeException finalizationFailure) {
            if (failure == null) {
                failure = finalizationFailure;
            } else {
                /*
                 * The finalization problem must never hide the original
                 * failure that already stopped the request.
                 */
                LOGGER.error(
                        "[TACZ-addon/SophisticatedBackpacks] ammo write-back "
                                + "failed while preserving the original "
                                + "failure",
                        finalizationFailure
                );
            }
        }

        if (failure instanceof LinkageError linkageError) {
            throw linkageError;
        }
        if (failure != null) {
            throw (RuntimeException) failure;
        }
        return stop;
    }

    /**
     * TaCZ mutates ammo-box ItemStacks in place through
     * {@code IAmmoBox#setAmmoCount}. Push changed stacks back through the
     * handler so Sophisticated Core refreshes its slot-NBT cache.
     *
     * @return whether any slot changed
     */
    private static boolean pushChangedStacks(
            InventoryHandler inventoryHandler,
            List<ItemStack> before
    ) {
        boolean changed = false;
        for (int slot = 0;
             slot < inventoryHandler.getSlots();
             slot++) {
            ItemStack current =
                    inventoryHandler.getStackInSlot(slot);
            if (!ItemStack.matches(before.get(slot), current)) {
                inventoryHandler.setStackInSlot(
                        slot,
                        current.copy()
                );
                changed = true;
            }
        }
        return changed;
    }

    private static void syncBackpackContents(
            ServerPlayer player,
            IBackpackWrapper wrapper
    ) {
        SophisticatedLinkedStorageCompat.EndpointResolution linked =
                SophisticatedLinkedStorageCompat.resolve(
                        wrapper.getBackpack()
                );

        if (linked.linked()) {
            linked.groupIdOptional()
                    .ifPresent(groupId ->
                            SophisticatedLinkedStorageCompat
                                    .sendSnapshot(
                                            player,
                                            groupId
                                    )
                    );

            /*
             * Includes malformed linked endpoints.
             * Never publish them through BackpackContentsMessage.
             */
            return;
        }

        /*
         * A linked bridge ABI failure must not disable ordinary backpacks.
         *
         * A linked or unclassified wrapper still fails closed instead of
         * sending its canonical host UUID through the ordinary client-storage
         * protocol.
         */
        if (!SophisticatedBackpackClassifier.resolveAccess(
                SophisticatedBackpackClassifier.classify(wrapper),
                linked,
                SophisticatedLinkedStorageCompat.generation()
        ).ordinaryContents()) {
            return;
        }

        wrapper.getContentsUuid()
                .ifPresent(uuid -> {
                    CompoundTag backpackContent =
                            BackpackStorage.get()
                                    .getOrCreateBackpackContents(
                                            uuid
                                    )
                                    .copy();

                    SBPPacketHandler.INSTANCE
                            .sendToClient(
                                    player,
                                    new BackpackContentsMessage(
                                            uuid,
                                            backpackContent
                                    )
                            );
                });
    }
}
