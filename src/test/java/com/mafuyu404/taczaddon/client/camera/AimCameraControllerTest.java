package com.mafuyu404.taczaddon.client.camera;

import com.mafuyu404.taczaddon.compat.camera.PerspectiveApiHandle;

import net.minecraft.client.CameraType;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicReference;

import static net.minecraft.client.CameraType.*;
import static org.junit.jupiter.api.Assertions.*;

class AimCameraControllerTest {
    @Test
    void compositeContextCanRepresentSsrAndPerspectiveTogether() {
        AimCameraContext context = new AimCameraContext(
                THIRD_PERSON_BACK,
                true,
                "leawind_third_person.third_person"
        );

        assertTrue(context.shoulderSurfingActive());
        assertEquals(
                "leawind_third_person.third_person",
                context.perspectiveApiId()
        );
    }

    @Test
    void vanillaOnlyForceVerifyAndRestore() {
        AtomicReference<CameraType> camera =
                new AtomicReference<>(THIRD_PERSON_BACK);
        AimCameraContext context = new AimCameraContext(
                THIRD_PERSON_BACK,
                false,
                null
        );

        AimCameraController.AimCameraForceResult result =
                CameraOwnershipTransaction.force(
                        camera.get(),
                        context,
                        () -> {
                            throw new AssertionError(
                                    "perspective must not be requested"
                            );
                        },
                        () -> {
                            throw new AssertionError(
                                    "SSR must not be forced"
                            );
                        },
                        () -> fail("SSR restore must not run"),
                        camera::set
                );

        assertNotNull(result);
        assertTrue(result.vanillaForced());
        assertFalse(result.shoulderSurfingForced());
        assertNull(result.perspectiveHandle());
        assertSame(FIRST_PERSON, camera.get());
        assertTrue(AimCameraController.isForcedFirstPersonActive(
                result,
                false,
                false,
                true
        ));
        assertFalse(AimCameraController.isForcedFirstPersonActive(
                result,
                false,
                false,
                false
        ));

        CameraOwnershipTransaction.restore(
                context,
                result,
                () -> fail("SSR restore must not run"),
                camera::set
        );
        assertSame(THIRD_PERSON_BACK, camera.get());
    }

    @Test
    void ssrOnlyForceVerifyAndRestore() {
        AtomicReference<Boolean> ssrForced = new AtomicReference<>(false);
        AtomicReference<Boolean> ssrRestored = new AtomicReference<>(false);
        AimCameraContext context = new AimCameraContext(
                THIRD_PERSON_BACK,
                true,
                null
        );

        AimCameraController.AimCameraForceResult result =
                CameraOwnershipTransaction.force(
                        THIRD_PERSON_BACK,
                        context,
                        () -> {
                            throw new AssertionError(
                                    "perspective must not be requested"
                            );
                        },
                        () -> {
                            ssrForced.set(true);
                            return true;
                        },
                        () -> ssrRestored.set(true),
                        type -> fail("vanilla setter must not run")
                );

        assertNotNull(result);
        assertFalse(result.vanillaForced());
        assertTrue(result.shoulderSurfingForced());
        assertNull(result.perspectiveHandle());
        assertTrue(ssrForced.get());
        assertTrue(AimCameraController.isForcedFirstPersonActive(
                result,
                false,
                true,
                true
        ));
        assertFalse(AimCameraController.isForcedFirstPersonActive(
                result,
                false,
                false,
                true
        ));

        CameraOwnershipTransaction.restore(
                context,
                result,
                () -> ssrRestored.set(true),
                type -> fail("vanilla setter must not run")
        );
        assertTrue(ssrRestored.get());
    }

    @Test
    void perspectiveOnlyForceVerifyAndRestore() {
        OneShotHandle handle = new OneShotHandle();
        AimCameraContext context = new AimCameraContext(
                THIRD_PERSON_BACK,
                false,
                "leawind_third_person.third_person"
        );

        AimCameraController.AimCameraForceResult result =
                CameraOwnershipTransaction.force(
                        THIRD_PERSON_BACK,
                        context,
                        () -> handle,
                        () -> {
                            throw new AssertionError(
                                    "SSR must not be forced"
                            );
                        },
                        () -> fail("SSR restore must not run"),
                        type -> fail("vanilla setter must not run")
                );

        assertNotNull(result);
        assertFalse(result.vanillaForced());
        assertFalse(result.shoulderSurfingForced());
        assertSame(handle, result.perspectiveHandle());
        assertTrue(AimCameraController.isForcedFirstPersonActive(
                result,
                true,
                false,
                false
        ));
        assertFalse(AimCameraController.isForcedFirstPersonActive(
                result,
                false,
                false,
                true
        ));

        CameraOwnershipTransaction.restore(
                context,
                result,
                () -> fail("SSR restore must not run"),
                type -> fail("vanilla setter must not run")
        );
        assertEquals(1, handle.restores);
    }

