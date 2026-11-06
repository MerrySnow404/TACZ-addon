package com.mafuyu404.taczaddon.compat.sophisticated;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import net.p3pp3rf1y.sophisticatedbackpacks.backpack.BackpackStorage;
import net.p3pp3rf1y.sophisticatedbackpacks.backpack.wrapper.BackpackLinkedStorageHostWrapper;
import net.p3pp3rf1y.sophisticatedbackpacks.backpack.wrapper.BackpackWrapper;
import net.p3pp3rf1y.sophisticatedbackpacks.backpack.wrapper.IBackpackWrapper;
import net.p3pp3rf1y.sophisticatedbackpacks.network.BackpackContentsPayload;
import net.p3pp3rf1y.sophisticatedbackpacks.network.RequestBackpackInventoryContentsPayload;
import net.p3pp3rf1y.sophisticatedcore.init.ModCoreDataComponents;
import net.p3pp3rf1y.sophisticatedcore.inventory.InventoryHandler;
import net.p3pp3rf1y.sophisticatedcore.linkedstorage.LinkedStorageContentsPayload;
import net.p3pp3rf1y.sophisticatedcore.linkedstorage.LinkedStorageEndpointData;
import net.p3pp3rf1y.sophisticatedcore.linkedstorage.LinkedStorageEndpointStackState;
import net.p3pp3rf1y.sophisticatedcore.linkedstorage.LinkedStorageStackLifecycle;
import net.p3pp3rf1y.sophisticatedcore.network.ItemStackReference;
import net.p3pp3rf1y.sophisticatedcore.upgrades.UpgradeHandler;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

/** Routes authoritative snapshots and client bootstrap requests. */
final class SophisticatedContentsSync {
    static void syncBackpackContents(ServerPlayer player, IBackpackWrapper wrapper) {
        routeContentsSync(
                wrapper,
                uuid ->
                        PacketDistributor.sendToPlayer(
                                player,
                                LinkedStorageContentsPayload.createSnapshot(
                                        player.serverLevel(), uuid)),
                uuid -> {
                    CompoundTag backpackContents =
                            BackpackStorage.get().getOrCreateBackpackContents(uuid);

                    CompoundTag synchronizedContents = new CompoundTag();

                    Tag inventoryNbt = backpackContents.get(InventoryHandler.INVENTORY_TAG);

                    if (inventoryNbt != null) {
                        synchronizedContents.put(
                                InventoryHandler.INVENTORY_TAG, inventoryNbt.copy());
                    }

                    Tag upgradeNbt = backpackContents.get(UpgradeHandler.UPGRADE_INVENTORY_TAG);

                    if (upgradeNbt != null) {
                        synchronizedContents.put(
                                UpgradeHandler.UPGRADE_INVENTORY_TAG, upgradeNbt.copy());
                    }

                    PacketDistributor.sendToPlayer(
                            player, new BackpackContentsPayload(uuid, synchronizedContents));
                });
    }

    static void routeContentsSync(
            IBackpackWrapper wrapper, Consumer<UUID> linked, Consumer<UUID> ordinary) {
        wrapper.getContentsUuid()
                .ifPresent(wrapper instanceof BackpackLinkedStorageHostWrapper ? linked : ordinary);
    }

    static void syncAllBackpack(PlayerInventoryProviderBridge bridge, Player player) {
        if (!player.level().isClientSide()) {
            return;
        }

        ClientSyncRequests requests =
                new ClientSyncRequests(
                        payload -> PacketDistributor.sendToServer(payload),
                        backpack -> BackpackWrapper.fromStack(backpack));
        bridge.forEachBackpack(
                player,
                (backpack, inventoryName, identifier, index) -> {
                    // A linked endpoint must never fall through to STORAGE_UUID,
                    // even when its endpoint data is transiently unavailable.
                    if (isLinkedStorageEndpoint(backpack)) {
                        return requests.request(
                                true,
                                getLinkedStorageGroupId(backpack),
                                backpack,
                                new ItemStackReference(
                                        ItemStackReference.EQUIPMENT_HANDLER,
                                        index,
                                        inventoryName,
                                        identifier));
                    }
                    return requests.request(
                            false,
                            backpack.get(ModCoreDataComponents.STORAGE_UUID.get()),
                            backpack,
                            new ItemStackReference(
                                    ItemStackReference.EQUIPMENT_HANDLER,
                                    index,
                                    inventoryName,
                                    identifier));
                });
    }

