package com.mafuyu404.taczaddon.compat.sophisticated.client;

import com.mafuyu404.taczaddon.compat.SophisticatedBackpacksCompat;
import com.mafuyu404.taczaddon.inventory.client.BackpackInventoryCache;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

import java.util.UUID;

public final class SophisticatedBackpacksClientSync {
    private SophisticatedBackpacksClientSync() {}

    public static void onContentsUpdated(UUID backpackUuid) {
        Minecraft minecraft = Minecraft.getInstance();

        /*
         * Keep wrapper and virtual-inventory mutation on the client thread,
         * regardless of the payload handler's execution context.
         */
        minecraft.execute(
                () -> {
                    LocalPlayer player = minecraft.player;

                    if (player == null) {
                        return;
                    }

                    boolean refreshed =
                            SophisticatedBackpacksCompat.refreshInventoryBackpackWrapper(
                                    player, backpackUuid);

                    if (refreshed) {
                        BackpackInventoryCache.invalidateBackpackCache();
                    }
                });
    }
}
