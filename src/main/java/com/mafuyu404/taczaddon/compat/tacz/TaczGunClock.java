package com.mafuyu404.taczaddon.compat.tacz;

/** Draw timestamps must use the same epoch and pause behavior as TaCZ. */
public final class TaczGunClock {
    private TaczGunClock() {}

    public static long nowMillis() {
        return TaczCompatibility.profile() == TaczCompatibilityProfile.TACZ_1_2_0
                ? V120.nowMillis() : System.currentTimeMillis();
    }

    /** Keep the new upstream class unlinked when running either 1.1.8 release. */
    private static final class V120 {
        private static long nowMillis() {
            return com.tacz.guns.util.time.GunTime.nowMillis();
        }
    }
}
