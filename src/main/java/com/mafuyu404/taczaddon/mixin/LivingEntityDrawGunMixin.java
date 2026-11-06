package com.mafuyu404.taczaddon.mixin;

import com.mafuyu404.taczaddon.config.CommonConfig;
import com.tacz.guns.entity.shooter.LivingEntityDrawGun;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(value = LivingEntityDrawGun.class, remap = false)
public class LivingEntityDrawGunMixin {
    @ModifyVariable(method = "draw", at = @At("STORE"), name = "drawTime")
    private long modifyDrawTime(long drawTime) {
        return CommonConfig.enableFastSwapGun() ? 0L : drawTime;
    }
}
