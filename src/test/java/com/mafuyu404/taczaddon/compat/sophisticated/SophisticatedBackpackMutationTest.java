package com.mafuyu404.taczaddon.compat.sophisticated;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mafuyu404.taczaddon.inventory.ItemStackData;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.items.IItemHandlerModifiable;

import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * Deterministic mutation-detection tests around a fake modifiable handler.
 *
 * <p>The server mutation facade cannot be exercised without a live {@code ServerPlayer}/{@code
 * IBackpackWrapper}, so the change-detection and write-back decisions are tested directly. When the
 * decision helpers report no change the facade's persistence/synchronization block is skipped,
 * which means no {@code saveInventory}/{@code BackpackContentsPayload} work is requested for an
 * untouched backpack.
 */
class SophisticatedBackpackMutationTest {

    @Test
    void ordinaryAmmoExtractionChangeIsDetectedAndWrittenBack() {
        FakeModifiableHandler handler = new FakeModifiableHandler(new ItemStack(Items.ARROW, 16));

        List<ItemStack> before = SophisticatedBackpackMutations.snapshotHandler(handler);

        handler.extractItem(0, 5, false);

        boolean changed = SophisticatedBackpackMutations.writeBackChangedStacks(before, handler);

        assertTrue(changed);
        assertEquals(List.of(0), handler.writeBackSlots);
        assertEquals(11, handler.getStackInSlot(0).getCount());
    }

    @Test
    void ammoBoxInPlaceMutationIsDetectedAndWrittenBack() {
        ItemStack ammoBox = new ItemStack(Items.STONE);
        ItemStackData.updateCustomData(ammoBox, tag -> tag.putInt("AmmoCount", 32));

        FakeModifiableHandler handler = new FakeModifiableHandler(ammoBox);

        List<ItemStack> before = SophisticatedBackpackMutations.snapshotHandler(handler);

        /*
         * TaCZ mutates the existing stack returned by getStackInSlot in
         * place; no extractItem or setStackInSlot is involved.
         */
        ItemStack live = handler.getStackInSlot(0);
        ItemStackData.updateCustomData(live, tag -> tag.putInt("AmmoCount", 16));

        boolean changed = SophisticatedBackpackMutations.writeBackChangedStacks(before, handler);

        assertTrue(changed);
        assertEquals(List.of(0), handler.writeBackSlots);
        assertEquals(
                16, ItemStackData.getCustomDataCopy(handler.getStackInSlot(0)).getInt("AmmoCount"));
    }

    @Test
    void noActualMutationDoesNotRequestWriteBack() {
        FakeModifiableHandler handler = new FakeModifiableHandler(new ItemStack(Items.ARROW, 16));

        List<ItemStack> before = SophisticatedBackpackMutations.snapshotHandler(handler);

        boolean changed = SophisticatedBackpackMutations.writeBackChangedStacks(before, handler);

        assertFalse(changed);
        assertTrue(handler.writeBackSlots.isEmpty());

        /*
         * The facade only persists and synchronizes inside
         * mutateBackpackHandler when the change decision is true, so an
         * untouched backpack requests neither saveInventory nor a
         * BackpackContentsPayload.
         */
        assertFalse(SophisticatedBackpackMutations.shouldPersistAndSynchronize(changed));
    }

    @Test
    void changedMutationRequestsPersistenceAndSynchronization() {
        FakeModifiableHandler handler = new FakeModifiableHandler(new ItemStack(Items.ARROW, 16));

        List<ItemStack> before = SophisticatedBackpackMutations.snapshotHandler(handler);

        handler.extractItem(0, 1, false);

        boolean changed = SophisticatedBackpackMutations.writeBackChangedStacks(before, handler);

        assertTrue(changed);
        assertTrue(SophisticatedBackpackMutations.shouldPersistAndSynchronize(changed));
    }

    @Test
    void equalStackDifferentInstanceDoesNotTriggerWriteBack() {
        FakeModifiableHandler handler = new FakeModifiableHandler(new ItemStack(Items.ARROW, 16));

        List<ItemStack> before = SophisticatedBackpackMutations.snapshotHandler(handler);

        /*
         * Same item/components/count on a different object instance must not
         * be treated as a mutation; only ItemStack content equality matters.
         */
        handler.setStackInSlot(0, new ItemStack(Items.ARROW, 16));
        handler.writeBackSlots.clear();

        boolean changed = SophisticatedBackpackMutations.writeBackChangedStacks(before, handler);

        assertFalse(changed);
        assertTrue(handler.writeBackSlots.isEmpty());
    }

    @Test
    void snapshotIsDetachedFromLiveStacks() {
        FakeModifiableHandler handler = new FakeModifiableHandler(new ItemStack(Items.ARROW, 16));

        List<ItemStack> before = SophisticatedBackpackMutations.snapshotHandler(handler);

        ItemStack live = handler.getStackInSlot(0);
        live.setCount(3);

        assertNotSame(live, before.get(0));
        assertEquals(16, before.get(0).getCount());
        assertEquals(3, handler.getStackInSlot(0).getCount());
    }

    private static final class FakeModifiableHandler implements IItemHandlerModifiable {

        private final List<ItemStack> stacks = new ArrayList<>();
        private final List<Integer> writeBackSlots = new ArrayList<>();

        FakeModifiableHandler(ItemStack... initial) {
            for (ItemStack stack : initial) {
                stacks.add(stack.copy());
            }
        }

        @Override
        public void setStackInSlot(int slot, @NotNull ItemStack stack) {
            writeBackSlots.add(slot);
            stacks.set(slot, stack);
        }

        @Override
        public int getSlots() {
            return stacks.size();
        }

        @Override
        public @NotNull ItemStack getStackInSlot(int slot) {
            return stacks.get(slot);
        }

        @Override
        public @NotNull ItemStack insertItem(int slot, @NotNull ItemStack stack, boolean simulate) {
            return stack;
        }

        @Override
        public @NotNull ItemStack extractItem(int slot, int amount, boolean simulate) {
            ItemStack current = stacks.get(slot);

            if (current.isEmpty() || amount <= 0) {
                return ItemStack.EMPTY;
            }

            int toExtract = Math.min(amount, current.getCount());
            ItemStack result = current.copyWithCount(toExtract);

            if (!simulate) {
                if (toExtract >= current.getCount()) {
                    stacks.set(slot, ItemStack.EMPTY);
                } else {
                    stacks.set(slot, current.copyWithCount(current.getCount() - toExtract));
                }
            }

            return result;
        }

        @Override
        public int getSlotLimit(int slot) {
            return 64;
        }

        @Override
        public boolean isItemValid(int slot, @NotNull ItemStack stack) {
            return true;
        }
    }
}
