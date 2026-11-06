package com.mafuyu404.taczaddon.ammo;

import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;

import java.util.List;

final class AggregateQueryHandler implements IItemHandler {
    private final List<ItemStack> stacks;

    AggregateQueryHandler(List<ItemStack> stacks) {
        this.stacks = stacks;
    }

    @Override
    public int getSlots() {
        return this.stacks.size();
    }

    @Override
    public ItemStack getStackInSlot(int slot) {
        if (slot < 0 || slot >= this.stacks.size()) {
            return ItemStack.EMPTY;
        }
        return this.stacks.get(slot);
    }

    @Override
    public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
        return stack;
    }

    @Override
    public ItemStack extractItem(int slot, int amount, boolean simulate) {
        if (slot < 0 || slot >= this.stacks.size() || amount <= 0) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = this.stacks.get(slot);
        if (stack.isEmpty()) {
            return ItemStack.EMPTY;
        }
        int extracted = Math.min(amount, stack.getCount());
        ItemStack result = stack.copyWithCount(extracted);
        if (!simulate) {
            stack.shrink(extracted);
        }
        return result;
    }

    @Override
    public int getSlotLimit(int slot) {
        return 64;
    }

    @Override
    public boolean isItemValid(int slot, ItemStack stack) {
        return false;
    }
}
