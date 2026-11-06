package com.mafuyu404.taczaddon.refit.client;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/** Keeps display indices separate from the source inventory used for installation. */
public record RefitDisplayEntry(Inventory inventory, int slot, boolean oem) {
    public ItemStack stack() {
        return inventory.getItem(slot);
    }
}
