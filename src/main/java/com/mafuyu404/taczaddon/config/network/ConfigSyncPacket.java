package com.mafuyu404.taczaddon.config.network;

import com.mafuyu404.taczaddon.TACZaddon;
import com.mafuyu404.taczaddon.config.ClientSyncedConfig;
import com.mafuyu404.taczaddon.config.CommonConfig;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record ConfigSyncPacket(
        boolean shootWhileReloading,
        boolean fastSwapGun,
        boolean nearbyContainerSources,
        int containerScanRadius,
        int batchCraftMax)
        implements CustomPacketPayload {
    public static final Type<ConfigSyncPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(TACZaddon.MODID, "config_sync"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ConfigSyncPacket> STREAM_CODEC =
            StreamCodec.of(
                    (buffer, packet) -> {
                        buffer.writeBoolean(packet.shootWhileReloading);
                        buffer.writeBoolean(packet.fastSwapGun);
                        buffer.writeBoolean(packet.nearbyContainerSources);
                        buffer.writeVarInt(packet.containerScanRadius);
                        buffer.writeVarInt(packet.batchCraftMax);
                    },
                    buffer ->
                            new ConfigSyncPacket(
                                    buffer.readBoolean(),
                                    buffer.readBoolean(),
                                    buffer.readBoolean(),
                                    buffer.readVarInt(),
                                    buffer.readVarInt()));

    public static void handle(ConfigSyncPacket packet, IPayloadContext context) {
        context.enqueueWork(
                () -> {
                    ClientSyncedConfig.apply(
                            packet.shootWhileReloading,
                            packet.fastSwapGun,
                            packet.nearbyContainerSources,
                            packet.containerScanRadius,
                            packet.batchCraftMax);
                });
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static ConfigSyncPacket fromServerConfig() {
        return new ConfigSyncPacket(
                CommonConfig.enableShootWhileReloading(),
                CommonConfig.enableFastSwapGun(),
                CommonConfig.enableNearbyContainerSources(),
                CommonConfig.getContainerScanRadius(),
                CommonConfig.getBatchCraftMax());
    }
}
