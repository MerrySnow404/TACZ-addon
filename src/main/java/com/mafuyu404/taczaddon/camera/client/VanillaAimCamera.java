package com.mafuyu404.taczaddon.camera.client;

import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.neoforged.fml.ModList;

import java.util.function.BooleanSupplier;

/** Owns only the vanilla perspective captured for a single ADS session. */
public final class VanillaAimCamera {
    // Installation alone does not imply ownership; SSR's plugin supplies its live state.
    private static BooleanSupplier shoulderSurfingOwnsCamera = () -> false;
    private CameraType restoreType;
    private boolean aiming;
    private boolean switched;

    public static void setShoulderSurfingCameraOwner(BooleanSupplier owner) {
        shoulderSurfingOwnsCamera = owner;
    }

    private static boolean hasThirdPartyOwner() {
        // Perspective API owns all its perspectives, including temporary first person.
        // Never add a competing Options writer when Leawind/Perspective API is installed.
        return ModList.get().isLoaded("leawind_third_person")
                || ModList.get().isLoaded("perspective_api")
                || shoulderSurfingOwnsCamera.getAsBoolean();
    }

    void update(Minecraft minecraft, boolean aimActive, boolean requested) {
        if (hasThirdPartyOwner()) {
            clear();
            aiming = aimActive;
            return;
        }
        if (!aimActive) {
            end(minecraft);
            return;
        }
        CameraType current = minecraft.options.getCameraType();
        if (!aiming) {
            aiming = true;
            if (current != CameraType.FIRST_PERSON) restoreType = current;
        }
        if (restoreType == null) return;
        if (current != (switched ? CameraType.FIRST_PERSON : restoreType)) {
            // A user camera change relinquishes this ADS session, including a pending switch.
            restoreType = null;
            switched = false;
            return;
        }
        if (requested && !switched) {
            minecraft.options.setCameraType(CameraType.FIRST_PERSON);
            switched = true;
        }
    }

    void end(Minecraft minecraft) {
        if (!hasThirdPartyOwner()
                && switched
                && restoreType != null
                && minecraft.options.getCameraType() == CameraType.FIRST_PERSON) {
            minecraft.options.setCameraType(restoreType);
        }
        clear();
    }

    private void clear() {
        restoreType = null;
        aiming = false;
        switched = false;
    }
}