    @Test
    void ssrAndPerspectiveForceVerifyAndRestoreTogether() {
        AtomicReference<Boolean> ssrForced = new AtomicReference<>(false);
        AtomicReference<Boolean> ssrRestored = new AtomicReference<>(false);
        OrderedHandle handle = new OrderedHandle(ssrRestored);
        AimCameraContext context = new AimCameraContext(
                THIRD_PERSON_BACK,
                true,
                "leawind_third_person.third_person"
        );

        AimCameraController.AimCameraForceResult result =
                CameraOwnershipTransaction.force(
                        THIRD_PERSON_BACK,
                        context,
                        () -> handle,
                        () -> {
                            ssrForced.set(true);
                            return true;
                        },
                        () -> ssrRestored.set(true),
                        type -> fail("vanilla setter must not run")
                );

        assertNotNull(result);
        assertFalse(result.vanillaForced());
        assertTrue(result.shoulderSurfingForced());
        assertSame(handle, result.perspectiveHandle());
        assertTrue(ssrForced.get());
        assertTrue(AimCameraController.isForcedFirstPersonActive(
                result,
                true,
                true,
                true
        ));
        assertFalse(AimCameraController.isForcedFirstPersonActive(
                result,
                false,
                true,
                true
        ));
        assertFalse(AimCameraController.isForcedFirstPersonActive(
                result,
                true,
                false,
                true
        ));

        CameraOwnershipTransaction.restore(
                context,
                result,
                () -> ssrRestored.set(true),
                type -> fail("vanilla setter must not run")
        );
        assertTrue(ssrRestored.get());
        assertTrue(handle.restoreSawSsrFirst);
        assertEquals(1, handle.restores);
    }

    @Test
    void perspectiveAcquisitionFailureReturnsNullWithoutFallback() {
        AimCameraContext context = new AimCameraContext(
                THIRD_PERSON_BACK,
                false,
                "leawind_third_person.third_person"
        );

        AimCameraController.AimCameraForceResult result =
                CameraOwnershipTransaction.force(
                        THIRD_PERSON_BACK,
                        context,
                        () -> null,
                        () -> {
                            throw new AssertionError(
                                    "SSR must not be forced"
                            );
                        },
                        () -> fail("SSR restore must not run"),
                        type -> fail("vanilla fallback must not run")
                );

        assertNull(result);
    }

    @Test
    void perspectiveSucceedsThenSsrFailureRollsBackPerspective() {
        CountingHandle handle = new CountingHandle();
        AimCameraContext context = new AimCameraContext(
                THIRD_PERSON_BACK,
                true,
                "leawind_third_person.third_person"
        );

        AimCameraController.AimCameraForceResult result =
                CameraOwnershipTransaction.force(
                        THIRD_PERSON_BACK,
                        context,
                        () -> handle,
                        () -> false,
                        () -> fail("SSR restore must not run"),
                        type -> fail("vanilla setter must not run")
                );

        assertNull(result);
        assertEquals(1, handle.restores);
    }

    @Test
    void ownershipLossReleasesPerspectiveHandle() {
        CountingHandle handle = new CountingHandle();
        AimCameraContext context = new AimCameraContext(
                THIRD_PERSON_BACK,
                true,
                "leawind_third_person.third_person"
        );
        AimCameraController.AimCameraForceResult result =
                new AimCameraController.AimCameraForceResult(
                        false,
                        true,
                        handle
                );

        CameraOwnershipTransaction.releaseAfterOwnershipLoss(
                context,
                result,
                FIRST_PERSON,
                true,
                () -> {
                },
                type -> fail("vanilla setter must not run")
        );

        assertEquals(1, handle.restores);
    }

