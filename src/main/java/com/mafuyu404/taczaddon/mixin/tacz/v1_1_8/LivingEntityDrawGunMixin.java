package com.mafuyu404.taczaddon.mixin.tacz.v1_1_8;

import com.mafuyu404.taczaddon.compat.tacz.TaczGunClock;
import com.mafuyu404.taczaddon.gun.FastSwapService;

import com.tacz.guns.entity.shooter.LivingEntityDrawGun;
import com.tacz.guns.entity.shooter.ShooterDataHolder;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = LivingEntityDrawGun.class, remap = false)
public abstract class LivingEntityDrawGunMixin {
    @Shadow
    @Final
    private ShooterDataHolder data;

    @Inject(
            method = "draw(Ljava/util/function/Supplier;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/tacz/guns/entity/shooter/"
                            + "ShooterDataHolder;initialData()V",
                    shift = At.Shift.AFTER
            ),
            remap = false,
            require = 1
    )
    private void taczaddon$fastSwapDrawStart(CallbackInfo ci) {
        /*
         * Shared (server-visible) hook: the policy is the server's own
         * configuration, never a client preference.
         */
        if (FastSwapService.serverPolicyEnabled()) {
            this.data.drawTimestamp = TaczGunClock.nowMillis();
        }
    }
}
