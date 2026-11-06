package com.mafuyu404.taczaddon.compat.sophisticated;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;

import org.junit.jupiter.api.Test;

/**
 * Client freshness decision tests for the TACZ-owned NBT comparison helpers.
 *
 * <p>The helper deliberately ignores harmless item-list ordering changes while still detecting
 * stack count changes, ammo-box component/NBT changes, slot content changes and inventory size
 * changes.
 */
class SophisticatedInventoryNbtRefreshTest {

    @Test
    void equalInventoryDoesNotRequireRefresh() {
        CompoundTag synchronizedInventory =
                inventoryNbt(
                        27, itemEntry(0, "tacz:ammo", 32), itemEntry(1, "minecraft:arrow", 12));
        CompoundTag cachedInventory =
                inventoryNbt(
                        27, itemEntry(0, "tacz:ammo", 32), itemEntry(1, "minecraft:arrow", 12));

        assertFalse(
                SophisticatedInventoryAccess.needsClientInventoryRefresh(
                        synchronizedInventory, cachedInventory));
    }

    @Test
    void ordinaryAmmoCountChangeRequiresRefresh() {
        CompoundTag synchronizedInventory = inventoryNbt(27, itemEntry(0, "tacz:ammo", 32));
        CompoundTag cachedInventory = inventoryNbt(27, itemEntry(0, "tacz:ammo", 16));

        assertTrue(
                SophisticatedInventoryAccess.needsClientInventoryRefresh(
                        synchronizedInventory, cachedInventory));
    }

    @Test
    void ammoBoxComponentOrNbtChangeRequiresRefresh() {
        CompoundTag synchronizedInventory = inventoryNbt(27, ammoBoxEntry(0, 32));
        CompoundTag cachedInventory = inventoryNbt(27, ammoBoxEntry(0, 16));

        assertTrue(
                SophisticatedInventoryAccess.needsClientInventoryRefresh(
                        synchronizedInventory, cachedInventory));
    }

    @Test
    void changedSlotContentsRequireRefresh() {
        CompoundTag synchronizedInventory = inventoryNbt(27, itemEntry(3, "tacz:ammo", 32));
        CompoundTag cachedInventory = inventoryNbt(27, itemEntry(3, "minecraft:arrow", 32));

        assertTrue(
                SophisticatedInventoryAccess.needsClientInventoryRefresh(
                        synchronizedInventory, cachedInventory));
    }

    @Test
    void sameItemsInDifferentNbtListOrderDoNotRequireRefresh() {
        CompoundTag synchronizedInventory =
                inventoryNbt(
                        27,
                        itemEntry(0, "tacz:ammo", 32),
                        itemEntry(4, "minecraft:arrow", 12),
                        itemEntry(9, "minecraft:stone", 1));
        CompoundTag cachedInventory =
                inventoryNbt(
                        27,
                        itemEntry(9, "minecraft:stone", 1),
                        itemEntry(0, "tacz:ammo", 32),
                        itemEntry(4, "minecraft:arrow", 12));

        assertFalse(
                SophisticatedInventoryAccess.needsClientInventoryRefresh(
                        synchronizedInventory, cachedInventory));
    }

    @Test
    void inventorySizeChangeRequiresRefresh() {
        CompoundTag synchronizedInventory = inventoryNbt(81, itemEntry(0, "tacz:ammo", 32));
        CompoundTag cachedInventory = inventoryNbt(27, itemEntry(0, "tacz:ammo", 32));

        assertTrue(
                SophisticatedInventoryAccess.needsClientInventoryRefresh(
                        synchronizedInventory, cachedInventory));
    }

    @Test
    void missingSynchronizedInventoryTagDoesNotPretendFreshDataExists() {
        assertFalse(SophisticatedInventoryAccess.hasSynchronizedInventoryTag(null));

        CompoundTag empty = new CompoundTag();
        assertFalse(SophisticatedInventoryAccess.hasSynchronizedInventoryTag(empty));

        CompoundTag onlyUpgrades = new CompoundTag();
        onlyUpgrades.put("upgradeInventory", new CompoundTag());
        assertFalse(SophisticatedInventoryAccess.hasSynchronizedInventoryTag(onlyUpgrades));

        CompoundTag wrongType = new CompoundTag();
        wrongType.putString("inventory", "not-a-compound");
        assertFalse(SophisticatedInventoryAccess.hasSynchronizedInventoryTag(wrongType));

        CompoundTag valid = new CompoundTag();
        valid.put("inventory", inventoryNbt(27));
        assertTrue(SophisticatedInventoryAccess.hasSynchronizedInventoryTag(valid));
    }

    private static CompoundTag inventoryNbt(int size, CompoundTag... itemEntries) {
        CompoundTag inventory = new CompoundTag();
        inventory.putInt("Size", size);

        ListTag items = new ListTag();
        for (CompoundTag itemEntry : itemEntries) {
            items.add(itemEntry);
        }
        inventory.put("Items", items);

        return inventory;
    }

    private static CompoundTag itemEntry(int slot, String id, int count) {
        CompoundTag entry = new CompoundTag();
        entry.putInt("Slot", slot);
        entry.putString("id", id);
        entry.putByte("Count", (byte) count);
        return entry;
    }

    private static CompoundTag ammoBoxEntry(int slot, int ammoCount) {
        CompoundTag entry = itemEntry(slot, "tacz:ammo_box", 1);

        CompoundTag components = new CompoundTag();
        CompoundTag ammoCountTag = new CompoundTag();
        ammoCountTag.putInt("AmmoCount", ammoCount);
        components.put("tacz:ammo_count", ammoCountTag);

        entry.put("components", components);
        return entry;
    }
}
