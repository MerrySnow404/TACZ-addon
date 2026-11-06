package com.mafuyu404.taczaddon.ammo.client;

import com.mafuyu404.taczaddon.ammo.AmmoSlotOperations;
import com.mafuyu404.taczaddon.compat.CreateStorageCompat;
import com.mafuyu404.taczaddon.compat.CuriosCompat;
import com.mafuyu404.taczaddon.compat.SophisticatedBackpacksCompat;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

public final class ExtraAmmoCount {
    private ExtraAmmoCount() {}

    public static int append(Player player, ItemStack gun, int nativeCount) {
        if (nativeCount >= 9999) return 9999;
        int[] count = {
            (int)
                    Math.min(
                            9999L,
                            (long) nativeCount
                                    + SophisticatedBackpacksCompat.countInventoryBackpackAmmo(
                                            player, gun))
        };
        CreateStorageCompat.visitBackpacks(
                player,
                handler -> {
                    count[0] =
                            (int)
                                    Math.min(
                                            9999L,
                                            (long) count[0]
                                                    + AmmoSlotOperations.countAmmo(handler, gun));
                    return count[0] >= 9999;
                });
        return count[0];
    }

    public static int appendForOfficial(Player player, ItemStack gun, int nativeCount) {
        int[] count = {append(player, gun, nativeCount)};
        // Official doShoot uses this count to cap bursts, so equipped ammo must participate too.
        CuriosCompat.visitHandlers(
                player,
                handler -> {
                    count[0] =
                            (int)
                                    Math.min(
                                            9999L,
                                            (long) count[0]
                                                    + AmmoSlotOperations.countAmmo(handler, gun));
                    return count[0] >= 9999;
                });
        return count[0];
    }
}
