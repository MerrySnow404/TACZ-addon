package com.mafuyu404.taczaddon.mixin;

import com.mafuyu404.taczaddon.inventory.client.VirtualContainerLoader;
import com.tacz.guns.client.gui.GunSmithTableScreen;

import net.minecraft.world.item.ItemStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;

@Mixin(value = GunSmithTableScreen.class, remap = false)
public abstract class VirtualContainer implements VirtualContainerLoader {
    @Unique private ArrayList<ItemStack> taczaddon$virtualContainer;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void taczaddon$initVirtualContainer(CallbackInfo ci) {
        this.taczaddon$virtualContainer = new ArrayList<>();
    }

    @Override
    public void taczaddon$setVirtualContainer(ArrayList<ItemStack> items) {
        this.taczaddon$virtualContainer = items == null ? new ArrayList<>() : items;
    }

    @Override
    public ArrayList<ItemStack> taczaddon$getVirtualContainer() {
        return this.taczaddon$virtualContainer;
    }
}
