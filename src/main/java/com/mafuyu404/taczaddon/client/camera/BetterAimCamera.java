package com.mafuyu404.taczaddon.client.camera;

import com.mafuyu404.taczaddon.ModConstants;
import com.mafuyu404.taczaddon.compat.camera.PerspectiveApiCompat;
import com.mafuyu404.taczaddon.compat.camera.ShoulderSurfing5Compat;
import com.mafuyu404.taczaddon.config.Config;

import com.tacz.guns.api.client.gameplay.IClientPlayerGunOperator;
import com.tacz.guns.api.item.IGun;
import net.minecraft.Util;
import net.minecraft.client.CameraType;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import static com.tacz.guns.util.InputExtraCheck.isInGame;
import static net.minecraft.client.CameraType.FIRST_PERSON;

@Mod.EventBusSubscriber(modid = ModConstants.MODID, value = Dist.CLIENT)
public class BetterAimCamera {
    private static final long AIM_CAMERA_SWITCH_DELAY_MS = 110L;

    private static final AimCameraStateMachine STATE =
            new AimCameraStateMachine();

    private static AimCameraContext cameraContext;
    private static AimCameraController.AimCameraForceResult
            cameraForceResult;
    private static long pendingSwitchTimeMs;

    public static void handle(
            InputEvent.MouseButton.Post event,
            KeyMapping aimKey
    ) {
        if (!Config.enableBetterAimCamera()) {
            return;
        }
        if (!isInGame()) {
            return;
        }
        if (!aimKey.matchesMouse(event.getButton())) {
            return;
        }
        updateCameraState();
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        updateCameraState();
    }

    /** Called after TaCZ 1.2.0 has handled mouse, keyboard or controller aim input. */
    public static void afterAimInput() {
        updateCameraState();
    }

    private static void updateCameraState() {
        Minecraft mc = Minecraft.getInstance();

        if (!Config.enableBetterAimCamera()
                || !isInGame()
                || mc.screen != null) {
            invalidate(mc);
            return;
        }

        LocalPlayer player = mc.player;
        if (player == null
                || player.isSpectator()
                || !IGun.mainHandHoldGun(player)
                || !(player instanceof IClientPlayerGunOperator operator)) {
            invalidate(mc);
            return;
        }

        if (STATE.isForced()
                && !AimCameraController.isForcedFirstPersonActive(
                mc,
                cameraForceResult
        )) {
            handleAction(STATE.markOwnershipLost(), mc);
            return;
        }

        boolean aiming = operator.isAim();
        boolean canCapture = aiming && shouldCaptureCamera(mc);

        handleAction(
                STATE.observeAiming(aiming, canCapture),
                mc
        );

        if (STATE.isWaiting()
                && aiming
                && Util.getMillis() >= pendingSwitchTimeMs) {
            AimCameraController.AimCameraForceResult forceResult =
                    AimCameraController.forceFirstPerson(
                            mc,
                            cameraContext
                    );

            if (forceResult == null) {
                cameraForceResult = null;
                handleAction(STATE.markOwnershipLost(), mc);
                return;
            }

            cameraForceResult = forceResult;
            handleAction(
                    STATE.confirmForcedFirstPerson(),
                    mc
            );
        }
    }

    private static boolean shouldCaptureCamera(Minecraft mc) {
        CameraType currentCamera = mc.options.getCameraType();

        if (currentCamera != FIRST_PERSON) {
            return true;
        }

        return ShoulderSurfing5Compat.isShoulderSurfing()
                || PerspectiveApiCompat
                .currentNonVanillaPerspectiveId() != null;
    }

    private static void invalidate(Minecraft mc) {
        handleAction(STATE.invalidate(), mc);
    }

    private static void handleAction(
            AimCameraStateMachine.Action action,
            Minecraft mc
    ) {
        switch (action) {
            case BEGIN_CAPTURE -> {
                cameraContext = AimCameraController.capture(mc);
                cameraForceResult = null;
                pendingSwitchTimeMs =
                        Util.getMillis() + AIM_CAMERA_SWITCH_DELAY_MS;
            }
            case CANCEL -> clearPending();
            case RESTORE -> {
                if (AimCameraController.isForcedFirstPersonActive(
                        mc,
                        cameraForceResult
                )) {
                    AimCameraController.restore(
                            mc,
                            cameraContext,
                            cameraForceResult
                    );
                } else {
                    AimCameraController.releaseAfterOwnershipLoss(
                            mc,
                            cameraContext,
                            cameraForceResult
                    );
                }
                clearAll();
            }
            case LOST_OWNERSHIP -> {
                AimCameraController.releaseAfterOwnershipLoss(
                        mc,
                        cameraContext,
                        cameraForceResult
                );
                clearAll();
            }
            case FORCE_FIRST_PERSON, NONE -> {
            }
        }
    }

    private static void clearPending() {
        cameraContext = null;
        cameraForceResult = null;
        pendingSwitchTimeMs = 0L;
    }

    private static void clearAll() {
        clearPending();
    }
}
