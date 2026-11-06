package com.mafuyu404.taczaddon.compat.camera;

import com.mafuyu404.taczaddon.compat.api.ApiShapeProbe;

import com.mojang.logging.LogUtils;
import net.minecraftforge.fml.ModList;
import org.slf4j.Logger;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * SSR 5.x-only facade. The outer class never references Shoulder Surfing
 * types so this mod still loads when Shoulder Surfing is absent.
 *
 * <p>Three independent states are reported:
 *
 * <ul>
 *     <li>{@link #isInstalled()} - Forge only reports the mod as loaded;</li>
 *     <li>{@link #isSupported()} - the new API generation was verified
 *     structurally. Legacy 4.x installations are deliberately reported as
 *     unsupported here so TaCZ 1.1.8-hotfix keeps running its own legacy
 *     compatibility path;</li>
 *     <li>{@link #isUsable()} - supported and the optional backend has not
 *     tripped its linkage circuit breaker.</li>
 * </ul>
 */
public final class ShoulderSurfing5Compat {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String MOD_ID =
            ShoulderSurfingGeneration.MOD_ID;

    private static volatile boolean linkageBroken;
    private static final AtomicBoolean LINKAGE_WARNING_LOGGED =
            new AtomicBoolean();
    private static final AtomicBoolean GENERATION_WARNING_LOGGED =
            new AtomicBoolean();
    private static volatile ShoulderSurfingGeneration generation;

    private ShoulderSurfing5Compat() {
    }

    public static boolean isInstalled() {
        ModList modList = ModList.get();
        return modList != null && modList.isLoaded(MOD_ID);
    }

    /**
     * Which upstream API generation is installed. Detection is structural and
     * cached; it never links the optional classes.
     */
    public static ShoulderSurfingGeneration generation() {
        ShoulderSurfingGeneration current = generation;
        if (current != null) {
            return current;
        }
        ShoulderSurfingGeneration detected =
                ShoulderSurfingGeneration.detect(
                        isInstalled(),
                        ApiShapeProbe.sourceFor(ShoulderSurfing5Compat.class)
                );
        generation = detected;
        if (detected == ShoulderSurfingGeneration.UNKNOWN) {
            warnUnknownGeneration();
        }
        return detected;
    }

    /** True only for a structurally verified new (5.x) API generation. */
    public static boolean isSupported() {
        return generation() == ShoulderSurfingGeneration.API_V5;
    }

    /**
     * Dispatch policy for the crosshair bridge.
     *
     * <p>Legacy and absent generations keep TaCZ's own compatibility
     * implementation. An unknown generation is answered by the addon instead,
     * because TaCZ's legacy backend cannot link against it. A broken addon
     * backend also stays with the addon: a verified 5.x installation is never
     * downgraded to "legacy".
     */
    public static ShoulderSurfingDispatch dispatch() {
        return ShoulderSurfingDispatch.resolve(
                generation(),
                linkageBroken
        );
    }

    /** True only when the addon answers with its verified 5.x backend. */
    public static boolean usesAddonBackend() {
        return dispatch() == ShoulderSurfingDispatch.USE_ADDON_V5;
    }

    /**
     * The crosshair decision the injection must apply, expressed as a pure
     * function so the dispatch policy can be tested without a Mixin instance.
     *
     * @return {@code null} when TaCZ's own compatibility path must run
     *         unchanged, {@code true} when the addon backend reports an active
     *         crosshair, and {@code false} when the addon must answer without
     *         touching TaCZ's legacy backend
     */
    public static Boolean crosshairOverride() {
        return switch (dispatch()) {
            case USE_ADDON_V5 ->
                    showCrosshairWhenShoulderSurfing();
            case BLOCK_UNSUPPORTED -> Boolean.FALSE;
            case DELEGATE_TAZC_LEGACY -> null;
        };
    }

    public static boolean isShoulderSurfing() {
        if (!isUsable()) {
            return false;
        }
        try {
            return ShoulderSurfing5CompatInner.isShoulderSurfing();
        } catch (LinkageError linkageError) {
            breakLinkage(linkageError);
            return false;
        }
    }

    public static boolean isFreeLooking() {
        if (!isUsable()) {
            return false;
        }
        try {
            return ShoulderSurfing5CompatInner.isFreeLooking();
        } catch (LinkageError linkageError) {
            breakLinkage(linkageError);
            return false;
        }
    }

    /**
     * Verifies the exact state produced by the addon when it asks SSR to enter
     * first person. Keeping the SSR query inside this optional boundary means
     * an ABI failure cannot be mistaken for valid camera ownership.
     */
    public static boolean isFirstPersonActive(
            boolean vanillaCameraFirstPerson
    ) {
        if (!isUsable()) {
            return false;
        }
        try {
            return vanillaCameraFirstPerson
                    && !ShoulderSurfing5CompatInner.isShoulderSurfing();
        } catch (LinkageError linkageError) {
            breakLinkage(linkageError);
            return false;
        }
    }

    /**
     * @return true only if the SSR API call completed successfully.
     */
    public static boolean forceFirstPerson() {
        if (!isUsable()) {
            return false;
        }
        try {
            ShoulderSurfing5CompatInner.forceFirstPerson();
            return true;
        } catch (LinkageError linkageError) {
            breakLinkage(linkageError);
            return false;
        }
    }

    public static boolean enableShoulderSurfing() {
        if (!isUsable()) {
            return false;
        }
        try {
            ShoulderSurfing5CompatInner.enableShoulderSurfing();
            return true;
        } catch (LinkageError linkageError) {
            breakLinkage(linkageError);
            return false;
        }
    }

    public static boolean showCrosshairWhenShoulderSurfing() {
        if (!isUsable()) {
            return false;
        }
        try {
            return ShoulderSurfing5CompatInner.isShoulderSurfing()
                    && !ShoulderSurfing5CompatInner.isFreeLooking();
        } catch (LinkageError linkageError) {
            breakLinkage(linkageError);
            return false;
        }
    }

    private static boolean isUsable() {
        return isSupported() && !linkageBroken;
    }

    private static void breakLinkage(LinkageError linkageError) {
        linkageBroken = true;
        if (LINKAGE_WARNING_LOGGED.compareAndSet(false, true)) {
            LOGGER.warn(
                    "[TACZ-addon] SSR 5.x API is unavailable; "
                            + "Shoulder Surfing integration disabled",
                    linkageError
            );
        }
    }

    private static void warnUnknownGeneration() {
        if (GENERATION_WARNING_LOGGED.compareAndSet(false, true)) {
            LOGGER.warn(
                    "[TACZ-addon] Shoulder Surfing is installed but its API "
                            + "generation could not be verified; TaCZ's legacy "
                            + "Shoulder Surfing path is blocked so an "
                            + "incompatible API is never linked against"
            );
        }
    }
}
