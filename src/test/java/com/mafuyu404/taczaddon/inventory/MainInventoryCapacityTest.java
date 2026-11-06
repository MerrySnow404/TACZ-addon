package com.mafuyu404.taczaddon.inventory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.wrapper.InvWrapper;

import org.junit.jupiter.api.Test;

/**
 * Main-inventory capacity decision tests.
 *
 * <p>Production uses {@code PlayerMainInvWrapper} (a {@code RangedWrapper(InvWrapper(inventory), 0,
 * 36)} over {@code Inventory.items}). These tests exercise the exact same NeoForge insertion code
 * path via {@code InvWrapper} over a plain 36-slot container, so no fragile Minecraft runtime mocks
 * are needed. The "main inventory only" scoping (armor/offhand excluded) is structural: the wrapper
 * only exposes slots 0..35 of Inventory.items.
 */
class MainInventoryCapacityTest {

    private static final int MAIN_INVENTORY_SIZE = 36;

    private static IItemHandler mainInventoryHandler(SimpleContainer container) {
        return new InvWrapper(container);
    }

    @Test
    void emptyMainInventoryAllowsInsert() {
        SimpleContainer container = new SimpleContainer(MAIN_INVENTORY_SIZE);

        assertTrue(
                MainInventoryTransaction.canFullyInsert(
                        mainInventoryHandler(container), new ItemStack(Items.STONE)));
    }

    @Test
    void fullMainInventoryRejectsInsert() {
        SimpleContainer container = new SimpleContainer(MAIN_INVENTORY_SIZE);

        for (int slot = 0; slot < MAIN_INVENTORY_SIZE; slot++) {
            container.setItem(slot, new ItemStack(Items.STONE, 64));
        }

        assertFalse(
                MainInventoryTransaction.canFullyInsert(
                        mainInventoryHandler(container), new ItemStack(Items.STONE)));
    }

    @Test
    void partialCompatibleStackAllowsInsert() {
        SimpleContainer container = new SimpleContainer(MAIN_INVENTORY_SIZE);

        // 63 stones leave one free stack space in the 64-stackable item.
        container.setItem(0, new ItemStack(Items.STONE, 63));

        assertTrue(
                MainInventoryTransaction.canFullyInsert(
                        mainInventoryHandler(container), new ItemStack(Items.STONE)));
    }

    @Test
    void simulateDoesNotMutateInventory() {
        SimpleContainer container = new SimpleContainer(MAIN_INVENTORY_SIZE);

        container.setItem(0, new ItemStack(Items.STONE, 1));

        MainInventoryTransaction.canFullyInsert(
                mainInventoryHandler(container), new ItemStack(Items.DIAMOND));

        assertTrue(container.getItem(1).isEmpty());
        assertEquals(1, container.getItem(0).getCount());
    }

    @Test
    void commitFullyInsertsIntoEmptySlot() {
        SimpleContainer container = new SimpleContainer(MAIN_INVENTORY_SIZE);

        ItemStack remainder =
                MainInventoryTransaction.commitInsert(
                        mainInventoryHandler(container), new ItemStack(Items.DIAMOND));

        assertTrue(remainder.isEmpty());
        assertEquals(Items.DIAMOND, container.getItem(0).getItem());
    }

    @Test
    void commitMergesIntoCompatibleStack() {
        SimpleContainer container = new SimpleContainer(MAIN_INVENTORY_SIZE);

        container.setItem(0, new ItemStack(Items.STONE, 63));

        ItemStack remainder =
                MainInventoryTransaction.commitInsert(
                        mainInventoryHandler(container), new ItemStack(Items.STONE));

        assertTrue(remainder.isEmpty());
        assertEquals(64, container.getItem(0).getCount());
    }

    @Test
    void commitDoesNotMutateInputStack() {
        SimpleContainer container = new SimpleContainer(MAIN_INVENTORY_SIZE);

        ItemStack input = new ItemStack(Items.DIAMOND, 1);

        MainInventoryTransaction.commitInsert(mainInventoryHandler(container), input);

        assertEquals(1, input.getCount());
    }
}
