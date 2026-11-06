package com.mafuyu404.taczaddon.ammo;

import com.mafuyu404.taczaddon.ammo.api.IncompleteConsumptionException;

import com.mafuyu404.taczaddon.ammo.api.ConsumptionStatus;

import com.mafuyu404.taczaddon.ammo.api.ConsumptionOutcome;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AmmoConsumptionOrchestratorTest {

    private static AmmoConsumptionOrchestrator.AmmoSource source(
            List<Integer> calls,
            ConsumptionOutcome outcome
    ) {
        return remaining -> {
            calls.add(remaining);
            return outcome.withConsumed(
                    Math.min(remaining, outcome.consumed())
            );
        };
    }

    private static AmmoConsumptionOrchestrator.AmmoSource fixed(
            List<Integer> calls,
            int consumed
    ) {
        return remaining -> {
            calls.add(remaining);
            return ConsumptionOutcome.confirmed(
                    Math.min(remaining, consumed)
            );
        };
    }

    @Test
    void nativeAlreadySatisfiesRequestCallsNoSources() {
        List<Integer> calls = new ArrayList<>();

        ConsumptionOutcome outcome =
                AmmoConsumptionOrchestrator.consumeRemaining(
                        30,
                        30,
                        source(calls, ConsumptionOutcome.confirmed(0)),
                        source(calls, ConsumptionOutcome.confirmed(0))
                );

        assertEquals(30, outcome.consumed());
        assertTrue(calls.isEmpty());
    }

    @Test
    void zeroConsumptionStillContinuesToTheNextSource() {
        List<Integer> calls = new ArrayList<>();

        ConsumptionOutcome outcome =
                AmmoConsumptionOrchestrator.consumeRemaining(
                        10,
                        0,
                        fixed(calls, 0),
                        fixed(calls, 6),
                        fixed(calls, 4)
                );

        assertEquals(10, outcome.consumed());
        assertTrue(outcome.mayContinue());
        assertEquals(List.of(10, 10, 4), calls);
    }

    @Test
    void priorityOrderReceivesTheRealRemainingAmount() {
        List<Integer> calls = new ArrayList<>();

        ConsumptionOutcome outcome =
                AmmoConsumptionOrchestrator.consumeRemaining(
                        30,
                        10,
                        fixed(calls, 12),
                        fixed(calls, 8),
                        fixed(calls, 100)
                );

        assertEquals(30, outcome.consumed());
        assertEquals(List.of(20, 8), calls);
    }

    @Test
    void totalNeverExceedsTheRequest() {
        List<Integer> calls = new ArrayList<>();

        ConsumptionOutcome outcome =
                AmmoConsumptionOrchestrator.consumeRemaining(
                        10,
                        3,
                        fixed(calls, 100),
                        fixed(calls, 100)
                );

        assertEquals(10, outcome.consumed());
        assertEquals(List.of(7), calls);
    }

    @Test
    void knownPartialFailureKeepsConfirmedRoundsAndStopsLaterSources() {
        List<Integer> calls = new ArrayList<>();

        ConsumptionOutcome outcome =
                AmmoConsumptionOrchestrator.consumeRemaining(
                        10,
                        0,
                        fixed(calls, 0),
                        source(
                                calls,
                                ConsumptionOutcome.stoppedConfirmed(4)
                        ),
                        source(calls, ConsumptionOutcome.confirmed(6))
                );

        assertEquals(4, outcome.consumed());
        assertEquals(
                ConsumptionStatus.STOPPED_CONFIRMED,
                outcome.status()
        );
        assertFalse(outcome.mayContinue());
        assertEquals(
                List.of(10, 10),
                calls,
                "later sources must not be asked for the deficit"
        );
    }

    @Test
    void unknownMutationKeepsEarlierRoundsAndStops() {
        List<Integer> calls = new ArrayList<>();

        ConsumptionOutcome outcome =
                AmmoConsumptionOrchestrator.consumeRemaining(
                        10,
                        2,
                        source(calls, ConsumptionOutcome.stoppedUnknown()),
                        remaining -> {
                            calls.add(-1);
                            return ConsumptionOutcome.confirmed(8);
                        }
                );

        assertEquals(2, outcome.consumed());
        assertEquals(
                ConsumptionStatus.STOPPED_UNKNOWN,
                outcome.status()
        );
        assertEquals(
                List.of(8),
                calls,
                "an unknown external mutation must not trigger more extraction"
        );
    }

    @Test
    void incompleteConsumptionExceptionIsTreatedAsUnknown() {
        List<Integer> calls = new ArrayList<>();

        ConsumptionOutcome outcome =
                AmmoConsumptionOrchestrator.consumeRemaining(
                        10,
                        3,
                        remaining -> {
                            calls.add(remaining);
                            throw new IncompleteConsumptionException(
                                    new NoSuchMethodError("bridge")
                            );
                        },
                        source(calls, ConsumptionOutcome.confirmed(7))
                );

        assertEquals(3, outcome.consumed());
        assertEquals(
                ConsumptionStatus.STOPPED_UNKNOWN,
                outcome.status()
        );
        assertEquals(List.of(7), calls);
    }

    @Test
    void runtimeFailureAfterConfirmedRoundsKeepsThem() {
        List<Integer> calls = new ArrayList<>();

        ConsumptionOutcome outcome =
                AmmoConsumptionOrchestrator.consumeRemaining(
                        10,
                        4,
                        remaining -> {
                            calls.add(remaining);
                            throw new IllegalStateException(
                                    "finalisation failed"
                            );
                        },
                        source(calls, ConsumptionOutcome.confirmed(6))
                );

        assertEquals(4, outcome.consumed());
        assertEquals(
                ConsumptionStatus.STOPPED_UNKNOWN,
                outcome.status()
        );
        assertEquals(List.of(6), calls);
    }

    @Test
    void invalidRequestReturnsZeroWithoutSourceCalls() {
        List<Integer> calls = new ArrayList<>();

        assertEquals(
                0,
                AmmoConsumptionOrchestrator.consumeRemaining(
                        0,
                        5,
                        source(calls, ConsumptionOutcome.confirmed(1))
                ).consumed()
        );
        assertEquals(
                0,
                AmmoConsumptionOrchestrator.consumeRemaining(
                        -1,
                        5,
                        source(calls, ConsumptionOutcome.confirmed(1))
                ).consumed()
        );
        assertTrue(calls.isEmpty());
    }

    @Test
    void virtualAndCreativeAmmoKeepNativeSemantics() {
        assertTrue(AmmoConsumptionOrchestrator.usesNativeVirtualAmmo(
                true, false, false
        ));
        assertTrue(AmmoConsumptionOrchestrator.usesNativeVirtualAmmo(
                false, true, true
        ));
        assertFalse(AmmoConsumptionOrchestrator.usesNativeVirtualAmmo(
                true, true, false
        ));
        assertFalse(AmmoConsumptionOrchestrator.usesNativeVirtualAmmo(
                false, false, false
        ));
    }

    @Test
    void clampConsumedHandlesAllBounds() {
        assertEquals(0, AmmoConsumptionOrchestrator.clampConsumed(10, -5));
        assertEquals(0, AmmoConsumptionOrchestrator.clampConsumed(10, 0));
        assertEquals(10, AmmoConsumptionOrchestrator.clampConsumed(10, 10));
        assertEquals(10, AmmoConsumptionOrchestrator.clampConsumed(10, 99));
        assertEquals(0, AmmoConsumptionOrchestrator.clampConsumed(0, 10));
    }
}
