package com.mafuyu404.taczaddon.compat.shouldersurfing;

import com.github.exopandora.shouldersurfing.api.client.IShoulderSurfing;
import com.github.exopandora.shouldersurfing.api.client.event.ComputePlayerAimStateEvent;
import com.github.exopandora.shouldersurfing.api.client.event.ComputeTemporaryFirstPersonStateEvent;
import com.github.exopandora.shouldersurfing.api.event.IEventBus;
import com.github.exopandora.shouldersurfing.api.plugin.IShoulderSurfingPlugin;
import com.mafuyu404.taczaddon.camera.client.AimCameraController;
import com.mafuyu404.taczaddon.camera.client.VanillaAimCamera;

import net.minecraft.client.Minecraft;

/** Loaded exclusively by SSR's shouldersurfing_plugin.json discovery. */
public final class TaczAddonShoulderSurfingPlugin implements IShoulderSurfingPlugin {
    @Override
    public void register(IEventBus eventBus) {
        // HandlerList compares ascending priorities; builtins use 1000. Augment at 2000.
        // Both handlers only add true, so builtin OR behavior is safe in either order.
        eventBus.register(2000, TaczAddonShoulderSurfingPlugin::computeAim);
        eventBus.register(2000, TaczAddonShoulderSurfingPlugin::computeTemporaryFirstPerson);
        VanillaAimCamera.setShoulderSurfingCameraOwner(TaczAddonShoulderSurfingPlugin::ownsCamera);
    }

    private static boolean ownsCamera() {
        IShoulderSurfing camera = IShoulderSurfing.getInstance();
        // Keep ownership throughout temporary first person so SSR can restore its perspective.
        return camera.isShoulderSurfing() || camera.isTemporaryFirstPerson();
    }

    private static void computeAim(ComputePlayerAimStateEvent event) {
        if (!ownsCamera()) return;
        if (event.getEntity() == Minecraft.getInstance().player
                && AimCameraController.isAimActive()) {
            event.setResult(true);
        }
    }

    private static void computeTemporaryFirstPerson(ComputeTemporaryFirstPersonStateEvent event) {
        if (ownsCamera() && AimCameraController.isTemporaryFirstPersonRequested())
            event.setResult(true);
    }
}
