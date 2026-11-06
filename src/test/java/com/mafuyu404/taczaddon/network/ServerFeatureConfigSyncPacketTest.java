package com.mafuyu404.taczaddon.network;

import com.mafuyu404.taczaddon.client.config.ClientSyncedConfig;
import com.mafuyu404.taczaddon.config.CommonConfig;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.DecoderException;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Wire contract of the server-owned feature policy packet.
 */
class ServerFeatureConfigSyncPacketTest {

    @AfterEach
    void reset() {
        ClientSyncedConfig.resetToSafeDefaults();
    }

    @Test
    void fastSwapPolicySurvivesTheRoundTrip() {
        FriendlyByteBuf buffer = new FriendlyByteBuf(
                Unpooled.buffer()
        );

        ServerFeatureConfigSyncPacket.encode(
                new ServerFeatureConfigSyncPacket(8, true, false),
                buffer
        );
        ServerFeatureConfigSyncPacket decoded =
                ServerFeatureConfigSyncPacket.decode(buffer);

        assertEquals(8, decoded.batchCraftMax());
        assertTrue(decoded.enableShootWhileReloading());
        assertFalse(decoded.enableFastSwapGun());
        assertEquals(0, buffer.readableBytes());

        FriendlyByteBuf second = new FriendlyByteBuf(Unpooled.buffer());
        ServerFeatureConfigSyncPacket.encode(
                new ServerFeatureConfigSyncPacket(1, false, true),
                second
        );
        assertTrue(
                ServerFeatureConfigSyncPacket.decode(second)
                        .enableFastSwapGun()
        );
    }

    @Test
    void invalidBatchMaximumIsRejectedAtTheDecodeBoundary() {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        buffer.writeVarInt(CommonConfig.MAX_BATCH_CRAFT + 1);
        buffer.writeBoolean(true);
        buffer.writeBoolean(true);

        assertThrows(
                DecoderException.class,
                () -> ServerFeatureConfigSyncPacket.decode(buffer)
        );
    }

    @Test
    void clientPredictionFailsClosedUntilTheServerSynchronizes() {
        ClientSyncedConfig.resetToSafeDefaults();
        assertFalse(
                ClientSyncedConfig.enableFastSwapGun(),
                "a new connection must not inherit the previous server policy"
        );

        ClientSyncedConfig.apply(4, false, true);
        assertTrue(ClientSyncedConfig.enableFastSwapGun());

        ClientSyncedConfig.apply(4, false, false);
        assertFalse(
                ClientSyncedConfig.enableFastSwapGun(),
                "a server that disables fast swap must disable prediction"
        );

        ClientSyncedConfig.resetToSafeDefaults();
        assertFalse(ClientSyncedConfig.enableFastSwapGun());
    }
}
