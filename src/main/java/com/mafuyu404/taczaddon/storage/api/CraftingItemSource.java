package com.mafuyu404.taczaddon.storage.api;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

public interface CraftingItemSource {
    CraftingSourceKey key();

    /**
     * Identity token for deduplicating aliased backends during one
     * source-resolution pass. The default is the source object itself; block
     * sources override this with the concrete handler/container identity.
     */
    default Object backendIdentity() {
        return this;
    }

    int slotCount();
    ItemStack getStackInSlot(int slot);
    ItemStack extractItem(int slot, int amount, boolean simulate);
    ItemStack insertItem(int slot, ItemStack stack, boolean simulate);
    boolean isValid(ServerPlayer player);
    default boolean hasUsableBackend() { return slotCount() > 0; }
    void markChanged();
    void synchronize(ServerPlayer player);
}
