package com.mafuyu404.taczaddon.compat.tacz;

import com.mafuyu404.taczaddon.bootstrap.TaczAddonMixinPlugin;

import com.mafuyu404.taczaddon.compat.tacz.contract.TaczFeature;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TaczVersionAdapterTest {
    @Test
    void runtimeEnablesEveryFeatureButOnlyTheMatchingAimAndAmmoAdapters() {
        for (var feature : TaczFeature.values()) {
            assertTrue(TaczCompatibility.isAvailable(feature), feature + ": " + TaczCompatibility.reason(feature));
        }
        boolean latest = TaczCompatibility.profile() == TaczCompatibilityProfile.TACZ_1_2_0;
        var plugin = new TaczAddonMixinPlugin();
        for (String name : new String[]{"v1_2_0.AimInputMixin", "v1_2_0.ClientAmmoCountMixin"}) {
            assertEquals(latest, plugin.shouldApplyMixin("unused", "com.mafuyu404.taczaddon.mixin.tacz." + name), name);
        }
        for (String name : new String[]{"AimKeyMixin", "v1_1_8.GunHudOverlayMixin"}) {
            assertEquals(!latest, plugin.shouldApplyMixin("unused", "com.mafuyu404.taczaddon.mixin.tacz." + name), name);
        }
    }

    @Test
    void drawTimestampUsesTheUpstreamEpoch() {
        if (TaczCompatibility.profile() == TaczCompatibilityProfile.TACZ_1_2_0) {
            com.mafuyu404.taczaddon.testutil.CompatibilityFixtures.jar("tacz", "1.2.0", "tacz");
            long before = com.tacz.guns.util.time.GunTime.nowMillis();
            long actual = TaczGunClock.nowMillis();
            long after = com.tacz.guns.util.time.GunTime.nowMillis();
            assertTrue(actual >= before && actual <= after);
            assertTrue(actual < System.currentTimeMillis() / 2, "must not mix wall time with the new gun clock");
        } else {
            long before = System.currentTimeMillis();
            long actual = TaczGunClock.nowMillis();
            assertTrue(actual >= before && actual <= System.currentTimeMillis());
        }
    }
}
