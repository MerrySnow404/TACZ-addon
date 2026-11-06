package com.mafuyu404.taczaddon.refit;

/**
 * Pure dependency decisions for the liberateAttachment compatibility gate.
 *
 * <p>Kept outside the mixin package so both the {@code TaczAddonMixinPlugin} and the unit tests can
 * reference it directly (Mixin forbids direct class loading from a configured mixin package).
 */
public final class RefitCompatibility {

    private RefitCompatibility() {}

    /**
     * The server-side provenance-aware unload takeover only requires the unload packet contract.
     */
    public static boolean shouldEnableUnload(boolean unloadContract) {
        return unloadContract;
    }

    /**
     * The client virtual refit UI requires BOTH the GunRefitScreen source contract and the server
     * unload takeover: enabling the virtual refit UI without provenance-aware unload would
     * re-introduce the duplication hole.
     */
    public static boolean shouldEnableRefit(boolean unloadContract, boolean refitContract) {
        return unloadContract && refitContract;
    }
}
