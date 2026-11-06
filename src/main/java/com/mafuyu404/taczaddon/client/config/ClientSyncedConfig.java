package com.mafuyu404.taczaddon.client.config;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * Client mirror of policies owned by the connected server.
 *
 * Server-authoritative features must remain disabled until an explicit
 * configuration packet has been accepted from the current connection.
 */
@OnlyIn(Dist.CLIENT)
public final class ClientSyncedConfig {
    private static volatile int batchCraftMax = 1;

    private static volatile boolean liberateAttachment = false;
    private static volatile boolean showAttachmentDetail = false;

    /*
     * Fail closed. A previous server's value must never authorize client
     * prediction on a new connection.
     */
    private static volatile boolean enableShootWhileReloading =
            false;

    /*
     * Server-owned fast-swap policy. It stays false until the current server
     * synchronizes it, so client-side cooldown prediction can never run ahead
     * of the authoritative value.
     */
    private static volatile boolean enableFastSwapGun = false;

    private ClientSyncedConfig() {
    }

    public static void apply(
            int max, boolean shootWhileReloading, boolean fastSwapGun
    ) {
        batchCraftMax = max;
        enableShootWhileReloading =
                shootWhileReloading;
        enableFastSwapGun = fastSwapGun;
    }

    public static int batchCraftMax() {
        return batchCraftMax;
    }

    public static boolean enableShootWhileReloading() {
        return enableShootWhileReloading;
    }

    public static boolean enableFastSwapGun() {
        return enableFastSwapGun;
    }

    public static void setLiberateAttachment(boolean enabled) {
        liberateAttachment = enabled;
    }

    public static boolean liberateAttachment() {
        return liberateAttachment;
    }

    public static void setShowAttachmentDetail(boolean enabled) {
        showAttachmentDetail = enabled;
    }

    public static boolean showAttachmentDetail() {
        return showAttachmentDetail;
    }

    public static void resetToSafeDefaults() {
        showAttachmentDetail = false;
        batchCraftMax = 1;
        enableShootWhileReloading = false;
        enableFastSwapGun = false;
        liberateAttachment = false;
    }
}
