package com.mafuyu404.taczaddon.network;

import com.mafuyu404.taczaddon.config.CommonConfig;

import io.netty.handler.codec.DecoderException;
import net.minecraft.network.FriendlyByteBuf;

/**
 * Synchronizes server-owned policy needed by client request and prediction
 * logic.
 */
public final class ServerFeatureConfigSyncPacket {
    private final int batchCraftMax;
    private final boolean enableShootWhileReloading;
    private final boolean enableFastSwapGun;

    ServerFeatureConfigSyncPacket(
            int batchCraftMax,
            boolean enableShootWhileReloading,
            boolean enableFastSwapGun
    ) {
        this.batchCraftMax = batchCraftMax;
        this.enableShootWhileReloading =
                enableShootWhileReloading;
        this.enableFastSwapGun = enableFastSwapGun;
    }

    public static void encode(
            ServerFeatureConfigSyncPacket message,
            FriendlyByteBuf buffer
    ) {
        buffer.writeVarInt(message.batchCraftMax);
        buffer.writeBoolean(
                message.enableShootWhileReloading
        );
        buffer.writeBoolean(message.enableFastSwapGun);
    }

    public static ServerFeatureConfigSyncPacket decode(
            FriendlyByteBuf buffer
    ) {
        int batchCraftMax =
                buffer.readVarInt();

        if (batchCraftMax < CommonConfig.MIN_BATCH_CRAFT
                || batchCraftMax > CommonConfig.MAX_BATCH_CRAFT) {
            throw new DecoderException(
                    "Invalid gunsmith batch maximum: "
                            + batchCraftMax
            );
        }

        boolean enableShootWhileReloading =
                buffer.readBoolean();

        boolean enableFastSwapGun =
                buffer.readBoolean();

        return new ServerFeatureConfigSyncPacket(
                batchCraftMax,
                enableShootWhileReloading,
                enableFastSwapGun
        );
    }

    public int batchCraftMax() {
        return this.batchCraftMax;
    }

    public boolean enableShootWhileReloading() {
        return this.enableShootWhileReloading;
    }

    public boolean enableFastSwapGun() {
        return this.enableFastSwapGun;
    }
}
