package com.mafuyu404.taczaddon.camera.client;

import static com.tacz.guns.util.InputExtraCheck.isInGame;

import com.mafuyu404.taczaddon.TACZaddon;
import com.mafuyu404.taczaddon.config.ClientConfig;
import com.tacz.guns.api.client.gameplay.IClientPlayerGunOperator;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.client.input.AimKey;
import com.tacz.guns.config.client.KeyConfig;

import net.minecraft.Util;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;

import org.lwjgl.glfw.GLFW;

/** Observes TaCZ ADS; never changes the gun operator or a third-party camera. */
@EventBusSubscriber(modid = TACZaddon.MODID, value = Dist.CLIENT)
public final class AimCameraController {
    private static final AimCameraState STATE = new AimCameraState();
    private static final VanillaAimCamera VANILLA = new VanillaAimCamera();
    private static LocalPlayer trackedPlayer;
    private static ClientLevel trackedLevel;
    private static ItemStack trackedGun;
    private static KeyMapping activeAimKey;

    private AimCameraController() {}

    public static boolean isAimActive() {
        // Hooks may run before our tick, or during a paused/menu frame.
        return STATE.isAimActive() && isTrackedAimValid(Minecraft.getInstance());
    }

    public static boolean isTemporaryFirstPersonRequested() {
        return STATE.isTemporaryFirstPersonRequested()
                && ClientConfig.enableBetterAimCamera()
                && isAimActive();
    }

    public static void handleAfterAimPress(InputEvent.MouseButton.Post event, KeyMapping aimKey) {
        if (!aimKey.matchesMouse(event.getButton())) return;
        boolean holdToAim = KeyConfig.HOLD_TO_AIM.get();
        if (event.getAction() != GLFW.GLFW_PRESS
                && !(holdToAim && event.getAction() == GLFW.GLFW_RELEASE)) return;
        activeAimKey = aimKey;
        // RETURN of TaCZ's handler: operator.isAim() is the truth.
        update();
    }

    public static void handleAfterAimInput() {
        activeAimKey = AimKey.AIM_KEY;
        update();
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onClientTick(ClientTickEvent.Post event) {
        update();
    }

    @SubscribeEvent
    public static void onRenderFrame(RenderFrameEvent.Pre event) {
        // Clear paused/menu state; avoid quantizing 110 ms to a 50 ms tick.
        update();
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        reset();
    }

    public static void reset() {
        STATE.reset();
        activeAimKey = null;
        trackedPlayer = null;
        trackedLevel = null;
        trackedGun = null;
        VANILLA.end(Minecraft.getInstance());
    }

    private static void update() {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player != trackedPlayer
                || minecraft.level != trackedLevel
                || (player != null && player.getMainHandItem() != trackedGun)) {
            reset();
            trackedPlayer = player;
            trackedLevel = minecraft.level;
            trackedGun = player == null ? null : player.getMainHandItem();
        }
        if (!isPlayerValid(minecraft)) {
            reset();
            return;
        }
        if (activeAimKey == null) activeAimKey = AimKey.AIM_KEY;
        IClientPlayerGunOperator operator = (IClientPlayerGunOperator) player;
        STATE.update(
                Util.getMillis(),
                true,
                operator.isAim(),
                KeyConfig.HOLD_TO_AIM.get(),
                activeAimKey.isDown(),
                ClientConfig.enableBetterAimCamera());
        VANILLA.update(
                minecraft,
                STATE.isAimActive() && ClientConfig.enableBetterAimCamera(),
                STATE.isTemporaryFirstPersonRequested());
        if (!STATE.isAimActive()) activeAimKey = null;
    }

    private static boolean isPlayerValid(Minecraft minecraft) {
        LocalPlayer player = minecraft.player;
        return minecraft.level != null
                && player != null
                && player.isAlive()
                && !player.isSpectator()
                && !minecraft.isPaused()
                && isInGame()
                && player instanceof IClientPlayerGunOperator
                && IGun.mainHandHoldGun(player);
    }

    private static boolean isTrackedAimValid(Minecraft minecraft) {
        return minecraft.player == trackedPlayer
                && minecraft.level == trackedLevel
                && isPlayerValid(minecraft)
                && minecraft.player.getMainHandItem() == trackedGun
                && ((IClientPlayerGunOperator) minecraft.player).isAim()
                && (!KeyConfig.HOLD_TO_AIM.get()
                        || (activeAimKey != null && activeAimKey.isDown()));
    }
}
