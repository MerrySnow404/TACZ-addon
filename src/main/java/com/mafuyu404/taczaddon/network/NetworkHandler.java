package com.mafuyu404.taczaddon.network;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.Objects;

public final class NetworkHandler {
    /*
     * 2.9 added the attachment detail gamerule state.
     *
     * 2.10 appends the server-owned fast swap policy to the feature config
     * payload. The wire format changed, so the protocol version changed with
     * it: an older peer is rejected by Forge's channel negotiation instead of
     * mis-decoding a shorter payload. All existing packet ID assignments are
     * unchanged.
     *
     * 2.11 adds the recipe id to the gunsmith source refresh request and the
     * authorization/truncation/aggregate fields to the source snapshot. Both
     * formats changed, so the protocol changed again while every packet ID
     * stayed in place.
     */
    private static final String PROTOCOL = "2.12";

    public static final SimpleChannel CHANNEL =
            NetworkRegistry.newSimpleChannel(
                    Objects.requireNonNull(
                            ResourceLocation.tryBuild(
                                    "taczaddon",
                                    "sync_data"
                            )
                    ),
                    () -> PROTOCOL,
                    PROTOCOL::equals,
                    PROTOCOL::equals
            );

    private NetworkHandler() {
    }

    public static void sendToClient(
            ServerPlayer player,
            Object packet
    ) {
        CHANNEL.send(
                PacketDistributor.PLAYER.with(() -> player),
                packet
        );
    }

}
