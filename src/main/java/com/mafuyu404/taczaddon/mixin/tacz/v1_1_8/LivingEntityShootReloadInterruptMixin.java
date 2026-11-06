package com.mafuyu404.taczaddon.mixin.tacz.v1_1_8;

import com.mafuyu404.taczaddon.gun.ShootWhileReloadService;

import com.tacz.guns.api.entity.ReloadState;
import com.tacz.guns.api.entity.ShootResult;
import com.tacz.guns.entity.shooter.LivingEntityShoot;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.function.Supplier;

/**
 * Server-authoritative reload interruption for the shoot-while-reloading
 * feature.
 *
 * The server detects that a shot arrived while the authoritative reload
 * state was still active, bypasses only the reload rejection, runs every
 * other native check, and only after all checks pass terminates the
 * authoritative reload immediately before the shot commits.
 */
@Mixin(
        value = LivingEntityShoot.class,
        remap = false
)
public abstract class
        LivingEntityShootReloadInterruptMixin {

    @Unique
    private static final String
            TACZADDON$SERVER_SHOOT =
            "shoot("
                    + "Ljava/util/function/Supplier;"
                    + "Ljava/util/function/Supplier;"
                    + "JFZ"
                    + ")Lcom/tacz/guns/api/entity/"
                    + "ShootResult;";

    @Shadow
    @Final
    private LivingEntity shooter;

    @Unique
    private boolean
            taczaddon$interruptReloadForCurrentShot;

    // Mark this invocation as an interrupt candidate only when all
    // preconditions are met: the shooter is a server player, the
    // server config allows it, and the authoritative reload state
    // is currently active.

    @Inject(
            method = TACZADDON$SERVER_SHOOT,
            at = @At("HEAD"),
            require = 1
    )
    private void taczaddon$beginReloadInterruptShot(
            Supplier<Float> pitch,
            Supplier<Float> yaw,
            long timestamp,
            float chargeProgress,
            boolean hasChargeContext,
            CallbackInfoReturnable<ShootResult> cir
    ) {
        this.taczaddon$interruptReloadForCurrentShot =
                ShootWhileReloadService
                        .canInterruptForImmediateServerShot(
                                this.shooter
                );
    }

    // Bypass only the isReloading() server check for the current
    // invocation. Every other native validation still runs.

    @Redirect(
            method = TACZADDON$SERVER_SHOOT,
            at = @At(
                    value = "INVOKE",
                    target =
                            "Lcom/tacz/guns/api/entity/"
                                    + "ReloadState$StateType;"
                                    + "isReloading()Z"
            ),
            require = 1
    )
    private boolean
            taczaddon$allowImmediateServerShot(
                    ReloadState.StateType stateType
            ) {
        if (this.taczaddon$interruptReloadForCurrentShot) {
            return false;
        }

        return stateType.isReloading();
    }

    // End the authoritative reload only after every native shot
    // validation (cooldown, network, draw, bolt, sprint, charge,
    // ammo, overheat, manual-action, Forge event) has passed,
    // immediately before the ServerMessageGunShoot is sent.
    //
    // Calling IGunOperator.cancelReload() first lets the Lua
    // interrupt_reload script update state-machine caches while
    // the gun still thinks it is reloading.  Only after that hook
    // returns do we force NOT_RELOADING.

    @Inject(
            method = TACZADDON$SERVER_SHOOT,
            at = @At(
                    value = "INVOKE",
                    target =
                            "Lcom/tacz/guns/network/"
                                    + "NetworkHandler;"
                                    + "sendToTrackingEntity("
                                    + "Ljava/lang/Object;"
                                    + "Lnet/minecraft/world/entity/"
                                    + "Entity;)V",
                    shift = At.Shift.BEFORE
            ),
            require = 1
    )
    private void taczaddon$commitReloadInterrupt(
            Supplier<Float> pitch,
            Supplier<Float> yaw,
            long timestamp,
            float chargeProgress,
            boolean hasChargeContext,
            CallbackInfoReturnable<ShootResult> cir
    ) {
        if (!this.taczaddon$interruptReloadForCurrentShot) {
            return;
        }

        ShootWhileReloadService.commitReloadCancellation(
                this.shooter
        );

        this.taczaddon$interruptReloadForCurrentShot =
                false;
    }

    // Always clear the flag so no state leaks to the next invocation,
    // regardless of whether the shot succeeded or was rejected.

    @Inject(
            method = TACZADDON$SERVER_SHOOT,
            at = @At("RETURN"),
            require = 1
    )
    private void taczaddon$finishReloadInterruptShot(
            Supplier<Float> pitch,
            Supplier<Float> yaw,
            long timestamp,
            float chargeProgress,
            boolean hasChargeContext,
            CallbackInfoReturnable<ShootResult> cir
    ) {
        this.taczaddon$interruptReloadForCurrentShot =
                false;
    }
}
