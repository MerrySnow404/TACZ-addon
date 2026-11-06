package com.mafuyu404.taczaddon.camera.client;

/** Only ADS validity and timing; no camera ownership or Minecraft dependencies. */
final class AimCameraState {
    private static final long DELAY_MS = 110L;
    private boolean aimActive;
    private boolean temporaryFirstPersonRequested;
    private boolean delayPending;
    private long startedAtMs;

    void update(
            long nowMs,
            boolean valid,
            boolean operatorAiming,
            boolean holdToAim,
            boolean keyDown,
            boolean enabled) {
        aimActive = valid && operatorAiming && (!holdToAim || keyDown);
        if (!aimActive || !enabled) {
            temporaryFirstPersonRequested = false;
            delayPending = false;
            startedAtMs = 0L;
            return;
        }
        if (!delayPending && !temporaryFirstPersonRequested) {
            delayPending = true;
            startedAtMs = nowMs;
        }
        if (delayPending && nowMs - startedAtMs >= DELAY_MS) {
            temporaryFirstPersonRequested = true;
            delayPending = false;
        }
    }

    boolean isAimActive() {
        return aimActive;
    }

    boolean isTemporaryFirstPersonRequested() {
        return temporaryFirstPersonRequested;
    }

    void reset() {
        aimActive = false;
        temporaryFirstPersonRequested = false;
        delayPending = false;
        startedAtMs = 0L;
    }
}
