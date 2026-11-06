package com.mafuyu404.taczaddon.client.gun;

import com.mafuyu404.taczaddon.config.Config;

import com.tacz.guns.api.client.gameplay.IClientPlayerGunOperator;
import com.tacz.guns.api.entity.ShootResult;
import net.minecraft.client.player.LocalPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.Optional;

@OnlyIn(Dist.CLIENT)
public class BetterMelee {
    public static Optional<ShootResult> interceptShoot(
            LocalPlayer player
    ) {
        if (player == null) {
            return Optional.empty();
        }

        if (Config.isItemInBlacklist(player.getMainHandItem())) {
            IClientPlayerGunOperator operator = IClientPlayerGunOperator.fromLocalPlayer(player);
            if (operator == null) {
                return Optional.empty();
            }

            if (!operator.isAim()) {
                operator.melee();
                return Optional.of(ShootResult.ID_NOT_EXIST);
            }
        }
        return Optional.empty();
    }
}
