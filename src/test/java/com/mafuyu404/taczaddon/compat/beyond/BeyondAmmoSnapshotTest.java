package com.mafuyu404.taczaddon.compat.beyond;

import com.mafuyu404.taczaddon.mixin.beyond.TaczAmmoCacheMixin;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class BeyondAmmoSnapshotTest {
    @Test
    void emptySnapshotClearsLastRoundOfSameNetwork() throws Exception {
        Map<String, Integer> cache = new HashMap<>(Map.of("tacz:9mm", 1));
        replace(cache, Map.of());
        assertTrue(cache.isEmpty());
    }

    @Test
    void snapshotDropsRemovedTypesAndCreativeWildcardButKeepsAvailableAmmo() throws Exception {
        Map<String, Integer> cache = new HashMap<>(Map.of(
                "tacz:9mm", 1, "*", Integer.MAX_VALUE));
        replace(cache, Map.of("tacz:556x45", 30));
        assertEquals(Map.of("tacz:556x45", 30), cache);
        replace(cache, Map.of("*", Integer.MAX_VALUE));
        assertEquals(Map.of("*", Integer.MAX_VALUE), cache);
    }

    private static void replace(Map<String, Integer> cache, Map<String, Integer> snapshot) throws Exception {
        var hook = TaczAmmoCacheMixin.class.getDeclaredMethod(
                "taczaddon$replaceSnapshot", Map.class, Map.class);
        hook.setAccessible(true);
        hook.invoke(null, cache, snapshot);
    }
}
