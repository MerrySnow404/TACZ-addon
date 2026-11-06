package com.mafuyu404.taczaddon.config;

public final class ClientSyncedConfig {
    private static volatile boolean shootWhileReloading;
    private static volatile boolean fastSwapGun;
    private static volatile boolean nearbyContainerSources;
    private static volatile int containerScanRadius = 3;
    private static volatile int batchCraftMax = 1;

    private ClientSyncedConfig() {}

    public static void apply(
            boolean shoot, boolean fast, boolean nearby, int radius, int batchMax) {
        shootWhileReloading = shoot;
        fastSwapGun = fast;
        nearbyContainerSources = nearby;
        containerScanRadius = Math.max(1, Math.min(radius, 16));
        batchCraftMax = Math.max(1, Math.min(batchMax, 64));
    }

    public static boolean enableFastSwapGun() {
        return fastSwapGun;
    }

    public static boolean enableShootWhileReloading() {
        return shootWhileReloading;
    }

    public static boolean enableNearbyContainerSources() {
        return nearbyContainerSources;
    }

    public static int getContainerScanRadius() {
        return containerScanRadius;
    }

    public static int getBatchCraftMax() {
        return batchCraftMax;
    }

    public static void reset() {
        apply(false, false, false, 3, 1);
    }
}
