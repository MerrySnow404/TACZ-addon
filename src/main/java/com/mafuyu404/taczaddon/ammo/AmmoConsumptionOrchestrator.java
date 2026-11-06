package com.mafuyu404.taczaddon.ammo;

import com.mojang.logging.LogUtils;

import org.slf4j.Logger;

import java.util.List;

/**
 * Internal orchestration for supplemental ammo consumption.
 *
 * <p>Every supplemental source reports a {@link ConsumptionOutcome} that says how many rounds were
 * confirmed, whether the operation finished, and whether the next source may still be asked. The
 * priority order is supplied by the caller: consumed-so-far, Beyond compatible source, backpack
 * fallback, Curios.
 *
 * <p>This deliberately does not know about TaCZ, Beyond Integration, Sophisticated Backpacks or
 * Curios.
 */
public final class AmmoConsumptionOrchestrator {
    private static final Logger LOGGER = LogUtils.getLogger();

    private AmmoConsumptionOrchestrator() {}

    /** One supplemental ammo source. */
    @FunctionalInterface
    public interface AmmoSource {
        /**
         * @param remaining rounds still missing for this request
         * @return what this source could confirm for this request
         */
        ConsumptionOutcome consume(int remaining);
    }

    /**
     * Result of one real consumption attempt.
     *
     * <ul>
     *   <li>{@link Status#CONFIRMED}: an exact amount, including a normal zero; later sources may
     *       still be asked for the remainder;
     *   <li>{@link Status#STOPPED_CONFIRMED}: an exact amount, but the source stopped abnormally,
     *       so no later source may run for this request;
     *   <li>{@link Status#STOPPED_UNKNOWN}: the source may already have changed state with an
     *       unknown amount, so no later source may run and the remaining deficit must never be
     *       assumed to be consumed.
     * </ul>
     */
    public record ConsumptionOutcome(int consumed, Status status) {
        public ConsumptionOutcome {
            consumed = Math.max(0, consumed);
        }

        public static ConsumptionOutcome confirmed(int consumed) {
            return new ConsumptionOutcome(consumed, Status.CONFIRMED);
        }

        public static ConsumptionOutcome stoppedConfirmed(int consumed) {
            return new ConsumptionOutcome(consumed, Status.STOPPED_CONFIRMED);
        }

        public static ConsumptionOutcome stoppedUnknown() {
            return new ConsumptionOutcome(0, Status.STOPPED_UNKNOWN);
        }

        ConsumptionOutcome withConsumed(int consumed) {
            return new ConsumptionOutcome(consumed, this.status);
        }

        public boolean mayContinue() {
            return this.status == Status.CONFIRMED;
        }

        public boolean stoppedAbnormally() {
            return this.status != Status.CONFIRMED;
        }
    }

    public enum Status {
        CONFIRMED,
        STOPPED_CONFIRMED,
        STOPPED_UNKNOWN
    }

    /**
     * Runs the supplemental sources in the given priority order.
     *
     * @param requested total rounds wanted for this operation
     * @param consumedSoFar rounds the native TaCZ path already consumed
     * @param sources supplemental sources, highest priority first
     * @return the total confirmed consumption and whether it stopped early
     */
    public static ConsumptionOutcome consumeRemaining(
            int requested, int consumedSoFar, AmmoSource... sources) {
        return consumeRemaining(
                requested, consumedSoFar, sources == null ? List.of() : List.of(sources));
    }

    public static ConsumptionOutcome consumeRemaining(
            int requested, int consumedSoFar, List<AmmoSource> sources) {
        if (requested <= 0) {
            return ConsumptionOutcome.confirmed(0);
        }

        int current = clampConsumed(requested, consumedSoFar);
        boolean stopped = false;
        boolean unknown = false;

        for (AmmoSource source : sources) {
            int remaining = Math.max(0, requested - current);
            if (remaining <= 0) {
                break;
            }

            ConsumptionOutcome outcome;
            try {
                outcome = source.consume(remaining);
            } catch (IncompleteConsumptionException incomplete) {
                /*
                 * An opaque external mutation did not report its amount. Stop
                 * immediately: the previous confirmed total stays recorded and
                 * no other source is asked for the same deficit.
                 */
                return ConsumptionOutcome.stoppedUnknown().withConsumed(current);
            } catch (RuntimeException exception) {
                /*
                 * A source may change state before throwing. Record what was
                 * already confirmed for this request and stop.
                 */
                LOGGER.warn(
                        "[TACZ-addon/AmmoFallback] source failed after "
                                + "{} confirmed rounds; stopping this request",
                        current,
                        exception);
                return ConsumptionOutcome.stoppedUnknown().withConsumed(current);
            }

            int confirmed = clampConsumed(remaining, outcome.consumed());
            current = clampConsumed(requested, current + confirmed);

            if (outcome.stoppedAbnormally()) {
                stopped = true;
                unknown = outcome.status() == Status.STOPPED_UNKNOWN;
                break;
            }
        }

        logDetailedComposition(requested, consumedSoFar, current, stopped);

        if (!stopped) {
            return ConsumptionOutcome.confirmed(current);
        }
        return unknown
                ? ConsumptionOutcome.stoppedUnknown().withConsumed(current)
                : ConsumptionOutcome.stoppedConfirmed(current);
    }

    /** Signals an external mutation whose committed amount could not be reported. */
    public static final class IncompleteConsumptionException extends RuntimeException {
        public IncompleteConsumptionException(Throwable cause) {
            super("External ammo consumption did not complete", cause);
        }
    }

    public static int clampConsumed(int requested, int consumed) {
        if (requested <= 0) {
            return 0;
        }
        return Math.max(0, Math.min(requested, consumed));
    }

    /**
     * TaCZ's own virtual ammo semantics.
     *
     * <p>{@code useInventoryAmmo} combined with a shooter that does not need an ammo check means
     * the gun feeds itself and {@code useDummyAmmo} marks creative or otherwise virtual ammo. Both
     * keep TaCZ's native behavior: no supplemental source is consulted.
     */
    public static boolean usesNativeVirtualAmmo(
            boolean usesInventoryAmmo, boolean needCheckAmmo, boolean usesDummyAmmo) {
        return (usesInventoryAmmo && !needCheckAmmo) || usesDummyAmmo;
    }

    private static void logDetailedComposition(
            int requested, int consumedSoFar, int total, boolean stopped) {
        if (!LOGGER.isDebugEnabled()) {
            return;
        }
        LOGGER.debug(
                "[TACZ-addon/AmmoFallback] requested={}, native={}, " + "total={}, stopped={}",
                requested,
                clampConsumed(requested, consumedSoFar),
                total,
                stopped);
    }
}
