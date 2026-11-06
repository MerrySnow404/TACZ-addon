package com.mafuyu404.taczaddon.gun;

import com.tacz.guns.api.entity.IGunOperator;
import net.minecraft.world.entity.LivingEntity;

public final class SlideShootService {
    private SlideShootService() {
    }

    public static void prepareShot(LivingEntity shooter) {
        if (shooter == null || !shooter.getTags().contains("slide")) {
            return;
        }
        IGunOperator operator =
                IGunOperator.fromLivingEntity(shooter);
        if (operator != null && operator.getDataHolder() != null) {
            operator.getDataHolder().sprintTimeS = 0.0F;
        }
    }
}
