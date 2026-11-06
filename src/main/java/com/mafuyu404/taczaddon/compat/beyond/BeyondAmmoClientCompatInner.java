package com.mafuyu404.taczaddon.compat.beyond;

import com.solr98.beyondintegration.client.TaczAmmoCache;
import com.solr98.beyondintegration.feature.ammo.tacz.TaczAmmoExtractor;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

final class BeyondAmmoClientCompatInner {
    private BeyondAmmoClientCompatInner() {}

    static boolean hasConfirmedAmmo(ItemStack gun) {
        return confirmedAmmoCount(gun) > 0;
    }

    static int confirmedAmmoCount(ItemStack gun) {
        ResourceLocation ammoId = TaczAmmoExtractor.getAmmoIdClient(gun);
        if (ammoId == null) return 0;
        if (!TaczAmmoCache.hasData(ammoId)) {
            TaczAmmoCache.requestQuick(ammoId);
            return 0;
        }
        return Math.max(0, TaczAmmoCache.getCount(ammoId));
    }
}
