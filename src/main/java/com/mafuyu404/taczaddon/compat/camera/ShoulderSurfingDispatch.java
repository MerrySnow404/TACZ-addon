package com.mafuyu404.taczaddon.compat.camera;

/**
 * What the Shoulder Surfing crosshair bridge must do for the installed
 * generation.
 *
 * <p>TaCZ 1.1.8-hotfix ships its own legacy compatibility class that links
 * {@code api/model/Perspective} and {@code client/InputHandler} directly. That
 * native path is only safe when the installed generation really provides those
 * classes:
 *
 * <ul>
 *     <li>the mod is absent - TaCZ's own check returns false, nothing loads;</li>
 *     <li>the legacy generation is installed - TaCZ's own path is the correct
 *     implementation and must run;</li>
 *     <li>an unknown generation is installed - TaCZ's legacy backend would
 *     fail to link, so the addon must answer instead of letting that
 *     {@link LinkageError} escape;</li>
 *     <li>a verified 5.x generation is installed - the addon backend owns the
 *     decision. If that backend later breaks, it stays an addon failure and
 *     must never be "recovered" by loading TaCZ's legacy backend.</li>
 * </ul>
 */
public enum ShoulderSurfingDispatch {
    /** Do not cancel the injection: TaCZ's own compatibility path runs. */
    DELEGATE_TAZC_LEGACY,
    /** Cancel the injection and answer from the verified 5.x backend. */
    USE_ADDON_V5,
    /** Cancel the injection and answer {@code false} without calling TaCZ. */
    BLOCK_UNSUPPORTED;

    public static ShoulderSurfingDispatch resolve(
            ShoulderSurfingGeneration generation,
            boolean backendLinkageBroken
    ) {
        if (generation == null) {
            return BLOCK_UNSUPPORTED;
        }
        return switch (generation) {
            case ABSENT, LEGACY -> DELEGATE_TAZC_LEGACY;
            case API_V5 -> backendLinkageBroken
                    ? BLOCK_UNSUPPORTED
                    : USE_ADDON_V5;
            case UNKNOWN -> BLOCK_UNSUPPORTED;
        };
    }
}