    static boolean isLinkedStorageEndpoint(ItemStack backpack) {
        return LinkedStorageStackLifecycle.classifyEndpoint(backpack)
                == LinkedStorageEndpointStackState.ENDPOINT;
    }

    static UUID getLinkedStorageGroupId(ItemStack backpack) {
        LinkedStorageEndpointData endpoint =
                backpack.get(ModCoreDataComponents.LINKED_STORAGE_ENDPOINT.get());
        return endpoint == null ? null : endpoint.groupId();
    }

    static boolean refreshInventoryBackpackWrapper(
            PlayerInventoryProviderBridge bridge, Player player, UUID updatedUuid) {
        if (updatedUuid == null) {
            return false;
        }

        boolean[] refreshed = {false};

        bridge.forEachBackpack(
                player,
                (backpack, inventoryName, identifier, index) -> {
                    UUID backpackUuid = backpack.get(ModCoreDataComponents.STORAGE_UUID.get());

                    if (!updatedUuid.equals(backpackUuid)) {
                        return false;
                    }

                    IBackpackWrapper wrapper = BackpackWrapper.fromStack(backpack);

                    if (wrapper instanceof BackpackWrapper backpackWrapper) {
                        /*
                         * Clears the cached InventoryHandler and
                         * UpgradeHandler. Their next access reloads data from
                         * BackpackStorage.
                         */
                        backpackWrapper.onContentsNbtUpdated();
                        refreshed[0] = true;
                    }

                    return true;
                });

        return refreshed[0];
    }

    static final class ClientSyncRequests {
        private final Set<UUID> requestedStorageUuids = new HashSet<>();
        private final Set<UUID> requestedLinkedGroupIds = new HashSet<>();
        private final Consumer<CustomPacketPayload> sender;
        private final Consumer<ItemStack> registerNormalWrapper;

        ClientSyncRequests(
                Consumer<CustomPacketPayload> sender, Consumer<ItemStack> registerNormalWrapper) {
            this.sender = sender;
            this.registerNormalWrapper = registerNormalWrapper;
        }

        /** Returns false in every case so the provider keeps visiting backpacks. */
        boolean request(
                boolean linkedEndpoint,
                @javax.annotation.Nullable UUID id,
                ItemStack backpack,
                ItemStackReference reference) {
            if (linkedEndpoint) {
                if (id != null && requestedLinkedGroupIds.add(id)) {
                    // Upstream sends a snapshot unless knownRevision equals the
                    // group's revision. -1 forces bootstrap after native logout clear.
                    sender.accept(LinkedStorageRequestBridge.create(id, -1L, reference));
                    com.mojang.logging.LogUtils.getLogger()
                            .debug(
                                    "[taczaddon] Requested linked backpack contents group={}"
                                        + " knownRevision=-1",
                                    id);
                }
                return false;
            }
            if (id != null && requestedStorageUuids.add(id)) {
                // Register only the exact ordinary stack; do not initialize its
                // InventoryHandler until BackpackStorage has native contents.
                registerNormalWrapper.accept(backpack);
                sender.accept(new RequestBackpackInventoryContentsPayload(id, reference));
                com.mojang.logging.LogUtils.getLogger()
                        .debug(
                                "[taczaddon] Requested ordinary backpack contents storageUuid={}",
                                id);
            }
            return false;
        }
    }
}
