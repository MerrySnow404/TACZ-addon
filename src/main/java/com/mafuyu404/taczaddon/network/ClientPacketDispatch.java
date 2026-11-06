package com.mafuyu404.taczaddon.network;

import net.minecraftforge.network.NetworkEvent;

import java.util.Objects;
import java.util.function.Supplier;

public final class ClientPacketDispatch {
    private static ClientPacketReceiver receiver;
    private ClientPacketDispatch() {}
    public static void bind(ClientPacketReceiver value) { receiver = Objects.requireNonNull(value); }
    public static void handle(GunSmithCraftResultPacket message, Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> Objects.requireNonNull(receiver, "Client packet receiver not initialized").receive(message));
        context.setPacketHandled(true);
    }
    public static void handle(GunSmithSourceSnapshotPacket message, Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> Objects.requireNonNull(receiver, "Client packet receiver not initialized").receive(message));
        context.setPacketHandled(true);
    }
    public static void handle(RefitSourceSnapshotPacket message, Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> Objects.requireNonNull(receiver, "Client packet receiver not initialized").receive(message));
        context.setPacketHandled(true);
    }
    public static void handle(ServerFeatureConfigSyncPacket message, Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> Objects.requireNonNull(receiver, "Client packet receiver not initialized").receive(message));
        context.setPacketHandled(true);
    }
    public static void handle(AttachmentDetailRuleStatePacket message, Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> Objects.requireNonNull(receiver, "Client packet receiver not initialized").receive(message));
        context.setPacketHandled(true);
    }
    public static void handle(LiberateAttachmentStatePacket message, Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> Objects.requireNonNull(receiver, "Client packet receiver not initialized").receive(message));
        context.setPacketHandled(true);
    }
}
