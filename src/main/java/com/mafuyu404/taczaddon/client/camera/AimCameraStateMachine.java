package com.mafuyu404.taczaddon.client.camera;

/**
 * Pure camera transition policy for BetterAimCamera.
 *
 * TaCZ owns aim state; this class only tracks whether the addon has
 * captured a camera context, is waiting to force first person, or currently
 * owns the first-person camera.
 */
public final class AimCameraStateMachine {
    public enum State {
        IDLE,
        WAITING_FOR_FIRST_PERSON,
        FORCED_FIRST_PERSON
    }

    public enum Action {
        NONE,
        BEGIN_CAPTURE,
        CANCEL,
        FORCE_FIRST_PERSON,
        RESTORE,
        LOST_OWNERSHIP
    }

    private State state = State.IDLE;
    private boolean suppressCaptureUntilAimEnds;

    public State state() {
        return this.state;
    }

    public boolean isWaiting() {
        return this.state == State.WAITING_FOR_FIRST_PERSON;
    }

    public boolean isForced() {
        return this.state == State.FORCED_FIRST_PERSON;
    }

    public Action observeAiming(
            boolean aiming,
            boolean canCapture
    ) {
        if (aiming) {
            if (this.state == State.IDLE
                    && canCapture
                    && !this.suppressCaptureUntilAimEnds) {
                this.state = State.WAITING_FOR_FIRST_PERSON;
                return Action.BEGIN_CAPTURE;
            }
            return Action.NONE;
        }

        Action action = Action.NONE;
        switch (this.state) {
            case IDLE -> action = Action.NONE;
            case WAITING_FOR_FIRST_PERSON -> action = Action.CANCEL;
            case FORCED_FIRST_PERSON -> action = Action.RESTORE;
        }
        this.state = State.IDLE;
        this.suppressCaptureUntilAimEnds = false;
        return action;
    }

    public Action confirmForcedFirstPerson() {
        if (this.state == State.WAITING_FOR_FIRST_PERSON) {
            this.state = State.FORCED_FIRST_PERSON;
            return Action.FORCE_FIRST_PERSON;
        }
        return Action.NONE;
    }

    public Action markOwnershipLost() {
        if (this.state == State.IDLE) {
            return Action.NONE;
        }

        this.suppressCaptureUntilAimEnds = true;
        Action action = this.state == State.FORCED_FIRST_PERSON
                ? Action.LOST_OWNERSHIP
                : Action.CANCEL;
        this.state = State.IDLE;
        return action;
    }

    public Action invalidate() {
        if (this.state == State.IDLE) {
            return Action.NONE;
        }

        Action action = this.state == State.FORCED_FIRST_PERSON
                ? Action.RESTORE
                : Action.CANCEL;
        this.state = State.IDLE;
        this.suppressCaptureUntilAimEnds = false;
        return action;
    }
}
