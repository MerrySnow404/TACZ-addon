package com.mafuyu404.taczaddon.compat.sophisticated.client;

/**
 * Pure client-tick scheduling state for the Sophisticated Backpacks cache.
 *
 * <p>The planner is deliberately free of Minecraft client-only and Sophisticated types so the
 * bootstrap ordering can be unit tested deterministically. {@link ClientEvent} owns the actual
 * virtual inventory and executes the returned action.
 *
 * <p>Lifecycle contract:
 *
 * <pre>
 * world join / player or level change
 *     exactly one BOOTSTRAP_REQUEST (never a rebuild on the same tick)
 *     cache refresh is deferred BACKPACK_CACHE_REFRESH_TICKS
 *
 * later ticks
 *     PERIODIC_SYNC_REQUEST only while holding a gun and every
 *         BACKPACK_SYNC_INTERVAL_TICKS (recovery, not correctness)
 *     REBUILD_CACHE when the refresh deadline passed or a payload
 *         invalidation reset it to 0
 * </pre>
 */
public final class BackpackCacheTickPlanner {

    public static final int BACKPACK_SYNC_INTERVAL_TICKS = 20;
    public static final int BACKPACK_CACHE_REFRESH_TICKS = 20;

    public enum Action {
        /**
         * Unconditional first request after a player/level identity change. The caller must request
         * contents and must not build or keep a virtual inventory from unsynchronized handlers on
         * this tick.
         */
        BOOTSTRAP_REQUEST,

        /** Low-frequency recovery polling while a gun is held. */
        PERIODIC_SYNC_REQUEST,

        /** Rebuild the virtual inventory from the current cache state. */
        REBUILD_CACHE,

        /** Nothing to do this tick. */
        WAIT
    }

    private Object cachedPlayer;
    private Object cachedLevel;

    private boolean backpackInitialSyncRequested;

    private long nextBackpackSyncTick;
    private long nextBackpackRefreshTick;

    public Action tick(Object player, Object level, boolean holdingGun, long gameTime) {
        if (cachedPlayer != player || cachedLevel != level) {

            cachedPlayer = player;
            cachedLevel = level;

            backpackInitialSyncRequested = false;

            nextBackpackSyncTick = 0L;
            nextBackpackRefreshTick = 0L;
        }

        /*
         * Exactly one unconditional bootstrap request per player/level
         * identity.
         *
         * The server response is asynchronous. Do not create a supposedly
         * valid empty wrapper/cache before BackpackStorage has received
         * authoritative inventory NBT. The caller returns after executing
         * this action and must not refresh the cache in the same branch.
         */
        if (!backpackInitialSyncRequested) {
            backpackInitialSyncRequested = true;

            nextBackpackSyncTick = gameTime + BACKPACK_SYNC_INTERVAL_TICKS;
            nextBackpackRefreshTick = gameTime + BACKPACK_CACHE_REFRESH_TICKS;

            return Action.BOOTSTRAP_REQUEST;
        }

        if (holdingGun && gameTime >= nextBackpackSyncTick) {

            nextBackpackSyncTick = gameTime + BACKPACK_SYNC_INTERVAL_TICKS;

            return Action.PERIODIC_SYNC_REQUEST;
        }

        if (gameTime >= nextBackpackRefreshTick) {
            nextBackpackRefreshTick = gameTime + BACKPACK_CACHE_REFRESH_TICKS;

            return Action.REBUILD_CACHE;
        }

        return Action.WAIT;
    }

    /**
     * @return true when the caller must drop its virtual inventory because the player or level
     *     identity changed
     */
    public boolean isNewIdentity(Object player, Object level) {
        if (cachedPlayer == player && cachedLevel == level) {
            return false;
        }

        cachedPlayer = player;
        cachedLevel = level;

        backpackInitialSyncRequested = false;

        nextBackpackSyncTick = 0L;
        nextBackpackRefreshTick = 0L;

        return true;
    }

    /**
     * Called after a BackpackContentsPayload refresh so the virtual inventory is rebuilt from the
     * fresh client storage on the next tick.
     */
    public void invalidateCache() {
        nextBackpackRefreshTick = 0L;
    }

    public void reset() {
        cachedPlayer = null;
        cachedLevel = null;
        backpackInitialSyncRequested = false;
        nextBackpackSyncTick = 0L;
        nextBackpackRefreshTick = 0L;
    }
}
