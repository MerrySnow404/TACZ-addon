package com.mafuyu404.taczaddon.compat.sophisticated;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.items.IItemHandler;
import net.p3pp3rf1y.sophisticatedbackpacks.backpack.BackpackStorage;
import net.p3pp3rf1y.sophisticatedbackpacks.backpack.wrapper.BackpackLinkedStorageHostWrapper;
import net.p3pp3rf1y.sophisticatedbackpacks.backpack.wrapper.IBackpackWrapper;
import net.p3pp3rf1y.sophisticatedcore.inventory.InventoryHandler;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Refreshes request-scoped inventory views from synchronized contents. */
final class SophisticatedInventoryAccess {
    static IItemHandler getFreshInventoryHandler(Player player, IBackpackWrapper wrapper) {
        InventoryHandler handler = wrapper.getInventoryHandler();

        if (wrapper instanceof BackpackLinkedStorageHostWrapper
                || player == null
                || !player.level().isClientSide()) {
            return handler;
        }

        Optional<UUID> contentsUuid = wrapper.getContentsUuid();

        if (contentsUuid.isEmpty()) {
            return handler;
        }

        CompoundTag contents =
                BackpackStorage.get().getOrCreateBackpackContents(contentsUuid.get());

        if (!hasSynchronizedInventoryTag(contents)) {
            return handler;
        }

        CompoundTag synchronizedInventory = contents.getCompound(InventoryHandler.INVENTORY_TAG);

        CompoundTag cachedInventory = handler.serializeNBT(player.level().registryAccess());

        if (!needsClientInventoryRefresh(synchronizedInventory, cachedInventory)) {
            return handler;
        }

        wrapper.onContentsNbtUpdated();

        return wrapper.getInventoryHandler();
    }

    static boolean hasSynchronizedInventoryTag(CompoundTag contents) {
        return contents != null
                && contents.contains(InventoryHandler.INVENTORY_TAG, Tag.TAG_COMPOUND);
    }

    static boolean needsClientInventoryRefresh(
            CompoundTag synchronizedInventory, CompoundTag cachedInventory) {
        if (Objects.equals(synchronizedInventory, cachedInventory)) {
            return false;
        }

        if (synchronizedInventory == null || cachedInventory == null) {
            return true;
        }

        if (synchronizedInventory.getInt("Size") != cachedInventory.getInt("Size")) {
            return true;
        }

        return !sameItemsBySlot(
                synchronizedInventory.getList("Items", Tag.TAG_COMPOUND),
                cachedInventory.getList("Items", Tag.TAG_COMPOUND));
    }

    static boolean sameItemsBySlot(ListTag synchronizedItems, ListTag cachedItems) {
        if (synchronizedItems.size() != cachedItems.size()) {
            return false;
        }

        Map<Integer, CompoundTag> synchronizedBySlot = new HashMap<>();

        for (Tag rawTag : synchronizedItems) {
            if (!(rawTag instanceof CompoundTag item)) {
                return false;
            }

            synchronizedBySlot.put(item.getInt("Slot"), item);
        }

        for (Tag rawTag : cachedItems) {
            if (!(rawTag instanceof CompoundTag item)) {
                return false;
            }

            CompoundTag synchronizedItem = synchronizedBySlot.get(item.getInt("Slot"));

            if (synchronizedItem == null || !synchronizedItem.equals(item)) {
                return false;
            }
        }

        return true;
    }
}
