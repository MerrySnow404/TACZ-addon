package com.mafuyu404.taczaddon.storage;

import com.mafuyu404.taczaddon.storage.api.CraftingSourceKey;
import com.mafuyu404.taczaddon.storage.NearbyInventorySourceResolver;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class NearbyLinkedStorageDedupTest {
    @Test
    void linkedEndpointsWithDifferentCapabilityWrappersAreCountedOnce() {
        var seen = new NearbyInventorySourceResolver.SourceIdentities();
        UUID group = UUID.randomUUID();
        assertTrue(seen.add(key(1), new Object(), group));
        assertFalse(seen.add(key(2), new Object(), UUID.fromString(group.toString())));
        assertTrue(seen.add(key(3), new Object(), UUID.randomUUID()));
        assertTrue(seen.add(key(4), new Object(), null));
        assertTrue(seen.add(key(5), new Object(), null));
    }

    @Test
    void ordinaryHandlerIdentityAndPositionDeduplicationArePreserved() {
        var seen = new NearbyInventorySourceResolver.SourceIdentities();
        Object handler = new Object();
        assertTrue(seen.add(key(1), handler, null));
        assertFalse(seen.add(key(2), handler, null));
        assertFalse(seen.add(key(1), new Object(), null));
        // A rejected alias must not claim the position of a later valid source.
        assertTrue(seen.add(key(2), new Object(), null));
    }

    private static CraftingSourceKey key(int x) {
        return new CraftingSourceKey.BlockEntity(Level.OVERWORLD, new BlockPos(x, 64, 0));
    }
}
