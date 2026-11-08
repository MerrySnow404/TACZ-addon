package com.mafuyu404.taczaddon.compat;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/** Client-side guard query, isolated from Beyond Integration's classes. */
final class BeyondAmmoClientCompatInner {
    private BeyondAmmoClientCompatInner() {}

    static boolean hasConfirmedAmmo(ItemStack gun) {
        ResourceLocation ammoId = BeyondAmmoApi.getAmmoIdClient(gun);
        if (ammoId == null) {
            return false;
        }
        if (!BeyondAmmoApi.hasData(ammoId)) {
            BeyondAmmoApi.requestQuick(ammoId);
            return false;
        }
        return BeyondAmmoApi.getCount(ammoId) > 0;
    }
}
