package com.mafuyu404.taczaddon.client.gun;

import net.minecraft.client.player.LocalPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class ClientShootWhileReloadService {
    private ClientShootWhileReloadService() {
    }

    public static boolean canInterruptForImmediateShot(
            LocalPlayer player
    ) {
        return ShootWhenReload.canInterruptForImmediateShot(player);
    }

    public static boolean playLocalReloadInterruptAnimation(
            LocalPlayer player
    ) {
        return ShootWhenReload.playLocalReloadInterruptAnimation(player);
    }
}
