package com.mafuyu404.taczaddon.bootstrap;

import com.mafuyu404.taczaddon.network.AmmoBoxCollectPacket;
import com.mafuyu404.taczaddon.network.AttachmentDetailRuleStatePacket;
import com.mafuyu404.taczaddon.network.ClientPacketDispatch;
import com.mafuyu404.taczaddon.network.GunSmithCraftRequestPacket;
import com.mafuyu404.taczaddon.network.GunSmithCraftResultPacket;
import com.mafuyu404.taczaddon.network.GunSmithSourceRefreshRequestPacket;
import com.mafuyu404.taczaddon.network.GunSmithSourceSnapshotPacket;
import com.mafuyu404.taczaddon.network.LiberateAttachmentInstallPacket;
import com.mafuyu404.taczaddon.network.LiberateAttachmentStatePacket;
import com.mafuyu404.taczaddon.network.RefitExternalAttachmentInstallPacket;
import com.mafuyu404.taczaddon.network.RefitSourceRefreshRequestPacket;
import com.mafuyu404.taczaddon.network.RefitSourceSnapshotPacket;
import com.mafuyu404.taczaddon.network.ServerFeatureConfigSyncPacket;
import com.mafuyu404.taczaddon.network.SwitchGunPacket;
import com.mafuyu404.taczaddon.network.server.AmmoBoxCollectHandler;
import com.mafuyu404.taczaddon.network.server.GunSmithCraftRequestHandler;
import com.mafuyu404.taczaddon.network.server.GunSmithSourceRefreshRequestHandler;
import com.mafuyu404.taczaddon.network.server.LiberateAttachmentInstallHandler;
import com.mafuyu404.taczaddon.network.server.RefitExternalAttachmentInstallHandler;
import com.mafuyu404.taczaddon.network.server.RefitSourceRefreshRequestHandler;
import com.mafuyu404.taczaddon.network.server.SwitchGunHandler;

import net.minecraftforge.network.NetworkDirection;

import java.util.Optional;

import static com.mafuyu404.taczaddon.network.NetworkHandler.CHANNEL;

public final class NetworkRegistration {
    private NetworkRegistration() {}
    private static final int ID_PRIMITIVE_RESERVED = 0;
    private static final int ID_SWITCH_GUN = 1;
    private static final int ID_AMMO_BOX_COLLECT = 2;
    private static final int ID_SERVER_FEATURE_CONFIG = 3;
    private static final int ID_GUNSMITH_SOURCE_REFRESH = 4;
    private static final int ID_GUNSMITH_SOURCE_SNAPSHOT = 5;
    private static final int ID_GUNSMITH_CRAFT_REQUEST = 6;
    private static final int ID_GUNSMITH_CRAFT_RESULT = 7;
    private static final int ID_LIBERATE_ATTACHMENT_STATE = 8;
    private static final int ID_LIBERATE_ATTACHMENT_INSTALL = 9;
    private static final int ID_REFIT_SOURCE_REFRESH = 10;
    private static final int ID_REFIT_SOURCE_SNAPSHOT = 11;
    private static final int ID_REFIT_EXTERNAL_INSTALL = 12;
    private static final int ID_ATTACHMENT_DETAIL_RULE_STATE = 13;
    public static void register() {
        CHANNEL.registerMessage(
                ID_SWITCH_GUN,
                SwitchGunPacket.class,
                SwitchGunPacket::encode,
                SwitchGunPacket::decode,
                SwitchGunHandler::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER)
        );

        CHANNEL.registerMessage(
                ID_AMMO_BOX_COLLECT,
                AmmoBoxCollectPacket.class,
                AmmoBoxCollectPacket::encode,
                AmmoBoxCollectPacket::decode,
                AmmoBoxCollectHandler::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER)
        );

        CHANNEL.registerMessage(
                ID_SERVER_FEATURE_CONFIG,
                ServerFeatureConfigSyncPacket.class,
                ServerFeatureConfigSyncPacket::encode,
                ServerFeatureConfigSyncPacket::decode,
                ClientPacketDispatch::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT)
        );

        CHANNEL.registerMessage(
                ID_GUNSMITH_SOURCE_REFRESH,
                GunSmithSourceRefreshRequestPacket.class,
                GunSmithSourceRefreshRequestPacket::encode,
                GunSmithSourceRefreshRequestPacket::decode,
                GunSmithSourceRefreshRequestHandler::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER)
        );

        CHANNEL.registerMessage(
                ID_GUNSMITH_SOURCE_SNAPSHOT,
                GunSmithSourceSnapshotPacket.class,
                GunSmithSourceSnapshotPacket::encode,
                GunSmithSourceSnapshotPacket::decode,
                ClientPacketDispatch::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT)
        );

        CHANNEL.registerMessage(
                ID_GUNSMITH_CRAFT_REQUEST,
                GunSmithCraftRequestPacket.class,
                GunSmithCraftRequestPacket::encode,
                GunSmithCraftRequestPacket::decode,
                GunSmithCraftRequestHandler::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER)
        );

        CHANNEL.registerMessage(
                ID_GUNSMITH_CRAFT_RESULT,
                GunSmithCraftResultPacket.class,
                GunSmithCraftResultPacket::encode,
                GunSmithCraftResultPacket::decode,
                ClientPacketDispatch::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT)
        );

        CHANNEL.registerMessage(
                ID_LIBERATE_ATTACHMENT_STATE,
                LiberateAttachmentStatePacket.class,
                LiberateAttachmentStatePacket::encode,
                LiberateAttachmentStatePacket::decode,
                ClientPacketDispatch::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT)
        );

        CHANNEL.registerMessage(
                ID_LIBERATE_ATTACHMENT_INSTALL,
                LiberateAttachmentInstallPacket.class,
                LiberateAttachmentInstallPacket::encode,
                LiberateAttachmentInstallPacket::decode,
                LiberateAttachmentInstallHandler::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER)
        );

        CHANNEL.registerMessage(
                ID_REFIT_SOURCE_REFRESH,
                RefitSourceRefreshRequestPacket.class,
                RefitSourceRefreshRequestPacket::encode,
                RefitSourceRefreshRequestPacket::decode,
                RefitSourceRefreshRequestHandler::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER)
        );

        CHANNEL.registerMessage(
                ID_REFIT_SOURCE_SNAPSHOT,
                RefitSourceSnapshotPacket.class,
                RefitSourceSnapshotPacket::encode,
                RefitSourceSnapshotPacket::decode,
                ClientPacketDispatch::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT)
        );

        CHANNEL.registerMessage(
                ID_REFIT_EXTERNAL_INSTALL,
                RefitExternalAttachmentInstallPacket.class,
                RefitExternalAttachmentInstallPacket::encode,
                RefitExternalAttachmentInstallPacket::decode,
                RefitExternalAttachmentInstallHandler::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER)
        );

        CHANNEL.registerMessage(
                ID_ATTACHMENT_DETAIL_RULE_STATE, AttachmentDetailRuleStatePacket.class,
                AttachmentDetailRuleStatePacket::encode, AttachmentDetailRuleStatePacket::decode,
                ClientPacketDispatch::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
    }
}
