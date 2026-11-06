package com.mafuyu404.taczaddon.mixin;

import com.mafuyu404.taczaddon.combat.client.ClientReloadInterruption;
import com.mafuyu404.taczaddon.combat.client.MeleeInputHandler;
import com.tacz.guns.api.entity.ReloadState;
import com.tacz.guns.api.entity.ShootResult;
import com.tacz.guns.client.gameplay.LocalPlayerDataHolder;
import com.tacz.guns.client.gameplay.LocalPlayerShoot;
import com.tacz.guns.entity.sync.ModSyncedEntityData;

import net.minecraft.client.player.LocalPlayer;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = LocalPlayerShoot.class, remap = false)
public class LocalPlayerShootMixin {
    @Shadow @Final private LocalPlayer player;
    @Unique private boolean taczaddon$interruptReloadForCurrentShot;

    @Inject(method = "shoot", at = @At("RETURN"), require = 1)
    private void taczaddon$afterShoot(CallbackInfoReturnable<ShootResult> cir) {
        this.taczaddon$interruptReloadForCurrentShot = false;
    }

    @Redirect(
            method = "shoot",
            at =
                    @At(
                            value = "FIELD",
                            target =
                                    "Lcom/tacz/guns/client/gameplay/"
                                            + "LocalPlayerDataHolder;"
                                            + "clientStateLock:Z",
                            ordinal = 0),
            require = 1)
    private boolean taczaddon$ignoreReloadStateLock(LocalPlayerDataHolder data) {
        if (this.taczaddon$interruptReloadForCurrentShot) {
            return false;
        }
        return data.clientStateLock;
    }

    @Redirect(
            method = "preCheck",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lcom/tacz/guns/api/entity/"
                                            + "ReloadState$StateType;"
                                            + "isReloading()Z"),
            require = 1)
    private boolean taczaddon$allowImmediateShotDuringReload(ReloadState.StateType stateType) {
        if (this.taczaddon$interruptReloadForCurrentShot) {
            return false;
        }
        return stateType.isReloading();
    }

    @Inject(
            method = "shoot",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lcom/tacz/guns/client/gameplay/"
                                            + "LocalPlayerDataHolder;"
                                            + "lockState(Ljava/util/function/"
                                            + "Predicate;)V",
                            shift = At.Shift.BEFORE),
            require = 1)
    private void taczaddon$playLocalCancelAnimationBeforeShotCommit(
            CallbackInfoReturnable<ShootResult> cir) {
        if (!this.taczaddon$interruptReloadForCurrentShot) {
            return;
        }
        ClientReloadInterruption.playLocalReloadInterruptAnimation(this.player);
        this.taczaddon$interruptReloadForCurrentShot = false;
    }

    @Inject(method = "shoot", at = @At("HEAD"), cancellable = true)
    private void onShoot(CallbackInfoReturnable<ShootResult> cir) {
        this.taczaddon$interruptReloadForCurrentShot = false;
        MeleeInputHandler.onShoot(cir);
        if (!cir.isCancelled()) {
            this.taczaddon$interruptReloadForCurrentShot =
                    ClientReloadInterruption.canInterruptForImmediateShot(this.player);
        }
    }

    @Inject(method = "shoot", at = @At("HEAD"))
    private void slideShoot(CallbackInfoReturnable<ShootResult> cir) {
        if (this.player.getTags().contains("slide"))
            ModSyncedEntityData.SPRINT_TIME_KEY.setValue(player, 0.0F);
    }
}
