package com.mafuyu404.taczaddon.camera.client;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class AimCameraStateTest {
    private final AimCameraState state = new AimCameraState();

    private void hold(long time, boolean aiming, boolean down) {
        state.update(time, true, aiming, true, down, true);
    }

    private void assertIdle() {
        assertFalse(state.isAimActive());
        assertFalse(state.isTemporaryFirstPersonRequested());
    }

    @Test
    void adsStartsImmediatelyButRequestWaitsExactly110Ms() {
        hold(1000, true, true);
        assertTrue(state.isAimActive());
        assertFalse(state.isTemporaryFirstPersonRequested());
        hold(1109, true, true);
        assertFalse(state.isTemporaryFirstPersonRequested());
        hold(1110, true, true);
        assertTrue(state.isTemporaryFirstPersonRequested());
        hold(3000, true, true);
        assertTrue(state.isTemporaryFirstPersonRequested());
    }

    @Test
    void operatorEndingAdsClearsBothEvenWithKeyHeld() {
        hold(0, true, true);
        hold(110, true, true);
        hold(111, false, true);
        assertIdle();
    }

    @Test
    void losingGunWhilePendingCancelsAndNewGunGetsFullDelay() {
        hold(0, true, true);
        state.update(80, false, true, true, true, true);
        assertIdle();
        hold(1000, true, true);
        assertFalse(state.isTemporaryFirstPersonRequested());
        hold(1109, true, true);
        assertFalse(state.isTemporaryFirstPersonRequested());
        hold(1110, true, true);
        assertTrue(state.isTemporaryFirstPersonRequested());
    }

    @Test
    void worldLossAndPlayerReplacementResetPendingAndActiveRequests() {
        for (long time : new long[] {50, 110}) {
            hold(0, true, true);
            hold(time, true, true);
            state.reset();
            assertIdle();
            hold(2000, true, true);
            assertFalse(state.isTemporaryFirstPersonRequested());
            state.reset();
        }
    }

    @Test
    void holdReleaseIsImmediateEvenBeforeOperatorTickUpdates() {
        hold(0, true, true);
        hold(110, true, true);
        hold(111, true, false);
        assertIdle();
    }

    @Test
    void toggleFollowsOperatorWithoutPhysicalKeyHold() {
        state.update(0, true, true, false, false, true);
        assertTrue(state.isAimActive());
        state.update(110, true, true, false, false, true);
        assertTrue(state.isTemporaryFirstPersonRequested());
        state.update(111, true, false, false, true, true);
        assertIdle();
    }

    @Test
    void rapidRepeatedAdsDoesNotReusePreviousDeadline() {
        for (int start = 0; start < 1000; start += 100) {
            hold(start, true, true);
            hold(start + 90, true, true);
            assertFalse(state.isTemporaryFirstPersonRequested());
            hold(start + 95, false, false);
            assertIdle();
        }
        hold(1000, true, true);
        hold(1109, true, true);
        assertFalse(state.isTemporaryFirstPersonRequested());
        hold(1110, true, true);
        assertTrue(state.isTemporaryFirstPersonRequested());
    }

    @Test
    void featureToggleOnlyControlsTemporaryRequestAndRestartsDelay() {
        hold(0, true, true);
        hold(110, true, true);
        state.update(120, true, true, true, true, false);
        assertTrue(state.isAimActive());
        assertFalse(state.isTemporaryFirstPersonRequested());
        hold(200, true, true);
        hold(309, true, true);
        assertFalse(state.isTemporaryFirstPersonRequested());
        hold(310, true, true);
        assertTrue(state.isTemporaryFirstPersonRequested());
    }

    @Test
    void invalidPlayerSpectatorOrMenuCancelsEvenIfOperatorStillAims() {
        hold(0, true, true);
        hold(110, true, true);
        state.update(111, false, true, false, false, true);
        assertIdle();
    }
}
