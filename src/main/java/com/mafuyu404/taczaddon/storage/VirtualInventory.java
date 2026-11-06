package com.mafuyu404.taczaddon.storage;

import com.mafuyu404.taczaddon.storage.api.InventoryAccess;

import com.google.common.collect.ImmutableList;
import net.minecraft.core.NonNullList;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.IItemHandler;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

public class VirtualInventory extends Inventory {
    public int size;
    public int playerInventorySize;
    private final boolean itemsOnly;
    private int sinkSlot = -1;

    public VirtualInventory(int size, Player player) {
        this(size, player, false);
    }

    public VirtualInventory(
            int size,
            Player player,
            boolean itemsOnly
    ) {
        super(Objects.requireNonNull(player));
        this.itemsOnly = itemsOnly;
        ((InventoryAccess) this).setItems(NonNullList.withSize(size, ItemStack.EMPTY));
        ((InventoryAccess) this).setCompartments(
                itemsOnly
                        ? ImmutableList.of(this.items)
                        : ImmutableList.of(
                                this.items,
                                this.armor,
                                this.offhand
                        )
        );
        this.playerInventorySize = player.getInventory().getContainerSize();
        this.size = size;
    }

    @Override
    public int getContainerSize() {
        if (this.itemsOnly) {
            return this.size;
        }
        return super.getContainerSize();
    }

    public VirtualInventory extend() {
        Inventory playerInventory = this.player.getInventory();
        for (int i = 0; i < this.playerInventorySize; i++) {
            this.setItem(i, playerInventory.getItem(i));
        }
        this.selected = playerInventory.selected;
        return this;
    }

    public VirtualInventory withSinkSlot(int slot) {
        if (slot < 0 || slot >= this.size) {
            throw new IllegalArgumentException(
                    "Virtual inventory sink is out of bounds: " + slot
            );
        }
        this.sinkSlot = slot;
        return this;
    }

    public int getSinkSlot() {
        return this.sinkSlot;
    }

    @Override
    public int getFreeSlot() {
        if (this.sinkSlot < 0) {
            return super.getFreeSlot();
        }
        return this.getItem(this.sinkSlot).isEmpty()
                ? this.sinkSlot
                : -1;
    }

    @Override
    public boolean add(ItemStack stack) {
        if (this.sinkSlot < 0) {
            return super.add(stack);
        }
        if (stack.isEmpty()
                || !this.getItem(this.sinkSlot).isEmpty()) {
            return false;
        }
        this.setItem(this.sinkSlot, stack.copy());
        return true;
    }

    public ItemHandler getHandler() {
        return new ItemHandler(this);
    }

    public static class ItemHandler implements IItemHandler {

        private final VirtualInventory virtualInventory;

        public ItemHandler(VirtualInventory virtualInventory) {
            this.virtualInventory = virtualInventory;
        }

        @Override
        public int getSlots() {
            return virtualInventory.size;
        }

        @Override
        public @NotNull ItemStack getStackInSlot(int slot) {
            if (slot < 0 || slot >= virtualInventory.size) {
                return ItemStack.EMPTY;
            }
            return virtualInventory.getItem(slot);
        }

        @Override
        public @NotNull ItemStack insertItem(int slot, @NotNull ItemStack stack, boolean simulate) {
            return stack;
        }

        @Override
        public @NotNull ItemStack extractItem(int slot, int amount, boolean simulate) {
            if (slot < 0 || slot >= virtualInventory.size || amount <= 0) {
                return ItemStack.EMPTY;
            }

            ItemStack itemStack = virtualInventory.getItem(slot);
            if (itemStack.isEmpty()) {
                return ItemStack.EMPTY;
            }

            int extracted = Math.min(amount, itemStack.getCount());
            ItemStack result = itemStack.copyWithCount(extracted);
            if (!simulate) {
                itemStack.shrink(extracted);
            }
            return result;
        }

        @Override
        public int getSlotLimit(int slot) {
            return virtualInventory.getMaxStackSize();
        }

        @Override
        public boolean isItemValid(int slot, @NotNull ItemStack stack) {
            return false;
        }
    }
}
