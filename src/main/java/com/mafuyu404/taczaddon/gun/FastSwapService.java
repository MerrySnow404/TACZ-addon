package com.mafuyu404.taczaddon.gun;

import com.mafuyu404.taczaddon.config.CommonConfig;

/**
 * Fast swap policy resolution.
 *
 * <p>The put-away skip is a gameplay decision: it is owned by the server
 * configuration and used by the server-visible draw hook. Client-side cooldown
 * prediction never reads the local client option; it uses the value the
 * current server synchronized, so a server with fast swap disabled stays
 * consistent with its clients.
 */
public final class FastSwapService {
    private FastSwapService() {
    }

    /** Server-authoritative policy used by the shared draw hook. */
    public static boolean serverPolicyEnabled() {
        return CommonConfig.enableFastSwapGun();
    }
}
