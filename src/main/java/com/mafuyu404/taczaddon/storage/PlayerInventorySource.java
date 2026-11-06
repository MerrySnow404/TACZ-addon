package com.mafuyu404.taczaddon.storage;

import com.mafuyu404.taczaddon.storage.api.CraftingItemSource;
import com.mafuyu404.taczaddon.storage.api.CraftingSourceKey;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import java.util.UUID;

/**
 * Mirrors the client gunsmith count: only Inventory#items is considered.
 * Armor and offhand are intentionally excluded.
 */
public final class PlayerInventorySource implements CraftingItemSource {
    private final UUID playerId;
    private final Inventory inventory;

    public PlayerInventorySource(ServerPlayer player) {
        this.playerId = player.getUUID();
        this.inventory = player.getInventory();
    }

    @Override
    public CraftingSourceKey key() {
        return new CraftingSourceKey.PlayerInventory(this.playerId);
    }

    @Override
    public int slotCount() {
        return this.inventory.items.size();
    }

    @Override
    public ItemStack getStackInSlot(int slot) {
        if (!isValidSlot(slot)) {
            return ItemStack.EMPTY;
        }
        return this.inventory.items.get(slot);
    }

    @Override
    public ItemStack extractItem(int slot, int amount, boolean simulate) {
        if (!isValidSlot(slot) || amount <= 0) {
            return ItemStack.EMPTY;
        }

        ItemStack current = this.inventory.items.get(slot);
        if (current.isEmpty()) {
            return ItemStack.EMPTY;
        }

        int extractedCount = Math.min(amount, current.getCount());
        ItemStack extracted = current.copyWithCount(extractedCount);

        if (!simulate) {
            current.shrink(extractedCount);
            if (current.isEmpty()) {
                this.inventory.items.set(slot, ItemStack.EMPTY);
            }
            this.inventory.setChanged();
        }

        return extracted;
    }

    @Override
    public ItemStack insertItem(
            int slot,
            ItemStack stack,
            boolean simulate
    ) {
        if (stack.isEmpty()) {
            return ItemStack.EMPTY;
        }

        if (!isValidSlot(slot)) {
            return stack.copy();
        }

        ItemStack current = this.inventory.items.get(slot);
        int slotLimit = Math.min(
                this.inventory.getMaxStackSize(),
                stack.getMaxStackSize()
        );

        if (current.isEmpty()) {
            int insertedCount = Math.min(slotLimit, stack.getCount());
            if (!simulate) {
                this.inventory.items.set(
                        slot,
                        stack.copyWithCount(insertedCount)
                );
                this.inventory.setChanged();
            }
            return remainder(stack, insertedCount);
        }

        if (!ItemStack.isSameItemSameTags(current, stack)) {
            return stack.copy();
        }

        int availableSpace =
                Math.min(slotLimit, current.getMaxStackSize())
                        - current.getCount();
        if (availableSpace <= 0) {
            return stack.copy();
        }

        int insertedCount = Math.min(availableSpace, stack.getCount());
        if (!simulate) {
            current.grow(insertedCount);
            this.inventory.setChanged();
        }
        return remainder(stack, insertedCount);
    }

    @Override
    public boolean isValid(ServerPlayer player) {
        return player.getUUID().equals(this.playerId)
                && player.getInventory() == this.inventory;
    }

    @Override
    public boolean hasUsableBackend() {
        return true;
    }

    @Override
    public void markChanged() {
        this.inventory.setChanged();
    }

    @Override
    public void synchronize(ServerPlayer player) {
        player.inventoryMenu.broadcastFullState();
    }

    private boolean isValidSlot(int slot) {
        return slot >= 0 && slot < this.inventory.items.size();
    }

    private static ItemStack remainder(ItemStack original, int inserted) {
        int remaining = original.getCount() - inserted;
        return remaining <= 0
                ? ItemStack.EMPTY
                : original.copyWithCount(remaining);
    }
}
