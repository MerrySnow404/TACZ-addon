package com.mafuyu404.taczaddon.compat.sophisticated.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mafuyu404.taczaddon.compat.sophisticated.client.BackpackCacheTickPlanner.Action;

import org.junit.jupiter.api.Test;

/**
 * Client cache lifecycle tests.
 *
 * <p>The first-tick bootstrap branch must request authoritative contents exactly once and must
 * never also rebuild the virtual inventory in the same branch: the cache is intentionally deferred
 * until the server response (or the refresh deadline) makes synchronized contents available.
 */
class BackpackCacheTickPlannerTest {

    private static final Object PLAYER = new Object();
    private static final Object LEVEL = new Object();

    @Test
    void worldJoinFirstTickRequestsBootstrapOnly() {
        BackpackCacheTickPlanner planner = new BackpackCacheTickPlanner();

        Action action = planner.tick(PLAYER, LEVEL, false, 100L);

        assertEquals(Action.BOOTSTRAP_REQUEST, action);
    }

    @Test
    void bootstrapBranchDoesNotAlsoRebuildCacheInTheSameTick() {
        BackpackCacheTickPlanner planner = new BackpackCacheTickPlanner();

        /*
         * The bootstrap and rebuild are mutually exclusive per tick. The
         * planner returns exactly one action, and the bootstrap branch defers
         * the first refresh by BACKPACK_CACHE_REFRESH_TICKS.
         */
        Action action = planner.tick(PLAYER, LEVEL, true, 100L);

        assertEquals(Action.BOOTSTRAP_REQUEST, action);
        assertNotEquals(Action.REBUILD_CACHE, action);

        for (long gameTime = 101L;
                gameTime < 100L + BackpackCacheTickPlanner.BACKPACK_CACHE_REFRESH_TICKS;
                gameTime++) {
            assertNotEquals(
                    Action.REBUILD_CACHE,
                    planner.tick(PLAYER, LEVEL, false, gameTime),
                    "cache must not be rebuilt before the deferred " + "refresh deadline");
        }

        assertEquals(
                Action.REBUILD_CACHE,
                planner.tick(
                        PLAYER,
                        LEVEL,
                        false,
                        100L + BackpackCacheTickPlanner.BACKPACK_CACHE_REFRESH_TICKS));
    }

    @Test
    void bootstrapRequestDoesNotDependOnHoldingGun() {
        BackpackCacheTickPlanner planner = new BackpackCacheTickPlanner();

        assertEquals(Action.BOOTSTRAP_REQUEST, planner.tick(PLAYER, LEVEL, false, 100L));
    }

    @Test
    void periodicSyncOnlyWhileHoldingGunAtInterval() {
        BackpackCacheTickPlanner planner = new BackpackCacheTickPlanner();

        planner.tick(PLAYER, LEVEL, true, 100L);

        int syncTicks = 0;

        for (long gameTime = 101L;
                gameTime <= 100L + 5L * BackpackCacheTickPlanner.BACKPACK_SYNC_INTERVAL_TICKS;
                gameTime++) {
            Action action = planner.tick(PLAYER, LEVEL, true, gameTime);

            if (action == Action.PERIODIC_SYNC_REQUEST) {
                syncTicks++;
            }
        }

        /*
         * Recovery polling fires once per 20-tick interval, never every
         * client tick.
         */
        assertEquals(5, syncTicks);

        Action noGun = planner.tick(PLAYER, LEVEL, false, 100L + 20L);
        assertNotEquals(Action.PERIODIC_SYNC_REQUEST, noGun);
    }

    @Test
    void playerOrLevelChangeResetsBootstrapAndRequestsAgain() {
        BackpackCacheTickPlanner planner = new BackpackCacheTickPlanner();

        planner.tick(PLAYER, LEVEL, false, 100L);
        assertFalse(planner.isNewIdentity(PLAYER, LEVEL));

        Object otherLevel = new Object();

        assertTrue(planner.isNewIdentity(PLAYER, otherLevel));
        assertEquals(Action.BOOTSTRAP_REQUEST, planner.tick(PLAYER, otherLevel, false, 200L));
    }

    @Test
    void payloadInvalidationRebuildsOnNextTick() {
        BackpackCacheTickPlanner planner = new BackpackCacheTickPlanner();

        planner.tick(PLAYER, LEVEL, false, 100L);

        planner.invalidateCache();

        assertEquals(Action.REBUILD_CACHE, planner.tick(PLAYER, LEVEL, false, 105L));
    }

    @Test
    void resetClearsIdentityAndBootstrapState() {
        BackpackCacheTickPlanner planner = new BackpackCacheTickPlanner();

        planner.tick(PLAYER, LEVEL, false, 100L);

        planner.reset();

        assertTrue(planner.isNewIdentity(PLAYER, LEVEL));
        assertEquals(Action.BOOTSTRAP_REQUEST, planner.tick(PLAYER, LEVEL, false, 200L));
    }
}
