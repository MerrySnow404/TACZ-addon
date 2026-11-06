package com.mafuyu404.taczaddon.client.refit;

import com.mafuyu404.taczaddon.client.config.ClientSyncedConfig;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class ClientAttachmentDetailRuleHandler {
    private ClientAttachmentDetailRuleHandler() {
    }

    public static void handle(boolean enabled) {
        ClientSyncedConfig.setShowAttachmentDetail(enabled);
    }
}
