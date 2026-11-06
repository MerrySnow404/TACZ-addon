package com.mafuyu404.taczaddon.gunsmith.network;

import com.mafuyu404.taczaddon.TACZaddon;
import com.mafuyu404.taczaddon.gunsmith.GunSmithCraftService;
import com.mafuyu404.taczaddon.inventory.network.ContainerPositionPacket;
import com.mafuyu404.taczaddon.network.NetworkHandler;
import com.mafuyu404.taczaddon.network.ServerboundPacketGuard;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record GunSmithCraftRequestPacket(
        int containerId, long requestId, ResourceLocation recipeId, int requestedCount)
        implements CustomPacketPayload {
    public static final Type<GunSmithCraftRequestPacket> TYPE =
            new Type<>(
                    ResourceLocation.fromNamespaceAndPath(
                            TACZaddon.MODID, "gunsmith_craft_request"));
    public static final StreamCodec<RegistryFriendlyByteBuf, GunSmithCraftRequestPacket>
            STREAM_CODEC =
                    StreamCodec.of(
                            (buffer, packet) -> {
                                buffer.writeVarInt(packet.containerId);
                                buffer.writeLong(packet.requestId);
                                buffer.writeResourceLocation(packet.recipeId);
                                buffer.writeVarInt(packet.requestedCount);
                            },
                            buffer ->
                                    new GunSmithCraftRequestPacket(
                                            buffer.readVarInt(),
                                            buffer.readLong(),
                                            buffer.readResourceLocation(),
                                            buffer.readVarInt()));

    public static void handle(GunSmithCraftRequestPacket packet, IPayloadContext context) {
        context.enqueueWork(
                () -> {
                    if (!(context.player() instanceof ServerPlayer player)) return;
                    if (ServerboundPacketGuard.isRateLimited(player, TYPE.id(), 2)) return;
                    handleOnServer(packet, player);
                });
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static void handleOnServer(GunSmithCraftRequestPacket packet, ServerPlayer player) {
        var result =
                GunSmithCraftService.craft(
                        player,
                        new GunSmithCraftService.Request(
                                packet.containerId,
                                packet.requestId,
                                packet.recipeId,
                                packet.requestedCount));
        NetworkHandler.sendToClient(
                player,
                new GunSmithCraftResultPacket(
                        packet.containerId,
                        packet.requestId,
                        result.success(),
                        result.craftedExecutions(),
                        result.outputPerCraft(),
                        result.failure()));
        ContainerPositionPacket.refreshStoredSnapshot(player);
    }
}
