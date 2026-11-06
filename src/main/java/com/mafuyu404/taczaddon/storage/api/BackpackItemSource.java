package com.mafuyu404.taczaddon.storage.api;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.IItemHandler;

import java.util.function.BooleanSupplier;

/** Request-scoped backpack access; optional mod types stay in the compatibility bridge. */
public final class BackpackItemSource implements CraftingItemSource {
    private final CraftingSourceKey.Backpack key;
    private final IItemHandler handler;
    private final BooleanSupplier valid;
    private final Runnable save;
    private final Runnable sync;

    public BackpackItemSource(CraftingSourceKey.Backpack key, IItemHandler handler,
                              BooleanSupplier valid, Runnable save, Runnable sync) {
        this.key = key;
        this.handler = handler;
        this.valid = valid;
        this.save = save;
        this.sync = sync;
    }

    @Override public CraftingSourceKey.Backpack key() { return key; }
    @Override public Object backendIdentity() { return handler; }
    @Override public int slotCount() { return valid.getAsBoolean() ? handler.getSlots() : 0; }
    private boolean validSlot(int slot) { return slot >= 0 && slot < slotCount(); }
    @Override public ItemStack getStackInSlot(int slot) {
        return validSlot(slot) ? handler.getStackInSlot(slot) : ItemStack.EMPTY;
    }
    @Override public ItemStack extractItem(int slot, int amount, boolean simulate) {
        return amount > 0 && validSlot(slot)
                ? handler.extractItem(slot, amount, simulate) : ItemStack.EMPTY;
    }
    @Override public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
        return validSlot(slot) ? handler.insertItem(slot, stack, simulate) : stack.copy();
    }
    @Override public boolean isValid(ServerPlayer player) {
        return key.playerId().equals(player.getUUID()) && valid.getAsBoolean();
    }
    // Save the captured backend even when access has since disappeared: an extraction
    // may already have happened. Never re-resolve a different backpack for write-back.
    @Override public void markChanged() { save.run(); }
    @Override public void synchronize(ServerPlayer player) { sync.run(); }
}
