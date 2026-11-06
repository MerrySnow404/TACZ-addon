package com.mafuyu404.taczaddon.client.camera;

import com.mafuyu404.taczaddon.client.camera.AimCameraStateMachine.Action;
import com.mafuyu404.taczaddon.client.camera.AimCameraStateMachine.State;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AimCameraStateMachineTest {
    @Test
    void toggleAimEntersAndRestoresExactlyOnce() {
        AimCameraStateMachine machine = new AimCameraStateMachine();

        assertEquals(
                Action.BEGIN_CAPTURE,
                machine.observeAiming(true, true)
        );
        assertEquals(
                Action.FORCE_FIRST_PERSON,
                machine.confirmForcedFirstPerson()
        );
        assertEquals(
                Action.RESTORE,
                machine.observeAiming(false, true)
        );
        assertEquals(State.IDLE, machine.state());
    }

    @Test
    void toggleReleaseWhenStillAimingDoesNotRestore() {
        AimCameraStateMachine machine = new AimCameraStateMachine();

        machine.observeAiming(true, true);
        machine.confirmForcedFirstPerson();

        assertEquals(
                Action.NONE,
                machine.observeAiming(true, true)
        );
        assertEquals(
                Action.NONE,
                machine.observeAiming(true, true)
        );
        assertEquals(
                Action.RESTORE,
                machine.observeAiming(false, true)
        );
        assertEquals(State.IDLE, machine.state());
    }

    @Test
    void holdToAimCaptureOnceAndRestoreOnce() {
        AimCameraStateMachine machine = new AimCameraStateMachine();

        assertEquals(
                Action.BEGIN_CAPTURE,
                machine.observeAiming(true, true)
        );
        assertEquals(
                Action.NONE,
                machine.observeAiming(true, true)
        );
        machine.confirmForcedFirstPerson();
        assertEquals(
                Action.RESTORE,
                machine.observeAiming(false, true)
        );
        assertEquals(
                Action.NONE,
                machine.observeAiming(false, true)
        );
    }

    @Test
    void pendingTransitionCanceledBeforeDelayNeverForces() {
        AimCameraStateMachine machine = new AimCameraStateMachine();

        machine.observeAiming(true, true);
        assertEquals(
                Action.CANCEL,
                machine.observeAiming(false, true)
        );
        assertEquals(
                Action.NONE,
                machine.confirmForcedFirstPerson()
        );
        assertEquals(State.IDLE, machine.state());
    }

    @Test
    void failedForceTransitionNeverConfirmsForcedFirstPerson() {
        AimCameraStateMachine machine = new AimCameraStateMachine();

        machine.observeAiming(true, true);
        assertEquals(
                Action.CANCEL,
                machine.markOwnershipLost()
        );
        assertEquals(State.IDLE, machine.state());
        assertEquals(
                Action.NONE,
                machine.confirmForcedFirstPerson()
        );
        assertEquals(State.IDLE, machine.state());
    }

    @Test
    void repeatedTrueObservationsDoNotRepeatCapture() {
        AimCameraStateMachine machine = new AimCameraStateMachine();

        machine.observeAiming(true, true);
        machine.confirmForcedFirstPerson();

        assertEquals(
                Action.NONE,
                machine.observeAiming(true, true)
        );
        assertEquals(State.FORCED_FIRST_PERSON, machine.state());
    }

    @Test
    void userManualPerspectiveChangeDoesNotRestoreStaleState() {
        AimCameraStateMachine machine = new AimCameraStateMachine();

        machine.observeAiming(true, true);
        machine.confirmForcedFirstPerson();
        assertEquals(
                Action.LOST_OWNERSHIP,
                machine.markOwnershipLost()
        );
        assertEquals(
                Action.NONE,
                machine.observeAiming(true, true)
        );
        assertEquals(
                Action.NONE,
                machine.observeAiming(false, true)
        );
        machine.observeAiming(false, true);
        assertEquals(
                Action.BEGIN_CAPTURE,
                machine.observeAiming(true, true)
        );
    }

    @Test
    void invalidationCancelsPendingButRestoresOwnedCamera() {
        AimCameraStateMachine machine = new AimCameraStateMachine();

        machine.observeAiming(true, true);
        assertEquals(Action.CANCEL, machine.invalidate());

        machine.observeAiming(true, true);
        machine.confirmForcedFirstPerson();
        assertEquals(Action.RESTORE, machine.invalidate());
        assertEquals(State.IDLE, machine.state());
    }

    @Test
    void firstPersonAlreadyActiveDoesNotStartPendingCapture() {
        AimCameraStateMachine machine = new AimCameraStateMachine();

        assertEquals(
                Action.NONE,
                machine.observeAiming(true, false)
        );
        assertEquals(State.IDLE, machine.state());
    }
}
