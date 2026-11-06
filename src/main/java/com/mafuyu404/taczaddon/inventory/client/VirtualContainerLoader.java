package com.mafuyu404.taczaddon.inventory.client;

import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;

public interface VirtualContainerLoader {

    void taczaddon$setVirtualContainer(ArrayList<ItemStack> items);

    ArrayList<ItemStack> taczaddon$getVirtualContainer();
}
