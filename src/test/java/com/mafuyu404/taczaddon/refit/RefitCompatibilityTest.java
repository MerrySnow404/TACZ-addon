package com.mafuyu404.taczaddon.refit;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Compatibility dependency-decision regression tests.
 *
 * <p>The client virtual refit UI must only be enabled when BOTH the GunRefitScreen source contract
 * AND the server unload takeover contract hold. The server-side unload protection may stand alone.
 */
class RefitCompatibilityTest {

    @Test
    void unloadProtectionRequiresUnloadContract() {
        assertTrue(RefitCompatibility.shouldEnableUnload(true));
        assertFalse(RefitCompatibility.shouldEnableUnload(false));
    }

    @Test
    void refitRequiresBothContracts() {
        assertTrue(RefitCompatibility.shouldEnableRefit(true, true));
        assertFalse(RefitCompatibility.shouldEnableRefit(false, true));
        assertFalse(RefitCompatibility.shouldEnableRefit(true, false));
        assertFalse(RefitCompatibility.shouldEnableRefit(false, false));
    }
}
