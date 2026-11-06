package com.mafuyu404.taczaddon.gun;

import com.mafuyu404.taczaddon.config.CommonConfig;

import com.tacz.guns.api.entity.IGunOperator;
import com.tacz.guns.api.entity.ReloadState;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.entity.shooter.ShooterDataHolder;
import com.tacz.guns.entity.sync.ModSyncedEntityData;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

public final class ShootWhileReloadService {
    private ShootWhileReloadService() {
    }

    public static boolean canInterruptForImmediateServerShot(
            LivingEntity shooter
    ) {
        if (!CommonConfig.enableShootWhileReloading()
                || shooter == null) {
            return false;
        }
        IGunOperator operator =
                IGunOperator.fromLivingEntity(shooter);
        if (operator == null) {
            return false;
        }
        ShooterDataHolder data = operator.getDataHolder();
        if (data == null
                || data.currentGunItem == null
                || !data.reloadStateType.isReloading()) {
            return false;
        }

        ItemStack gunStack = data.currentGunItem.get();
        if (gunStack == null || gunStack.isEmpty()) {
            return false;
        }
        IGun gun = IGun.getIGunOrNull(gunStack);
        return gun != null && !gun.useInventoryAmmo(gunStack);
    }

    public static void commitReloadCancellation(
            LivingEntity shooter
    ) {
        if (shooter == null) {
            return;
        }

        IGunOperator operator =
                IGunOperator.fromLivingEntity(shooter);
        if (operator == null) {
            return;
        }

        ShooterDataHolder data = operator.getDataHolder();
        operator.cancelReload();
        if (data != null) {
            data.reloadStateType =
                    ReloadState.StateType.NOT_RELOADING;
            data.reloadTimestamp = -1L;
        }
        ModSyncedEntityData.RELOAD_STATE_KEY.setValue(
                shooter,
                new ReloadState()
        );
    }
}
