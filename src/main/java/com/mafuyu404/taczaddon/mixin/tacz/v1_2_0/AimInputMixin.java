package com.mafuyu404.taczaddon.mixin.tacz.v1_2_0;

import com.mafuyu404.taczaddon.client.camera.BetterAimCamera;

import com.tacz.guns.client.input.AimKey;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = AimKey.class, remap = false)
public class AimInputMixin {
    @Inject(method = "onAimInput(Z)Z", at = @At("RETURN"), require = 1)
    private static void taczaddon$afterAimInput(boolean isPress, CallbackInfoReturnable<Boolean> cir) {
        BetterAimCamera.afterAimInput();
    }
}
