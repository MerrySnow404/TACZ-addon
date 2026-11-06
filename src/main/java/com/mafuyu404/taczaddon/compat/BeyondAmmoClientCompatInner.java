package com.mafuyu404.taczaddon.compat;

import com.solr98.beyondintegration.client.TaczAmmoCache;
import com.solr98.beyondintegration.handler.TaczAmmoExtractor;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

final class BeyondAmmoClientCompatInner {
    private BeyondAmmoClientCompatInner() {}

    static boolean hasConfirmedAmmo(ItemStack gun) {
        ResourceLocation ammoId = TaczAmmoExtractor.getAmmoIdClient(gun);
        if (ammoId == null) return false;
        if (!TaczAmmoCache.hasData(ammoId)) {
            TaczAmmoCache.requestQuick(ammoId);
            return false;
        }
        return TaczAmmoCache.getCount(ammoId) > 0;
    }
}
