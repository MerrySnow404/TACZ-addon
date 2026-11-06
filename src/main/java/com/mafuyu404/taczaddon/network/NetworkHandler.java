package com.mafuyu404.taczaddon.network;

import com.mafuyu404.taczaddon.TACZaddon;
import com.mafuyu404.taczaddon.ammo.network.AmmoBoxCollectPacket;
import com.mafuyu404.taczaddon.combat.network.SwitchGunPacket;
import com.mafuyu404.taczaddon.config.network.ConfigSyncPacket;
import com.mafuyu404.taczaddon.config.network.RuleSyncPacket;
import com.mafuyu404.taczaddon.gunsmith.network.GunSmithCraftRequestPacket;
import com.mafuyu404.taczaddon.gunsmith.network.GunSmithCraftResultPacket;
import com.mafuyu404.taczaddon.inventory.network.ContainerPositionPacket;
import com.mafuyu404.taczaddon.inventory.network.ContainerReaderPacket;
import com.mafuyu404.taczaddon.refit.network.RefitExternalAttachmentInstallPacket;
import com.mafuyu404.taczaddon.refit.network.RefitSourceRefreshRequestPacket;
import com.mafuyu404.taczaddon.refit.network.RefitSourceSnapshotPacket;
import com.mafuyu404.taczaddon.refit.network.RefreshRefitScreenPacket;
import com.mafuyu404.taczaddon.refit.network.VirtualAttachmentRefitPacket;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public class NetworkHandler {
    private static final String PROTOCOL = "3.0";

    public static void register(IEventBus modBus) {
        modBus.addListener(NetworkHandler::registerPayloads);
    }

    private static void registerPayloads(RegisterPayloadHandlersEvent event) {
        // The addon sends server-authoritative inventory and UI state packets, so both
        // sides must negotiate the same channel instead of treating it as optional.
        PayloadRegistrar registrar = event.registrar(TACZaddon.MODID).versioned(PROTOCOL);
        registrar.playToClient(
                ConfigSyncPacket.TYPE, ConfigSyncPacket.STREAM_CODEC, ConfigSyncPacket::handle);
        registrar.playToServer(
                GunSmithCraftRequestPacket.TYPE,
                GunSmithCraftRequestPacket.STREAM_CODEC,
                GunSmithCraftRequestPacket::handle);
        registrar.playToClient(
                GunSmithCraftResultPacket.TYPE,
                GunSmithCraftResultPacket.STREAM_CODEC,
                GunSmithCraftResultPacket::handle);
        registrar.playToServer(
                RefitSourceRefreshRequestPacket.TYPE,
                RefitSourceRefreshRequestPacket.STREAM_CODEC,
                RefitSourceRefreshRequestPacket::handle);
        registrar.playToClient(
                RefitSourceSnapshotPacket.TYPE,
                RefitSourceSnapshotPacket.STREAM_CODEC,
                RefitSourceSnapshotPacket::handle);
        registrar.playToServer(
                RefitExternalAttachmentInstallPacket.TYPE,
                RefitExternalAttachmentInstallPacket.STREAM_CODEC,
                RefitExternalAttachmentInstallPacket::handle);
        registrar.playToClient(
                RuleSyncPacket.TYPE, RuleSyncPacket.STREAM_CODEC, RuleSyncPacket::handle);
        registrar.playToClient(
                ContainerReaderPacket.TYPE,
                ContainerReaderPacket.STREAM_CODEC,
                ContainerReaderPacket::handle);
        registrar.playToServer(
                ContainerPositionPacket.TYPE,
                ContainerPositionPacket.STREAM_CODEC,
                ContainerPositionPacket::handle);
        registrar.playToServer(
                SwitchGunPacket.TYPE, SwitchGunPacket.STREAM_CODEC, SwitchGunPacket::handle);
        registrar.playToServer(
                AmmoBoxCollectPacket.TYPE,
                AmmoBoxCollectPacket.STREAM_CODEC,
                AmmoBoxCollectPacket::handle);
        registrar.playToServer(
                VirtualAttachmentRefitPacket.TYPE,
                VirtualAttachmentRefitPacket.STREAM_CODEC,
                VirtualAttachmentRefitPacket::handle);
        registrar.playToClient(
                RefreshRefitScreenPacket.TYPE,
                RefreshRefitScreenPacket.STREAM_CODEC,
                RefreshRefitScreenPacket::handle);
    }

    public static void sendToClient(ServerPlayer player, CustomPacketPayload packet) {
        PacketDistributor.sendToPlayer(player, packet);
    }

    public static void sendToServer(CustomPacketPayload packet) {
        PacketDistributor.sendToServer(packet);
    }
}
