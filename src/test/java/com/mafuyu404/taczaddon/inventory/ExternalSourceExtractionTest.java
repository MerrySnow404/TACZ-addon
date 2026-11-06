package com.mafuyu404.taczaddon.inventory;

import static org.junit.jupiter.api.Assertions.*;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.items.ItemStackHandler;

import org.junit.jupiter.api.Test;

class ExternalSourceExtractionTest {
    private static ItemStackHandler source(int count) {
        var source = new ItemStackHandler(1);
        source.setStackInSlot(0, new ItemStack(Items.IRON_INGOT, count));
        return source;
    }

    @Test
    void simulationDoesNotExtractAndRollbackRestoresExactlyOnce() {
        var source = source(5);
        var extraction = new ExternalSourceExtraction(source, 0);
        assertTrue(extraction.simulateOne());
        assertEquals(5, source.getStackInSlot(0).getCount());
        assertEquals(1, extraction.extractOne().getCount());
        assertEquals(4, source.getStackInSlot(0).getCount());
        extraction.rollback();
        extraction.rollback();
        assertEquals(5, source.getStackInSlot(0).getCount());
    }

    @Test
    void rejectedSimulationConsumesNothing() {
        var source =
                new ItemStackHandler(1) {
                    @Override
                    public ItemStack extractItem(int slot, int amount, boolean simulate) {
                        return ItemStack.EMPTY;
                    }
                };
        source.setStackInSlot(0, new ItemStack(Items.IRON_INGOT, 5));
        assertFalse(new ExternalSourceExtraction(source, 0).simulateOne());
        assertEquals(5, source.getStackInSlot(0).getCount());
    }

    @Test
    void changedComponentsAreRejectedBeforeMutation() {
        var source = source(5);
        var extraction = new ExternalSourceExtraction(source, 0);
        assertTrue(extraction.simulateOne());
        ItemStackData.updateCustomData(
                source.getStackInSlot(0), tag -> tag.putBoolean("changed", true));
        assertThrows(IllegalStateException.class, extraction::extractOne);
        extraction.rollback();
        assertTrue(ItemStackData.getCustomDataCopy(source.getStackInSlot(0)).getBoolean("changed"));
        assertEquals(5, source.getStackInSlot(0).getCount());
    }

    @Test
    void mutateThenThrowRestoresTheExternalSlot() {
        var source =
                new ItemStackHandler(1) {
                    @Override
                    public ItemStack extractItem(int slot, int amount, boolean simulate) {
                        ItemStack extracted = super.extractItem(slot, amount, simulate);
                        if (!simulate) throw new IllegalStateException("failure after extraction");
                        return extracted;
                    }
                };
        source.setStackInSlot(0, new ItemStack(Items.IRON_INGOT, 5));
        var extraction = new ExternalSourceExtraction(source, 0);
        assertTrue(extraction.simulateOne());
        assertThrows(IllegalStateException.class, extraction::extractOne);
        extraction.rollback();
        assertEquals(5, source.getStackInSlot(0).getCount());
    }

    @Test
    void failedInstallRestoresGunInventoryAndExternalSource() {
        var inventory = new net.minecraft.world.entity.player.Inventory(null);
        inventory.setItem(0, new ItemStack(Items.DIAMOND_SWORD));
        var source = source(2);
        var extraction = new ExternalSourceExtraction(source, 0);
        var transaction = MainInventoryTransaction.begin(inventory);
        assertTrue(extraction.simulateOne());
        assertTrue(transaction.canFullyInsert(new ItemStack(Items.GOLD_INGOT)));
        extraction.extractOne();
        inventory.setItem(0, new ItemStack(Items.STONE));
        // Use the shared insertion helper without PlayerMainInvWrapper's world notification (the
        // test inventory has no player).
        MainInventoryTransaction.commitInsert(
                new net.neoforged.neoforge.items.wrapper.InvWrapper(inventory),
                new ItemStack(Items.GOLD_INGOT));
        extraction.rollback();
        transaction.rollback();
        assertEquals(Items.DIAMOND_SWORD, inventory.getItem(0).getItem());
        assertTrue(inventory.getItem(1).isEmpty());
        assertEquals(2, source.getStackInSlot(0).getCount());
    }

    @Test
    void fullInventoryPreflightLeavesExternalSourceAlone() {
        var inventory = new net.minecraft.world.entity.player.Inventory(null);
        for (int slot = 0; slot < inventory.items.size(); slot++)
            inventory.setItem(slot, new ItemStack(Items.STONE, 64));
        var source = source(1);
        var extraction = new ExternalSourceExtraction(source, 0);
        var transaction = MainInventoryTransaction.begin(inventory);
        assertTrue(extraction.simulateOne());
        assertFalse(transaction.canFullyInsert(new ItemStack(Items.GOLD_INGOT)));
        transaction.close();
        assertEquals(1, source.getStackInSlot(0).getCount());
    }
}
