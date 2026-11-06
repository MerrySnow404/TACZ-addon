package com.mafuyu404.taczaddon.compat.sophisticated;

/**
 * Process-lifetime state of the optional Sophisticated payload Mixin hook. Native CLIENT_SYNC
 * protocol readiness is independent of this hook.
 *
 * <p>The state transition is deliberately fail-closed:
 *
 * <pre>
 * UNKNOWN
 *   |
 *   +-- preflight(false) --> INELIGIBLE
 *   |
 *   +-- preflight(true)  --> ELIGIBLE
 *                              |
 *                              +-- applied(true)  --> INSTALLED
 *                              |
 *                              +-- applied(false) --> APPLY_FAILED
 * </pre>
 *
 * <p>Only INSTALLED is usable. Passing the raw dependency contract check is therefore not enough to
 * enable the optional callback: postApply must verify that the response hook was actually injected
 * into the transformed target.
 */
public final class SophisticatedPayloadContractState {

    public enum State {
        UNKNOWN,

        /**
         * Raw dependency bytecode does not satisfy the contract required by
         * BackpackContentsPayloadMixin.
         */
        INELIGIBLE,

        /** Raw dependency contract passed, but Mixin application has not yet been verified. */
        ELIGIBLE,

        /**
         * postApply verified that the transformed target contains the TACZAddon payload callback.
         */
        INSTALLED,

        /** Preflight succeeded but postApply could not verify the injected callback. */
        APPLY_FAILED
    }

    private static volatile State state = State.UNKNOWN;

    private SophisticatedPayloadContractState() {}

    /**
     * Records the raw-bytecode preflight result.
     *
     * <p>Final states are sticky for the process lifetime. Repeated Mixin queries must not
     * downgrade or otherwise rewrite a completed verdict.
     */
    public static synchronized void reportPreflight(boolean eligible) {
        if (isFinalState(state)) {
            return;
        }

        if (!eligible) {
            state = State.INELIGIBLE;
            return;
        }

        if (state == State.UNKNOWN) {
            state = State.ELIGIBLE;
        }
    }

    /**
     * Records the post-transform result.
     *
     * <p>A positive postApply report is accepted only after a positive preflight. Calling this
     * unexpectedly from UNKNOWN must fail closed rather than declaring the optional callback
     * installed.
     */
    public static synchronized void reportApplied(boolean installed) {
        if (isFinalState(state)) {
            return;
        }

        if (state != State.ELIGIBLE) {
            state = State.APPLY_FAILED;
            return;
        }

        state = installed ? State.INSTALLED : State.APPLY_FAILED;
    }

    /** The optional acceleration/invalidation callback is usable only after verified injection. */
    public static boolean isUsable() {
        return state == State.INSTALLED;
    }

    public static State state() {
        return state;
    }

    private static boolean isFinalState(State value) {
        return value == State.INELIGIBLE || value == State.INSTALLED || value == State.APPLY_FAILED;
    }

    /**
     * Test-only reset. Runtime world changes/reconnects must not reset a process-lifetime Mixin
     * verdict.
     */
    static synchronized void reset() {
        state = State.UNKNOWN;
    }
}
