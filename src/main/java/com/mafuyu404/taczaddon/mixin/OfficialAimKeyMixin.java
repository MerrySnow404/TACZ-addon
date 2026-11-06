package com.mafuyu404.taczaddon.mixin;

import com.mafuyu404.taczaddon.camera.client.AimCameraController;
import com.tacz.guns.client.input.AimKey;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = AimKey.class, remap = false)
public class OfficialAimKeyMixin {
    @Inject(method = "onAimInput(Z)Z", at = @At("RETURN"), require = 1)
    private static void taczaddon$afterAimInput(
            boolean pressed, CallbackInfoReturnable<Boolean> cir) {
        AimCameraController.handleAfterAimInput();
    }
}
