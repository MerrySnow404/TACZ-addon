package com.mafuyu404.taczaddon.mixin;

import com.mafuyu404.taczaddon.gunsmith.client.GunSmithClientState;
import com.tacz.guns.client.input.InteractKey;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.BlockHitResult;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = InteractKey.class)
public class InteractGunSmithTable {
    @Inject(
            method = "interactBlock",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;startUseItem()V"))
    private static void onInteractBlock(
            BlockHitResult blockHitResult, LocalPlayer player, Minecraft mc, CallbackInfo ci) {
        GunSmithClientState.setLastGunSmithInteractPos(blockHitResult.getBlockPos());
    }
}
