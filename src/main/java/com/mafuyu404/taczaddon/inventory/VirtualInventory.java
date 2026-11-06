package com.mafuyu404.taczaddon.inventory;

import com.google.common.collect.ImmutableList;

import net.minecraft.core.NonNullList;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.Objects;

public class VirtualInventory extends Inventory {
    private final int virtualSize;
    private final int playerInventorySize;

    public VirtualInventory(int size, Player player) {
        super(Objects.requireNonNull(player));
        // Compatibility view for TaCZ methods that strictly require Inventory.
        // Handler-based ammo/crafting paths use CompositeItemSource instead.
        ((InventoryContentsAccess) this).setItems(NonNullList.withSize(size, ItemStack.EMPTY));
        ((InventoryContentsAccess) this)
                .setCompartments(ImmutableList.of(this.items, this.armor, this.offhand));
        this.playerInventorySize = player.getInventory().getContainerSize();
        this.virtualSize = size;
    }

    public VirtualInventory extend() {
        Inventory playerInventory = this.player.getInventory();
        for (int i = 0; i < this.playerInventorySize; i++) {
            this.setItem(i, playerInventory.getItem(i));
        }
        this.selected = playerInventory.selected;
        return this;
    }

    public int getVirtualSize() {
        return virtualSize;
    }

    public int getPlayerInventorySize() {
        return playerInventorySize;
    }
}
