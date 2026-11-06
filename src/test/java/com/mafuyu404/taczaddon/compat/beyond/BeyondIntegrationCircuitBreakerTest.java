package com.mafuyu404.taczaddon.compat.beyond;

import com.mafuyu404.taczaddon.ammo.api.ConsumptionOutcome;
import com.mafuyu404.taczaddon.ammo.api.ConsumptionStatus;

import com.mafuyu404.taczaddon.ammo.AmmoConsumptionOrchestrator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class BeyondIntegrationCircuitBreakerTest {
    @AfterEach
    void reset() throws Exception {
        var latch = BeyondIntegrationCompat.class.getDeclaredField("linkageBroken");
        latch.setAccessible(true);
        latch.setBoolean(null, false);
        var warning = BeyondIntegrationCompat.class.getDeclaredField("LINKAGE_WARNING_LOGGED");
        warning.setAccessible(true);
        ((AtomicBoolean) warning.get(null)).set(false);
    }

    @Test
    void opaquePartialFailureStopsThisRequestThenOtherSourcesResume() {
        AtomicInteger externalMutations = new AtomicInteger();
        var outcome = AmmoConsumptionOrchestrator.consumeRemaining(10, 2,
                remaining -> ConsumptionOutcome.confirmed(
                        BeyondIntegrationCompat.runGuarded(remaining, () -> {
                            externalMutations.incrementAndGet();
                            throw new NoSuchMethodError("failed after partial external mutation");
                        })),
                remaining -> { fail("Unknown external consumption must not trigger more extraction"); return null; });
        assertEquals(2, outcome.consumed());
        assertEquals(ConsumptionStatus.STOPPED_UNKNOWN, outcome.status());
        assertEquals(1, externalMutations.get());

        assertEquals(10, AmmoConsumptionOrchestrator.consumeRemaining(10, 2,
                remaining -> ConsumptionOutcome.confirmed(
                        BeyondIntegrationCompat.runGuarded(remaining, () -> {
                            fail("Broken external bridge retried"); return 0;
                        })),
                remaining -> ConsumptionOutcome
                        .confirmed(remaining)).consumed());
        assertTrue(com.mafuyu404.taczaddon.compat.curios.CuriosGuardProbe.runGuarded(() -> true));
        assertTrue(com.mafuyu404.taczaddon.compat.jei.JeiGuardProbe.runGuarded(() -> true));
    }

    @Test
    void successfulBridgeIsBoundedAndMissesDoNotTripBreaker() {
        assertEquals(0, BeyondIntegrationCompat.runGuarded(4, () -> -1));
        assertEquals(4, BeyondIntegrationCompat.runGuarded(4, () -> 10));
        assertEquals(2, BeyondIntegrationCompat.runGuarded(4, () -> 2));
    }

    @Test
    void unrelatedFailuresAreNotSwallowed() {
        assertThrows(IllegalStateException.class, () -> BeyondIntegrationCompat.runGuarded(4,
                () -> { throw new IllegalStateException("fixture"); }));
        assertThrows(AssertionError.class, () -> BeyondIntegrationCompat.runGuarded(4,
                () -> { throw new AssertionError("fixture"); }));
        assertEquals(4, BeyondIntegrationCompat.runGuarded(4, () -> 4));
    }
}
