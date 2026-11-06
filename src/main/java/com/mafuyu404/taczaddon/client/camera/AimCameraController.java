package com.mafuyu404.taczaddon.client.camera;

import com.mafuyu404.taczaddon.compat.camera.PerspectiveApiCompat;
import com.mafuyu404.taczaddon.compat.camera.PerspectiveApiHandle;
import com.mafuyu404.taczaddon.compat.camera.ShoulderSurfing5Compat;

import com.mojang.logging.LogUtils;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.slf4j.Logger;

import javax.annotation.Nullable;

import static net.minecraft.client.CameraType.FIRST_PERSON;

@OnlyIn(Dist.CLIENT)
public final class AimCameraController {
    private static final Logger LOGGER = LogUtils.getLogger();

    private AimCameraController() {
    }

    public record AimCameraForceResult(
            boolean vanillaForced,
            boolean shoulderSurfingForced,
            @Nullable PerspectiveApiHandle perspectiveHandle
    ) {
    }

    public static AimCameraContext capture(Minecraft mc) {
        CameraType vanillaCameraType = mc.options.getCameraType();
        boolean shoulderSurfingActive =
                ShoulderSurfing5Compat.isShoulderSurfing();
        String perspectiveApiId =
                PerspectiveApiCompat.currentNonVanillaPerspectiveId();
        AimCameraContext context = new AimCameraContext(
                vanillaCameraType,
                shoulderSurfingActive,
                perspectiveApiId
        );
        LOGGER.debug(
                "[TACZ-addon] Captured camera state: vanilla={}, ssr={}, "
                        + "perspectiveId={}",
                context.vanillaCameraType(),
                context.shoulderSurfingActive(),
                context.perspectiveApiId()
        );
        return context;
    }

    @Nullable
    public static AimCameraForceResult forceFirstPerson(
            Minecraft mc,
            AimCameraContext context
    ) {
        if (context == null) {
            return null;
        }

        AimCameraForceResult result = CameraOwnershipTransaction.force(
                mc.options.getCameraType(),
                context,
                PerspectiveApiCompat::requestFirstPerson,
                ShoulderSurfing5Compat::forceFirstPerson,
                () -> ShoulderSurfing5Compat.enableShoulderSurfing(),
                mc.options::setCameraType
        );
        if (result == null) {
            LOGGER.debug(
                    "[TACZ-addon] Camera force transaction failed"
            );
        } else {
            LOGGER.debug(
                    "[TACZ-addon] Camera force acquired: vanilla={}, "
                            + "ssr={}, perspective={}",
                    result.vanillaForced(),
                    result.shoulderSurfingForced(),
                    result.perspectiveHandle() != null
            );
        }
        return result;
    }

    public static boolean isForcedFirstPersonActive(
            Minecraft mc,
            AimCameraForceResult forceResult
    ) {
        if (forceResult == null) {
            return false;
        }

        boolean vanillaCameraFirstPerson =
                mc.options.getCameraType() == FIRST_PERSON;

        return CameraOwnershipTransaction.isActive(
                forceResult,
                PerspectiveApiCompat.isFirstPersonActive(),
                ShoulderSurfing5Compat.isFirstPersonActive(
                        vanillaCameraFirstPerson
                ),
                vanillaCameraFirstPerson
        );
    }

    static boolean isForcedFirstPersonActive(
            AimCameraForceResult forceResult,
            boolean perspectiveApiFirstPerson,
            boolean shoulderSurfingFirstPerson,
            boolean vanillaCameraFirstPerson
    ) {
        return CameraOwnershipTransaction.isActive(
                forceResult,
                perspectiveApiFirstPerson,
                shoulderSurfingFirstPerson,
                vanillaCameraFirstPerson
        );
    }

    public static void restore(
            Minecraft mc,
            AimCameraContext context,
            AimCameraForceResult forceResult
    ) {
        if (forceResult == null) {
            return;
        }

        CameraOwnershipTransaction.restore(
                context,
                forceResult,
                () -> ShoulderSurfing5Compat.enableShoulderSurfing(),
                mc.options::setCameraType
        );
        LOGGER.debug(
                "[TACZ-addon] Camera restored: vanilla={}, ssr={}, "
                        + "perspective={}",
                forceResult.vanillaForced(),
                forceResult.shoulderSurfingForced(),
                forceResult.perspectiveHandle() != null
        );
    }

    public static void releaseAfterOwnershipLoss(
            Minecraft mc,
            AimCameraContext context,
            AimCameraForceResult forceResult
    ) {
        if (forceResult == null) {
            return;
        }

        boolean vanillaCameraFirstPerson =
                mc.options.getCameraType() == FIRST_PERSON;
        boolean ssrStillOwned =
                ShoulderSurfing5Compat.isFirstPersonActive(
                        vanillaCameraFirstPerson
                );
        LOGGER.debug(
                "[TACZ-addon] Ownership lost: perspectiveStillOwned={}, "
                        + "ssrStillOwned={}, vanillaStillOwned={}",
                forceResult.perspectiveHandle() != null,
                ssrStillOwned,
                forceResult.vanillaForced() && vanillaCameraFirstPerson
        );
        CameraOwnershipTransaction.releaseAfterOwnershipLoss(
                context,
                forceResult,
                mc.options.getCameraType(),
                ssrStillOwned,
                () -> ShoulderSurfing5Compat.enableShoulderSurfing(),
                mc.options::setCameraType
        );
    }
}
