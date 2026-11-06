package com.mafuyu404.taczaddon.client.camera;

import com.mafuyu404.taczaddon.compat.camera.PerspectiveApiHandle;

import net.minecraft.client.CameraType;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import javax.annotation.Nullable;

import static net.minecraft.client.CameraType.FIRST_PERSON;

/**
 * Pure ownership policy for the composite camera force transaction.
 */
@OnlyIn(Dist.CLIENT)
final class CameraOwnershipTransaction {
    @FunctionalInterface
    interface PerspectiveAcquirer {
        @Nullable PerspectiveApiHandle request();
    }

    @FunctionalInterface
    interface SsrFirstPersonForcer {
        boolean force();
    }

    @FunctionalInterface
    interface SsrRestorer {
        void restore();
    }

    @FunctionalInterface
    interface VanillaCameraSetter {
        void set(CameraType cameraType);
    }

    private CameraOwnershipTransaction() {
    }

    @Nullable
    static AimCameraController.AimCameraForceResult force(
            CameraType currentVanillaCameraType,
            AimCameraContext context,
            PerspectiveAcquirer perspectiveAcquirer,
            SsrFirstPersonForcer ssrForcer,
            SsrRestorer ssrRestorer,
            VanillaCameraSetter vanillaCameraSetter
    ) {
        if (context == null) {
            return null;
        }

        PerspectiveApiHandle handle = null;
        boolean ssrAttempted = false;
        boolean ssrForced = false;
        boolean vanillaForced = false;

        try {
            if (context.perspectiveApiId() != null) {
                handle = perspectiveAcquirer.request();
                if (handle == null) {
                    return null;
                }
            }

            if (context.shoulderSurfingActive()) {
                ssrAttempted = true;
                if (!ssrForcer.force()) {
                    releaseSafely(handle);
                    return null;
                }
                ssrForced = true;
            }

            if (context.perspectiveApiId() == null
                    && !context.shoulderSurfingActive()) {
                if (currentVanillaCameraType != FIRST_PERSON) {
                    vanillaCameraSetter.set(FIRST_PERSON);
                    vanillaForced = true;
                }
            }

            return new AimCameraController.AimCameraForceResult(
                    vanillaForced,
                    ssrForced,
                    handle
            );
        } catch (RuntimeException | LinkageError throwable) {
            if (ssrAttempted && context.shoulderSurfingActive()) {
                try {
                    ssrRestorer.restore();
                } catch (LinkageError ignored) {
                }
            }
            releaseSafely(handle);
            return null;
        }
    }

    static boolean isActive(
            AimCameraController.AimCameraForceResult result,
            boolean perspectiveApiFirstPerson,
            boolean shoulderSurfingFirstPerson,
            boolean vanillaCameraFirstPerson
    ) {
        if (result == null) {
            return false;
        }
        if (result.perspectiveHandle() != null
                && !perspectiveApiFirstPerson) {
            return false;
        }
        if (result.shoulderSurfingForced()
                && !shoulderSurfingFirstPerson) {
            return false;
        }
        return !result.vanillaForced() || vanillaCameraFirstPerson;
    }

    static void restore(
            AimCameraContext context,
            AimCameraController.AimCameraForceResult result,
            SsrRestorer ssrRestorer,
            VanillaCameraSetter vanillaCameraSetter
    ) {
        if (result == null) {
            return;
        }

        try {
            if (result.shoulderSurfingForced()) {
                ssrRestorer.restore();
            }
            if (result.vanillaForced() && context != null) {
                vanillaCameraSetter.set(context.vanillaCameraType());
            }
        } finally {
            releaseSafely(result.perspectiveHandle());
        }
    }

    static void releaseAfterOwnershipLoss(
            AimCameraContext context,
            AimCameraController.AimCameraForceResult result,
            CameraType currentVanillaCameraType,
            boolean shoulderSurfingFirstPerson,
            SsrRestorer ssrRestorer,
            VanillaCameraSetter vanillaCameraSetter
    ) {
        if (result == null) {
            return;
        }

        releaseSafely(result.perspectiveHandle());

        if (result.shoulderSurfingForced()
                && shoulderSurfingFirstPerson) {
            try {
                ssrRestorer.restore();
            } catch (LinkageError ignored) {
            }
        }
        if (result.vanillaForced()
                && currentVanillaCameraType == FIRST_PERSON
                && context != null) {
            vanillaCameraSetter.set(context.vanillaCameraType());
        }
    }

    private static void releaseSafely(
            @Nullable PerspectiveApiHandle handle
    ) {
        if (handle == null) {
            return;
        }
        try {
            handle.restore();
        } catch (LinkageError ignored) {
        }
    }
}
