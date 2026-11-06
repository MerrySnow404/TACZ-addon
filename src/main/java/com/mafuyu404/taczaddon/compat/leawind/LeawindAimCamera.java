package com.mafuyu404.taczaddon.compat.leawind;

import com.mafuyu404.taczaddon.camera.client.AimCameraController;

import io.github.leawind.perspectiveapi.api.PerspectiveAPI;
import io.github.leawind.perspectiveapi.api.PerspectiveSwitcherBehavior;

/** Leawind artifact 8602835 is 3.0.3-beta, not the 2.x GitHub 1.21 branch. */
public final class LeawindAimCamera {
    private static final String LEAWIND_PERSPECTIVE = "leawind_third_person.third_person";

    private LeawindAimCamera() {}

    public static void init() {
        PerspectiveAPI.runWhenReady(
                "taczaddon:better_aim_camera",
                () ->
                        // priority:perspective_api.override = 1000; above the builtin switcher
                        // (MIN_VALUE).
                        PerspectiveAPI.getOverrideChain()
                                .register(1000, LeawindAimCamera::requestedPerspective));
    }

    private static String requestedPerspective() {
        if (!AimCameraController.isTemporaryFirstPersonRequested()) return null;
        // Read the player's selection, not getCurrent(): the latter is our temporary FP
        // after the first frame and would produce an FP/third-person oscillation.
        var switcher = PerspectiveAPI.getSwitcherManager().getSelectedSwitcher();
        if (switcher instanceof PerspectiveSwitcherBehavior behavior) {
            String selected = behavior.getSelectedPerspectiveId();
            // Perspective API also owns vanilla third person, so VanillaAimCamera yields here.
            if (LEAWIND_PERSPECTIVE.equals(selected)
                    || "perspective_api.third_person_back".equals(selected)
                    || "perspective_api.third_person_front".equals(selected)) {
                return "perspective_api.first_person";
            }
        }
        // Selecting actual first person (or another perspective) never inverts it.
        return null;
    }
}
