package com.mafuyu404.taczaddon.compat.camera;

import com.mafuyu404.taczaddon.compat.api.ApiShapeProbe;

import com.mojang.logging.LogUtils;
import net.minecraftforge.fml.ModList;
import org.slf4j.Logger;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Optional Perspective API facade used by Leawind Third Person 3.x.
 *
 * <p>The outer class contains no Perspective API types. The inner class is
 * loaded only after the runtime profile verified that the installed generation
 * actually provides the API shape the backend uses.
 *
 * <p>The Leawind generation is verified structurally. Leawind 2.x does not
 * depend on {@code perspective_api} and is reported as
 * {@link PerspectiveIntegrationProfile#LEAWIND_2_UNSUPPORTED} even when
 * Perspective API is installed next to it: it simply coexists, no backend is
 * loaded, and no Perspective API capability is claimed.
 */
public final class PerspectiveApiCompat {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String LEAWIND_MOD_ID =
            PerspectiveIntegrationProfile.LEAWIND_MOD_ID;
    private static final String MOD_ID =
            PerspectiveIntegrationProfile.PERSPECTIVE_API_MOD_ID;

    private static volatile boolean linkageBroken;
    private static final AtomicBoolean LINKAGE_WARNING_LOGGED =
            new AtomicBoolean();
    private static final AtomicBoolean PROFILE_WARNING_LOGGED =
            new AtomicBoolean();
    private static volatile PerspectiveIntegrationProfile profile;

    private PerspectiveApiCompat() {
    }

    public static boolean isInstalled() {
        return isModLoaded(LEAWIND_MOD_ID);
    }

    public static boolean isPerspectiveApiInstalled() {
        return isModLoaded(MOD_ID);
    }

    public static PerspectiveIntegrationProfile profile() {
        PerspectiveIntegrationProfile current = profile;
        if (current != null) {
            return current;
        }
        PerspectiveIntegrationProfile detected =
                PerspectiveIntegrationProfile.detect(
                        isInstalled(),
                        isPerspectiveApiInstalled(),
                        ApiShapeProbe.sourceFor(PerspectiveApiCompat.class)
                );
        profile = detected;
        if (detected == PerspectiveIntegrationProfile.UNKNOWN
                || detected
                == PerspectiveIntegrationProfile
                .LEAWIND_3_MISSING_PERSPECTIVE_API) {
            warnUnknownProfile();
        }
        return detected;
    }

    /** True only for a structurally verified Perspective API generation. */
    public static boolean isSupported() {
        return profile().backendSupported();
    }

    public static String currentNonVanillaPerspectiveId() {
        if (!isUsable()) {
            return null;
        }
        try {
            String perspectiveId =
                    PerspectiveApiCompatInner.currentPerspectiveId();
            if (perspectiveId == null
                    || PerspectiveCameraIds.FIRST_PERSON_ID.equals(perspectiveId)) {
                return null;
            }
            return perspectiveId;
        } catch (LinkageError linkageError) {
            breakLinkage(linkageError);
            return null;
        }
    }

    public static boolean isFirstPersonActive() {
        if (!isUsable()) {
            return false;
        }
        try {
            return PerspectiveApiCompatInner.isFirstPersonActive();
        } catch (LinkageError linkageError) {
            breakLinkage(linkageError);
            return false;
        }
    }

    public static PerspectiveApiHandle requestFirstPerson() {
        if (!isUsable()) {
            return null;
        }
        try {
            PerspectiveApiHandle unsafeHandle =
                    PerspectiveApiCompatInner.requestFirstPerson();
            if (unsafeHandle == null) {
                return null;
            }
            return new SafePerspectiveApiHandle(unsafeHandle);
        } catch (LinkageError linkageError) {
            breakLinkage(linkageError);
            return null;
        }
    }

    private static boolean isUsable() {
        return isSupported() && !linkageBroken;
    }

    private static boolean isModLoaded(String modId) {
        ModList modList = ModList.get();
        return modList != null && modList.isLoaded(modId);
    }

    private static void breakLinkage(LinkageError linkageError) {
        linkageBroken = true;
        if (LINKAGE_WARNING_LOGGED.compareAndSet(false, true)) {
            LOGGER.warn(
                    "[TACZ-addon] Perspective API is unavailable; "
                            + "Leawind camera integration disabled",
                    linkageError
            );
        }
    }

    private static void warnUnknownProfile() {
        if (PROFILE_WARNING_LOGGED.compareAndSet(false, true)) {
            LOGGER.warn(
                    "[TACZ-addon] Leawind/Perspective API generation {} "
                            + "could not be used; the Leawind camera "
                            + "integration stays unavailable",
                    profile
            );
        }
    }

    private static final class SafePerspectiveApiHandle
            implements PerspectiveApiHandle {
        private final PerspectiveApiHandle delegate;
        private final AtomicBoolean restored = new AtomicBoolean();

        private SafePerspectiveApiHandle(PerspectiveApiHandle delegate) {
            this.delegate = delegate;
        }

        @Override
        public void restore() {
            if (!this.restored.compareAndSet(false, true)) {
                return;
            }
            try {
                this.delegate.restore();
            } catch (LinkageError linkageError) {
                breakLinkage(linkageError);
            }
        }
    }
}
