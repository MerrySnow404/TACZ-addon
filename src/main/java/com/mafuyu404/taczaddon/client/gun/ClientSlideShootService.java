package com.mafuyu404.taczaddon.client.gun;

import com.tacz.guns.entity.sync.ModSyncedEntityData;
import net.minecraft.client.player.LocalPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class ClientSlideShootService {
    private ClientSlideShootService() {
    }

    public static void prepareClientShot(LocalPlayer player) {
        if (player != null && player.getTags().contains("slide")) {
            ModSyncedEntityData.SPRINT_TIME_KEY.setValue(
                    player,
                    0.0F
            );
        }
    }
}
