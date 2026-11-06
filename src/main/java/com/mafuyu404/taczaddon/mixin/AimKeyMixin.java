package com.mafuyu404.taczaddon.mixin;

import com.mafuyu404.taczaddon.camera.client.AimCameraController;
import com.tacz.guns.client.input.AimKey;

import net.minecraft.client.KeyMapping;
import net.neoforged.neoforge.client.event.InputEvent;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = AimKey.class, remap = false)
public final class AimKeyMixin {
    @Shadow @Final public static KeyMapping AIM_KEY;

    @Inject(method = "onAimPress", at = @At("RETURN"), remap = false)
    private static void taczaddon$afterAimPress(
            InputEvent.MouseButton.Post event, CallbackInfo ci) {
        AimCameraController.handleAfterAimPress(event, AIM_KEY);
    }
}
