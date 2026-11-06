package com.mafuyu404.taczaddon.storage.api;

import net.minecraft.core.NonNullList;
import net.minecraft.world.item.ItemStack;

import java.util.List;

public interface InventoryAccess {

    void setItems(NonNullList<ItemStack> items);

    NonNullList<ItemStack> getItems();

    void setCompartments(
            List<NonNullList<ItemStack>> compartments
    );

    List<NonNullList<ItemStack>> getCompartments();
}