package com.mafuyu404.taczaddon.mixin.tacz.v1_1_8;

import com.mafuyu404.taczaddon.client.gun.BetterMelee;

import com.tacz.guns.api.entity.ShootResult;
import com.tacz.guns.client.gameplay.LocalPlayerShoot;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = LocalPlayerShoot.class, remap = false)
public abstract class LocalPlayerBetterMeleeMixin {
    @Shadow
    @Final
    private LocalPlayer player;

    @Inject(
            method = "shoot",
            at = @At("HEAD"),
            cancellable = true,
            require = 1
    )
    private void taczaddon$interceptBlacklistedMelee(
            CallbackInfoReturnable<ShootResult> cir
    ) {
        BetterMelee.interceptShoot(this.player)
                .ifPresent(cir::setReturnValue);
    }
}
