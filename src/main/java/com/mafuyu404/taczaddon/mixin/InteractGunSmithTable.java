package com.mafuyu404.taczaddon.mixin;

import com.mafuyu404.taczaddon.gunsmith.client.GunSmithClientState;
import com.tacz.guns.client.input.InteractKey;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.BlockHitResult;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Group;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = InteractKey.class)
public class InteractGunSmithTable {
    // TaCZ 1.2.0-hotfix adds the shared-use-binding flag; retain the legacy port entry point.
    @Group(name = "taczaddon$interactBlock", min = 1, max = 1)
    @Inject(
            method = "interactBlock(Lnet/minecraft/world/phys/BlockHitResult;Lnet/minecraft/client/player/LocalPlayer;Lnet/minecraft/client/Minecraft;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;startUseItem()V"),
            require = 0)
    private static void onInteractBlock(
            BlockHitResult blockHitResult, LocalPlayer player, Minecraft mc, CallbackInfo ci) {
        GunSmithClientState.setLastGunSmithInteractPos(blockHitResult.getBlockPos());
    }

    @Group(name = "taczaddon$interactBlock", min = 1, max = 1)
    @Inject(
            method = "interactBlock(Lnet/minecraft/world/phys/BlockHitResult;Lnet/minecraft/client/player/LocalPlayer;Lnet/minecraft/client/Minecraft;Z)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;startUseItem()V"),
            require = 0)
    private static void onInteractBlockWithUseBinding(
            BlockHitResult blockHitResult,
            LocalPlayer player,
            Minecraft mc,
            boolean vanillaHandlesUse,
            CallbackInfo ci) {
        GunSmithClientState.setLastGunSmithInteractPos(blockHitResult.getBlockPos());
    }
}