    @Test
    void ownershipLossPreservesExternallyChangedVanillaCamera() {
        AtomicReference<CameraType> camera =
                new AtomicReference<>(THIRD_PERSON_FRONT);
        AimCameraContext context = new AimCameraContext(
                THIRD_PERSON_BACK,
                false,
                null
        );
        AimCameraController.AimCameraForceResult result =
                new AimCameraController.AimCameraForceResult(
                        true,
                        false,
                        null
                );

        CameraOwnershipTransaction.releaseAfterOwnershipLoss(
                context,
                result,
                camera.get(),
                false,
                () -> fail("SSR restore must not run"),
                camera::set
        );

        assertSame(THIRD_PERSON_FRONT, camera.get());
    }

    @Test
    void ownershipLossRestoresVanillaOnlyWhenOurStateIsStillIntact() {
        AtomicReference<CameraType> camera =
                new AtomicReference<>(FIRST_PERSON);
        AimCameraContext context = new AimCameraContext(
                THIRD_PERSON_BACK,
                false,
                null
        );
        AimCameraController.AimCameraForceResult result =
                new AimCameraController.AimCameraForceResult(
                        true,
                        false,
                        null
                );

        CameraOwnershipTransaction.releaseAfterOwnershipLoss(
                context,
                result,
                camera.get(),
                false,
                () -> fail("SSR restore must not run"),
                camera::set
        );

        assertSame(THIRD_PERSON_BACK, camera.get());
    }

    @Test
    void ownershipLossRestoresSsrOnlyWhileStillOwned() {
        AtomicReference<Boolean> ssrRestored = new AtomicReference<>(false);
        AimCameraContext context = new AimCameraContext(
                THIRD_PERSON_BACK,
                true,
                null
        );
        AimCameraController.AimCameraForceResult result =
                new AimCameraController.AimCameraForceResult(
                        false,
                        true,
                        null
                );

        CameraOwnershipTransaction.releaseAfterOwnershipLoss(
                context,
                result,
                FIRST_PERSON,
                true,
                () -> ssrRestored.set(true),
                type -> fail("vanilla setter must not run")
        );
        assertTrue(ssrRestored.get());

        ssrRestored.set(false);
        CameraOwnershipTransaction.releaseAfterOwnershipLoss(
                context,
                result,
                THIRD_PERSON_FRONT,
                false,
                () -> ssrRestored.set(true),
                type -> fail("vanilla setter must not run")
        );
        assertFalse(ssrRestored.get());
    }

    @Test
    void normalRestoreIsIdempotent() {
        OneShotHandle handle = new OneShotHandle();
        AimCameraContext context = new AimCameraContext(
                THIRD_PERSON_BACK,
                false,
                "leawind_third_person.third_person"
        );
        AimCameraController.AimCameraForceResult result =
                new AimCameraController.AimCameraForceResult(
                        false,
                        false,
                        handle
                );

        CameraOwnershipTransaction.restore(
                context,
                result,
                () -> fail("SSR restore must not run"),
                type -> fail("vanilla setter must not run")
        );
        CameraOwnershipTransaction.restore(
                context,
                result,
                () -> fail("SSR restore must not run"),
                type -> fail("vanilla setter must not run")
        );

        assertEquals(1, handle.restores);
    }

    private static class CountingHandle
            implements PerspectiveApiHandle {
        int restores;

        @Override
        public void restore() {
            this.restores++;
        }
    }

    private static class OneShotHandle
            implements PerspectiveApiHandle {
        int restores;
        private boolean restored;

        @Override
        public void restore() {
            if (!this.restored) {
                this.restored = true;
                this.restores++;
            }
        }
    }

    private static final class OrderedHandle
            implements PerspectiveApiHandle {
        private final AtomicReference<Boolean> ssrRestored;
        private boolean restoreSawSsrFirst;
        private int restores;
        private boolean restored;

        private OrderedHandle(
                AtomicReference<Boolean> ssrRestored
        ) {
            this.ssrRestored = ssrRestored;
        }

        @Override
        public void restore() {
            if (!this.restored) {
                this.restored = true;
                this.restores++;
                this.restoreSawSsrFirst = Boolean.TRUE.equals(
                        this.ssrRestored.get()
                );
            }
        }
    }
}
