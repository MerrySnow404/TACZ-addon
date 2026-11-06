package com.mafuyu404.taczaddon.bootstrap;

import com.mafuyu404.taczaddon.compat.JeiCompat;
import com.mafuyu404.taczaddon.compat.SophisticatedBackpacksCompat;
import com.mafuyu404.taczaddon.compat.leawind.LeawindAimCamera;

import net.neoforged.fml.ModList;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;

public final class SetupEvent {
    private SetupEvent() {}

    public static void onClientSetup(FMLClientSetupEvent event) {
        // Keep all Perspective API linkage behind the optional-mod boundary.
        if (ModList.get().isLoaded("leawind_third_person")
                && ModList.get().isLoaded("perspective_api")) {
            event.enqueueWork(() -> LeawindAimCamera.init());
        }
        event.enqueueWork(JeiCompat::init);
        event.enqueueWork(SophisticatedBackpacksCompat::init);
    }
}
