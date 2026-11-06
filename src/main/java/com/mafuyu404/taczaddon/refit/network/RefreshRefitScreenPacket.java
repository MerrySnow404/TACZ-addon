package com.mafuyu404.taczaddon.refit.network;

import com.mafuyu404.taczaddon.TACZaddon;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record RefreshRefitScreenPacket(boolean refresh) implements CustomPacketPayload {

    public static final Type<RefreshRefitScreenPacket> TYPE =
            new Type<>(
                    ResourceLocation.fromNamespaceAndPath(TACZaddon.MODID, "refresh_refit_screen"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RefreshRefitScreenPacket>
            STREAM_CODEC =
                    ByteBufCodecs.BOOL
                            .<RegistryFriendlyByteBuf>cast()
                            .map(RefreshRefitScreenPacket::new, RefreshRefitScreenPacket::refresh);

    public static void handle(RefreshRefitScreenPacket packet, IPayloadContext context) {
        context.enqueueWork(
                () ->
                        com.mafuyu404.taczaddon.bootstrap.client.ClientPayloadHandler
                                .handleRefreshRefitScreen());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
