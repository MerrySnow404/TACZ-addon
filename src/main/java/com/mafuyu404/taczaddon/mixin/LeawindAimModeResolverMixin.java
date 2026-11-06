package com.mafuyu404.taczaddon.mixin;

import com.mafuyu404.taczaddon.camera.client.AimCameraController;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Exact 3.0.3-beta ADS boundary; does not modify Leawind's key or persistent state. */
@Pseudo
@Mixin(
        targets = "io.github.leawind.thirdperson.internal.logic.scheduler.aiming.AimModeResolver",
        remap = false)
public abstract class LeawindAimModeResolverMixin {
    @Inject(
            method = "shouldAim(ZZZ)Z",
            at = @At("RETURN"),
            cancellable = true,
            require = 1,
            remap = false)
    private static void taczaddon$includeActualAds(
            boolean manual, boolean smart, boolean automatic, CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValueZ() && AimCameraController.isAimActive()) cir.setReturnValue(true);
    }
}
