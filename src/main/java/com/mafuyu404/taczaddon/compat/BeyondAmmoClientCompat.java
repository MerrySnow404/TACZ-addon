package com.mafuyu404.taczaddon.compat;

import net.minecraft.world.item.ItemStack;

/** Client code stays behind the optional dependency boundary. */
public final class BeyondAmmoClientCompat {
    private static boolean broken;

    private BeyondAmmoClientCompat() {}

    public static boolean isUsable() {
        return !broken && BeyondIntegrationCompat.isUsable();
    }

    public static boolean hasConfirmedAmmo(ItemStack gun) {
        if (!isUsable()) return false;
        try {
            return BeyondAmmoClientCompatInner.hasConfirmedAmmo(gun);
        } catch (LinkageError failure) {
            broken = true;
            com.mojang.logging.LogUtils.getLogger()
                    .warn("Beyond client ammo query unavailable", failure);
            return false;
        }
    }
}
