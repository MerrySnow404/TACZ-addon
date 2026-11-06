package com.mafuyu404.taczaddon.refit;

import static org.junit.jupiter.api.Assertions.*;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.items.ItemStackHandler;

import org.junit.jupiter.api.Test;

class RefitCandidateExtractionTest {
    @Test
    void visibleButUnextractableTemplatesAreNotCandidates() {
        var handler =
                new ItemStackHandler(1) {
                    @Override
                    public ItemStack extractItem(int slot, int amount, boolean simulate) {
                        return ItemStack.EMPTY;
                    }
                };
        handler.setStackInSlot(0, new ItemStack(Items.IRON_INGOT));
        assertTrue(RefitSourceResolver.extractableStack(handler, 0).isEmpty());
    }

    @Test
    void candidateProbeDoesNotConsumeTheRealStack() {
        var handler = new ItemStackHandler(1);
        handler.setStackInSlot(0, new ItemStack(Items.IRON_INGOT, 3));
        assertEquals(3, RefitSourceResolver.extractableStack(handler, 0).getCount());
        assertEquals(3, handler.getStackInSlot(0).getCount());
    }

    @Test
    void mismatchingSimulationCannotAdvertiseTheVisibleStack() {
        var handler =
                new ItemStackHandler(1) {
                    @Override
                    public ItemStack extractItem(int slot, int amount, boolean simulate) {
                        return new ItemStack(Items.GOLD_INGOT);
                    }
                };
        handler.setStackInSlot(0, new ItemStack(Items.IRON_INGOT));
        assertTrue(RefitSourceResolver.extractableStack(handler, 0).isEmpty());
    }
}
