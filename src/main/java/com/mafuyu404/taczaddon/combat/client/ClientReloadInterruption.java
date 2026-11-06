package com.mafuyu404.taczaddon.combat.client;

import com.mafuyu404.taczaddon.combat.ShootWhileReloadService;
import com.mafuyu404.taczaddon.config.ClientSyncedConfig;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.client.animation.AnimationController;
import com.tacz.guns.api.client.animation.ObjectAnimationRunner;
import com.tacz.guns.api.client.animation.statemachine.AnimationStateContext;
import com.tacz.guns.api.client.animation.statemachine.AnimationStateMachine;
import com.tacz.guns.api.entity.IGunOperator;
import com.tacz.guns.api.entity.ReloadState;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.client.animation.statemachine.GunAnimationConstant;
import com.tacz.guns.client.resource.index.ClientGunIndex;
import com.tacz.guns.resource.pojo.data.gun.GunData;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.Locale;

/**
 * Client-side eligibility and visual transition for immediate shooting during an active reload.
 *
 * <p>This class does not cancel the authoritative reload, send a reload-cancel packet, consume
 * ammunition, or create a replacement shot. The server-side LivingEntityShoot Mixin owns the
 * authoritative reload interruption.
 */
@OnlyIn(Dist.CLIENT)
public final class ClientReloadInterruption {
    private ClientReloadInterruption() {}

    /**
     * Determines whether the current native LocalPlayerShoot#shoot invocation may bypass only
     * TaCZ's client-side reload gates.
     *
     * <p>The gun must already contain ammunition that can be fired. Reserve inventory ammunition is
     * not considered.
     */
    public static boolean canInterruptForImmediateShot(LocalPlayer player) {
        if (!ClientSyncedConfig.enableShootWhileReloading()) {
            return false;
        }

        if (player == null || !player.isAlive() || player.isSpectator()) {
            return false;
        }

        IGunOperator operator = IGunOperator.fromLivingEntity(player);

        if (operator == null) {
            return false;
        }

        ReloadState reloadState = operator.getSynReloadState();

        if (reloadState == null || !reloadState.getStateType().isReloading()) {
            return false;
        }

        ItemStack gunStack = player.getMainHandItem();

        IGun gun = IGun.getIGunOrNull(gunStack);

        if (gun == null || gun.useInventoryAmmo(gunStack)) {
            return false;
        }

        ResourceLocation gunId = gun.getGunId(gunStack);

        ClientGunIndex gunIndex = TimelessAPI.getClientGunIndex(gunId).orElse(null);

        if (gunIndex == null) {
            return false;
        }

        GunData gunData = gunIndex.getGunData();

        return ShootWhileReloadService.hasLoadedAmmo(
                gun.getCurrentAmmoCount(gunStack),
                gun.hasBulletInBarrel(gunStack),
                gunData.getBolt());
    }

    /**
     * Plays only the local reload-interrupt animation.
     *
     * <p>No ClientMessagePlayerCancelReload is sent. The server atomically terminates reload only
     * when the native shooting request passes every authoritative validation.
     */
    public static boolean playLocalReloadInterruptAnimation(LocalPlayer player) {
        if (player == null) {
            return false;
        }

        IGunOperator operator = IGunOperator.fromLivingEntity(player);

        if (operator == null) {
            return false;
        }

        ReloadState reloadState = operator.getSynReloadState();

        if (reloadState == null || !reloadState.getStateType().isReloading()) {
            return false;
        }

        ItemStack gunStack = player.getMainHandItem();

        return TimelessAPI.getGunDisplay(gunStack)
                .map(
                        display -> {
                            AnimationStateMachine<?> stateMachine =
                                    display.getAnimationStateMachine();

                            if (stateMachine == null || !stateMachine.isInitialized()) {
                                return false;
                            }

                            /*
                             * First allow custom gun state machines to leave their
                             * dedicated reload state and perform script-side cleanup.
                             */
                            stateMachine.trigger(GunAnimationConstant.INPUT_CANCEL_RELOAD);

                            /*
                             * Default magazine-fed guns remain logically in idle while
                             * reload_tactical/reload_empty runs on the main track.
                             * INPUT_CANCEL_RELOAD therefore does not stop that animation.
                             *
                             * Remove only reload-related runners. Native TaCZ shooting
                             * will trigger INPUT_SHOOT afterward on its normal track.
                             */
                            stopActiveReloadAnimations(stateMachine);

                            return true;
                        })
                .orElse(false);
    }

    private static void stopActiveReloadAnimations(AnimationStateMachine<?> stateMachine) {
        AnimationStateContext context = stateMachine.getContext();

        if (context == null) {
            return;
        }

        AnimationController controller = stateMachine.getAnimationController();

        for (int track : context.getTrackArray()) {
            ObjectAnimationRunner runner = controller.getAnimation(track);

            if (isReloadAnimationRunner(runner)) {
                /*
                 * removeAnimation also clears a queued animation for the track.
                 * This is required when cancel_reload creates reload_end or when
                 * the current runner is transitioning into another reload clip.
                 */
                controller.removeAnimation(track);
            }
        }
    }

    private static boolean isReloadAnimationRunner(ObjectAnimationRunner runner) {
        if (runner == null) {
            return false;
        }

        if (isReloadAnimationName(runner.getAnimation().name)) {
            return true;
        }

        ObjectAnimationRunner transitionTarget = runner.getTransitionTo();

        return transitionTarget != null
                && isReloadAnimationName(transitionTarget.getAnimation().name);
    }

    private static boolean isReloadAnimationName(String animationName) {
        if (animationName == null || animationName.isBlank()) {
            return false;
        }

        String normalized = animationName.toLowerCase(Locale.ROOT);

        return normalized.startsWith("reload") || normalized.contains("_reload");
    }
}
