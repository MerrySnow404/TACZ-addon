package com.mafuyu404.taczaddon.gunsmith;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class GunSmithCraftingSessionLifetimeTest {
    private static final long[] IDLE_DURATIONS_TICKS = {1_220L, 6_001L, 36_001L};

    @Test
    void elapsedGameTimeAloneNeverInvalidatesAValidSession() {
        GunSmithCraftingSessionManager.SessionLifecycleState state = validState();

        for (long elapsedTicks : IDLE_DURATIONS_TICKS) {
            assertTrue(
                    GunSmithCraftingSessionManager.isLifecycleValid(state),
                    () -> "valid session expired after " + elapsedTicks + " idle ticks");
        }
    }

    @Test
    void realLifecycleFailuresInvalidateTheSession() {
        assertFalse(
                isValid(
                        state(
                                false, true, true, true, true, true, true, true, true, true, true,
                                0.0D)),
                "player UUID changed");
        assertFalse(
                isValid(
                        state(
                                true, false, true, true, false, true, true, true, true, true, true,
                                0.0D)),
                "container id changed");
        assertFalse(
                isValid(
                        state(
                                true, true, false, true, true, true, true, true, true, true, true,
                                0.0D)),
                "dimension changed");
        assertFalse(
                isValid(
                        state(
                                true, true, true, false, false, false, false, true, true, true,
                                true, 0.0D)),
                "menu closed or replaced by another menu");
        assertFalse(
                isValid(
                        state(
                                true, true, true, true, false, true, true, true, true, true, true,
                                0.0D)),
                "active menu has another container id");
        assertFalse(
                isValid(
                        state(
                                true, true, true, true, true, false, true, true, true, true, true,
                                0.0D)),
                "menu table definition changed");
        assertFalse(
                isValid(
                        state(
                                true, true, true, true, true, true, false, true, true, true, true,
                                0.0D)),
                "menu stillValid rejected the player");
        assertFalse(
                isValid(
                        state(
                                true, true, true, true, true, true, true, false, false, false,
                                false, 0.0D)),
                "table chunk unloaded");
        assertFalse(
                isValid(
                        state(
                                true, true, true, true, true, true, true, true, false, false, false,
                                0.0D)),
                "table block entity replaced");
        assertFalse(
                isValid(
                        state(
                                true, true, true, true, true, true, true, true, true, false, true,
                                0.0D)),
                "table destroyed");
        assertFalse(
                isValid(
                        state(
                                true, true, true, true, true, true, true, true, true, true, false,
                                0.0D)),
                "table definition id changed");
        assertFalse(
                isValid(
                        state(
                                true,
                                true,
                                true,
                                true,
                                true,
                                true,
                                true,
                                true,
                                true,
                                true,
                                true,
                                64.000_001D)),
                "player exceeded interaction distance");
    }

    private static boolean isValid(GunSmithCraftingSessionManager.SessionLifecycleState state) {
        return GunSmithCraftingSessionManager.isLifecycleValid(state);
    }

    private static GunSmithCraftingSessionManager.SessionLifecycleState validState() {
        return state(true, true, true, true, true, true, true, true, true, true, true, 0.0D);
    }

    private static GunSmithCraftingSessionManager.SessionLifecycleState state(
            boolean playerMatches,
            boolean suppliedContainerMatches,
            boolean dimensionMatches,
            boolean gunsmithMenuOpen,
            boolean activeMenuContainerMatches,
            boolean menuTableDefinitionMatches,
            boolean menuStillValid,
            boolean tableLoaded,
            boolean expectedTableEntityPresent,
            boolean tableEntityActive,
            boolean tableDefinitionMatches,
            double distanceSquared) {
        return new GunSmithCraftingSessionManager.SessionLifecycleState(
                playerMatches,
                suppliedContainerMatches,
                dimensionMatches,
                gunsmithMenuOpen,
                activeMenuContainerMatches,
                menuTableDefinitionMatches,
                menuStillValid,
                tableLoaded,
                expectedTableEntityPresent,
                tableEntityActive,
                tableDefinitionMatches,
                distanceSquared);
    }
}
