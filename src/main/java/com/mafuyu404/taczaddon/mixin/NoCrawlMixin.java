package com.mafuyu404.taczaddon.mixin;

import com.tacz.guns.entity.shooter.LivingEntityCrawl;

import net.neoforged.fml.ModList;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = LivingEntityCrawl.class, remap = false)
public class NoCrawlMixin {
    @Inject(method = "tickCrawling", at = @At("HEAD"), cancellable = true)
    private void doIt(CallbackInfo ci) {
        if (ModList.get().isLoaded("moveslikemafuyu")) {
            ci.cancel();
        }
    }
}
