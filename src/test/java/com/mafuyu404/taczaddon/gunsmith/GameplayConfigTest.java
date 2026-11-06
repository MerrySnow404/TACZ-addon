package com.mafuyu404.taczaddon.gunsmith;

import static org.junit.jupiter.api.Assertions.*;

import com.mafuyu404.taczaddon.config.*;

import net.neoforged.neoforge.common.ModConfigSpec;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;

class GameplayConfigTest {
    @AfterEach
    void reset() {
        ClientSyncedConfig.reset();
    }

    @Test
    void defaultsAndRanges() {
        ModConfigSpec.ValueSpec radius =
                CommonConfig.SPEC.getSpec().get(List.of("GunSmithTable", "containerScanRadius"));
        ModConfigSpec.ValueSpec batch =
                CommonConfig.SPEC.getSpec().get(List.of("GunSmithTable", "batchCraftMax"));
        ModConfigSpec.ValueSpec shoot =
                CommonConfig.SPEC.getSpec().get(List.of("Gameplay", "enableShootWhileReloading"));
        assertEquals(3, radius.getDefault());
        assertTrue(radius.test(1));
        assertTrue(radius.test(16));
        assertFalse(radius.test(0));
        assertFalse(radius.test(17));
        assertEquals(64, batch.getDefault());
        assertTrue(batch.test(1));
        assertTrue(batch.test(64));
        assertFalse(batch.test(0));
        assertFalse(batch.test(65));
        assertEquals(true, shoot.getDefault());
    }

    @Test
    void serverPolicyClampsAndDisconnectFailsClosed() {
        ClientSyncedConfig.apply(true, true, true, Integer.MAX_VALUE, Integer.MAX_VALUE);
        assertTrue(ClientSyncedConfig.enableFastSwapGun());
        assertTrue(ClientSyncedConfig.enableShootWhileReloading());
        assertTrue(ClientSyncedConfig.enableNearbyContainerSources());
        assertEquals(16, ClientSyncedConfig.getContainerScanRadius());
        assertEquals(64, ClientSyncedConfig.getBatchCraftMax());
        ClientSyncedConfig.apply(true, true, true, Integer.MIN_VALUE, Integer.MIN_VALUE);
        assertEquals(1, ClientSyncedConfig.getContainerScanRadius());
        assertEquals(1, ClientSyncedConfig.getBatchCraftMax());
        ClientSyncedConfig.reset();
        assertFalse(ClientSyncedConfig.enableFastSwapGun());
        assertFalse(ClientSyncedConfig.enableShootWhileReloading());
        assertFalse(ClientSyncedConfig.enableNearbyContainerSources());
        assertEquals(3, ClientSyncedConfig.getContainerScanRadius());
        assertEquals(1, ClientSyncedConfig.getBatchCraftMax());
    }

    @Test
    void craftRequestIdsAreMonotonicPerSession() {
        var session =
                new GunSmithCraftingSessionManager.GunSmithCraftingSession(
                        java.util.UUID.randomUUID(),
                        1,
                        net.minecraft.world.level.Level.OVERWORLD,
                        net.minecraft.core.BlockPos.ZERO,
                        net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(
                                "tacz", "table"));
        assertFalse(session.acceptCraftRequestId(-1));
        assertTrue(session.acceptCraftRequestId(1));
        assertFalse(session.acceptCraftRequestId(1));
        assertFalse(session.acceptCraftRequestId(0));
        assertTrue(session.acceptCraftRequestId(2));
    }
}
