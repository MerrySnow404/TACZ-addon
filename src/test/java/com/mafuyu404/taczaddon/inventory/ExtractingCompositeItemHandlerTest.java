package com.mafuyu404.taczaddon.inventory;

import static org.junit.jupiter.api.Assertions.*;

import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.items.ItemStackHandler;

import org.junit.jupiter.api.Test;

class ExtractingCompositeItemHandlerTest {
    private static ExtractingCompositeItemHandler composite(ItemStackHandler source) {
        var builder = ReadOnlyCompositeItemHandler.builder();
        builder.addHandler(source, "backpack");
        return builder.buildExtracting();
    }

    private static ItemStackHandler capped(int count) {
        var source =
                new ItemStackHandler(1) {
                    @Override
                    public ItemStack extractItem(int slot, int amount, boolean simulate) {
                        return super.extractItem(slot, Math.min(64, amount), simulate);
                    }
                };
        source.setStackInSlot(0, new ItemStack(Items.ARROW, count));
        return source;
    }

    @Test
    void drainsOversizedStackAcrossExtractionLimitsAndDoesNotRestoreItOnCommit() {
        var source = capped(1000);
        var composite = composite(source);
        assertEquals(300, composite.extractItem(0, 300, false).getCount());
        composite.commitChanges();
        assertEquals(700, source.getStackInSlot(0).getCount());
    }

    @Test
    void partialSupplyReturnsOnlyWhatWasExtracted() {
        var source = capped(100);
        assertEquals(100, composite(source).extractItem(0, 300, false).getCount());
        assertTrue(source.getStackInSlot(0).isEmpty());
    }

    @Test
    void simulationNeverCountsTheSameRoundsTwiceOrMutatesTheSource() {
        var source = capped(1000);
        var composite = composite(source);
        assertEquals(64, composite.extractItem(0, 300, true).getCount());
        composite.commitChanges();
        assertEquals(1000, source.getStackInSlot(0).getCount());
    }

    @Test
    void stopsWhenHandlerRefusesFurtherExtraction() {
        var source =
                new ItemStackHandler(1) {
                    private int calls;

                    @Override
                    public ItemStack extractItem(int slot, int amount, boolean simulate) {
                        return calls++ == 0
                                ? super.extractItem(slot, 32, simulate)
                                : ItemStack.EMPTY;
                    }
                };
        source.setStackInSlot(0, new ItemStack(Items.ARROW, 100));
        assertEquals(32, composite(source).extractItem(0, 100, false).getCount());
        assertEquals(68, source.getStackInSlot(0).getCount());
    }

    @Test
    void replacementWithDifferentComponentsIsNotConsumed() {
        var source =
                new ItemStackHandler(1) {
                    @Override
                    public ItemStack extractItem(int slot, int amount, boolean simulate) {
                        ItemStack extracted =
                                super.extractItem(slot, Math.min(64, amount), simulate);
                        if (!simulate) {
                            var replacement = new ItemStack(Items.ARROW, 100);
                            replacement.set(
                                    DataComponents.CUSTOM_NAME,
                                    Component.literal("different ammo"));
                            setStackInSlot(slot, replacement);
                        }
                        return extracted;
                    }
                };
        source.setStackInSlot(0, new ItemStack(Items.ARROW, 100));
        var composite = composite(source);
        assertEquals(64, composite.extractItem(0, 200, false).getCount());
        composite.commitChanges();
        assertEquals(100, source.getStackInSlot(0).getCount());
        assertEquals("different ammo", source.getStackInSlot(0).getHoverName().getString());
    }

    @Test
    void ammoBoxComponentChangesStillCommitThroughWorkingCopy() {
        var source = capped(1);
        var composite = composite(source);
        composite
                .getStackInSlot(0)
                .set(DataComponents.CUSTOM_NAME, Component.literal("updated box"));
        assertNull(source.getStackInSlot(0).get(DataComponents.CUSTOM_NAME));
        composite.commitChanges();
        assertEquals("updated box", source.getStackInSlot(0).getHoverName().getString());
    }
}
