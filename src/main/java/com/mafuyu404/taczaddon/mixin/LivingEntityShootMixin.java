package com.mafuyu404.taczaddon.mixin;

import com.mafuyu404.taczaddon.combat.ShootWhileReloadService;
import com.mafuyu404.taczaddon.config.CommonConfig;
import com.tacz.guns.api.entity.IGunOperator;
import com.tacz.guns.api.entity.ReloadState;
import com.tacz.guns.api.entity.ShootResult;
import com.tacz.guns.entity.shooter.LivingEntityShoot;
import com.tacz.guns.entity.shooter.ShooterDataHolder;
import com.tacz.guns.entity.sync.ModSyncedEntityData;

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

@Mixin(value = LivingEntityShoot.class, remap = false)
public class LivingEntityShootMixin {

    @Shadow @Final private ShooterDataHolder data;

    @Shadow @Final private LivingEntity shooter;

    @Unique private boolean taczaddon$interruptReloadForCurrentShot;

    // Verified TaCZ 9027337: both public overloads delegate to this private implementation.
    @Inject(
            method =
                    "shoot(Ljava/util/function/Supplier;Ljava/util/function/Supplier;JFZ)Lcom/tacz/guns/api/entity/ShootResult;",
            at = @At("HEAD"),
            require = 1)
    private void taczaddon$beforeShot(
            Supplier<Float> pitch,
            Supplier<Float> yaw,
            long time,
            float charge,
            boolean charged,
            CallbackInfoReturnable<ShootResult> cir) {
        this.taczaddon$interruptReloadForCurrentShot =
                CommonConfig.enableShootWhileReloading()
                        && this.data != null
                        && this.data.reloadStateType != null
                        && this.data.reloadStateType.isReloading()
                        && this.data.currentGunItem != null
                        && ShootWhileReloadService.hasLoadedAmmo(this.data.currentGunItem.get());
    }

    @Redirect(
            method =
                    "shoot(Ljava/util/function/Supplier;Ljava/util/function/Supplier;JFZ)Lcom/tacz/guns/api/entity/ShootResult;",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lcom/tacz/guns/api/entity/ReloadState$StateType;isReloading()Z"),
            require = 1)
    private boolean taczaddon$allowImmediateServerShot(ReloadState.StateType state) {
        return !this.taczaddon$interruptReloadForCurrentShot && state.isReloading();
    }

    // Bytecode offset 669: after every native rejection, including GunShootEvent cancellation.
    @Inject(
            method =
                    "shoot(Ljava/util/function/Supplier;Ljava/util/function/Supplier;JFZ)Lcom/tacz/guns/api/entity/ShootResult;",
            at =
                    @At(
                            value = "NEW",
                            target = "com/tacz/guns/network/message/event/ServerMessageGunShoot"),
            require = 1)
    private void taczaddon$commitReloadInterruption(
            Supplier<Float> pitch,
            Supplier<Float> yaw,
            long time,
            float charge,
            boolean charged,
            CallbackInfoReturnable<ShootResult> cir) {
        if (!this.taczaddon$interruptReloadForCurrentShot) return;
        IGunOperator operator = IGunOperator.fromLivingEntity(this.shooter);
        if (operator != null) operator.cancelReload();
        this.data.reloadStateType = ReloadState.StateType.NOT_RELOADING;
        this.data.reloadTimestamp = -1L;
        ModSyncedEntityData.RELOAD_STATE_KEY.setValue(this.shooter, new ReloadState());
        this.taczaddon$interruptReloadForCurrentShot = false;
    }

    @Inject(
            method =
                    "shoot(Ljava/util/function/Supplier;Ljava/util/function/Supplier;JFZ)Lcom/tacz/guns/api/entity/ShootResult;",
            at = @At("RETURN"),
            require = 1)
    private void taczaddon$afterShot(
            Supplier<Float> pitch,
            Supplier<Float> yaw,
            long time,
            float charge,
            boolean charged,
            CallbackInfoReturnable<ShootResult> cir) {
        this.taczaddon$interruptReloadForCurrentShot = false;
    }

    @Inject(
            method =
                    "shoot(Ljava/util/function/Supplier;Ljava/util/function/Supplier;J)Lcom/tacz/guns/api/entity/ShootResult;",
            at = @At("HEAD"),
            remap = false)
    private void slideShoot(
            Supplier<Float> pitch,
            Supplier<Float> yaw,
            long timestamp,
            CallbackInfoReturnable<ShootResult> cir) {
        if (this.shooter.getTags().contains("slide")) this.data.sprintTimeS = 0.0F;
    }
}
