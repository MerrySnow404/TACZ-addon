package com.mafuyu404.taczaddon.refit.network;

import com.mafuyu404.taczaddon.TACZaddon;
import com.mafuyu404.taczaddon.network.NetworkHandler;
import com.mafuyu404.taczaddon.network.ServerboundPacketGuard;
import com.mafuyu404.taczaddon.refit.RefitSourceResolver;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record RefitSourceRefreshRequestPacket(long requestId) implements CustomPacketPayload {
    public static final Type<RefitSourceRefreshRequestPacket> TYPE =
            new Type<>(
                    ResourceLocation.fromNamespaceAndPath(TACZaddon.MODID, "refit_source_refresh"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RefitSourceRefreshRequestPacket>
            STREAM_CODEC =
                    StreamCodec.of(
                            (buffer, packet) -> {
                                buffer.writeLong(packet.requestId);
                            },
                            buffer -> new RefitSourceRefreshRequestPacket(buffer.readLong()));

    public static void handle(RefitSourceRefreshRequestPacket packet, IPayloadContext context) {
        context.enqueueWork(
                () -> {
                    if (!(context.player() instanceof ServerPlayer player)) return;
                    if (ServerboundPacketGuard.isRateLimited(player, TYPE.id(), 10)) return;
                    if (!RefitSourceResolver.canUseSources(player)) return;
                    NetworkHandler.sendToClient(
                            player,
                            new RefitSourceSnapshotPacket(
                                    packet.requestId,
                                    RefitSourceResolver.resolveExternalCandidates(player)));
                });
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
