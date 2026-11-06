package com.mafuyu404.taczaddon.client.refit;

import com.mafuyu404.taczaddon.client.config.ClientSyncedConfig;

import com.tacz.guns.client.gui.GunRefitScreen;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class ClientLiberateAttachmentHandler {
    private ClientLiberateAttachmentHandler() {
    }

    public static void handle(boolean enabled) {
        ClientSyncedConfig.setLiberateAttachment(enabled);

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof GunRefitScreen screen
                && screen instanceof GunRefitScreenAccess access) {
            access.taczaddon$rebuildLiberatedAttachmentButtons();
        }
    }
}
