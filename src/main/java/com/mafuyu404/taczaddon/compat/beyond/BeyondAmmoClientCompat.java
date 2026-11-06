package com.mafuyu404.taczaddon.compat.beyond;

import com.mojang.logging.LogUtils;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class BeyondAmmoClientCompat {
    private static boolean linkageBroken;

    private BeyondAmmoClientCompat() {}

    public static boolean isUsable() {
        return !linkageBroken && BeyondIntegrationCompat.isInstalled()
                && BeyondAmmoCacheContract.isSupported();
    }

    public static boolean hasConfirmedAmmo(ItemStack gun) {
        if (!isUsable() || gun == null || gun.isEmpty()) return false;
        try {
            return BeyondAmmoClientCompatInner.hasConfirmedAmmo(gun);
        } catch (LinkageError error) {
            linkageBroken = true;
            LogUtils.getLogger().warn("[TACZ-addon] Beyond client ammo guard disabled after linkage failure", error);
            return false;
        }
    }

    /** 1.2.0's shared client ammo counter also needs confirmed network reserves. */
    public static int confirmedAmmoCount(ItemStack gun) {
        if (!isUsable() || gun == null || gun.isEmpty()) return 0;
        try {
            return BeyondAmmoClientCompatInner.confirmedAmmoCount(gun);
        } catch (LinkageError error) {
            linkageBroken = true;
            LogUtils.getLogger().warn("[TACZ-addon] Beyond client ammo count disabled after linkage failure", error);
            return 0;
        }
    }
}
