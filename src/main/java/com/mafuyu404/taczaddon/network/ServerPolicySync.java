package com.mafuyu404.taczaddon.network;

import com.mafuyu404.taczaddon.config.CommonConfig;
import com.mafuyu404.taczaddon.rule.RuleRegistry;

import net.minecraft.server.level.ServerPlayer;

import static com.mafuyu404.taczaddon.network.NetworkHandler.sendToClient;

public final class ServerPolicySync {
    public static void broadcastAttachmentDetail(net.minecraft.server.MinecraftServer server, boolean enabled) {
        server.getPlayerList().getPlayers().forEach(player -> sendAttachmentDetailRuleState(player, enabled));
    }
    public static void broadcastLiberateAttachment(net.minecraft.server.MinecraftServer server, boolean enabled) {
        server.getPlayerList().getPlayers().forEach(player -> sendLiberateAttachmentState(player, enabled));
    }
    private ServerPolicySync() {}
    public static void sendServerConfig(ServerPlayer player) {
        sendToClient(
                player,
                new ServerFeatureConfigSyncPacket(CommonConfig.getBatchCraftMax(),
                        CommonConfig.enableShootWhileReloading(), CommonConfig.enableFastSwapGun())
        );
    }

    public static void sendAttachmentDetailRuleState(ServerPlayer player) {
        sendAttachmentDetailRuleState(player, player.level().getGameRules()
                .getBoolean(RuleRegistry.SHOW_ATTACHMENT_DETAIL));
    }

    public static void sendAttachmentDetailRuleState(ServerPlayer player, boolean enabled) {
        sendToClient(player, new AttachmentDetailRuleStatePacket(enabled));
    }

    public static void sendLiberateAttachmentState(
            ServerPlayer player
    ) {
        sendLiberateAttachmentState(
                player,
                player.level()
                        .getGameRules()
                        .getBoolean(RuleRegistry.LIBERATE_ATTACHMENT)
        );
    }

    public static void sendLiberateAttachmentState(
            ServerPlayer player,
            boolean enabled
    ) {
        sendToClient(
                player,
                new LiberateAttachmentStatePacket(enabled)
        );
    }

}
