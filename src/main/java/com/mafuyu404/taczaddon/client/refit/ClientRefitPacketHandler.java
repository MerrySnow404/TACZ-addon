package com.mafuyu404.taczaddon.client.refit;

import com.mafuyu404.taczaddon.network.RefitSourceSnapshotPacket;

import com.tacz.guns.client.gui.GunRefitScreen;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class ClientRefitPacketHandler {
    private ClientRefitPacketHandler() {
    }

    public static void handleSourceSnapshot(
            RefitSourceSnapshotPacket message
    ) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!(minecraft.screen instanceof GunRefitScreen)
                || !(minecraft.screen
                instanceof GunRefitScreenAccess access)) {
            return;
        }

        access.taczaddon$acceptRefitSourceSnapshot(
                message.requestId(),
                message.candidates()
        );
    }
}
