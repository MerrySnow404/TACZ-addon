package com.mafuyu404.taczaddon.combat;

import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.resource.pojo.data.gun.Bolt;

import net.minecraft.world.item.ItemStack;

public final class ShootWhileReloadService {
    private ShootWhileReloadService() {}

    public static boolean hasLoadedAmmo(int loadedCount, boolean chambered, Bolt bolt) {
        return (long) loadedCount + (chambered && bolt != Bolt.OPEN_BOLT ? 1 : 0) > 0;
    }

    public static boolean hasLoadedAmmo(ItemStack stack) {
        IGun gun = IGun.getIGunOrNull(stack);
        return gun != null
                && !gun.useInventoryAmmo(stack)
                && TimelessAPI.getCommonGunIndex(gun.getGunId(stack))
                        .map(
                                index ->
                                        hasLoadedAmmo(
                                                gun.getCurrentAmmoCount(stack),
                                                gun.hasBulletInBarrel(stack),
                                                index.getGunData().getBolt()))
                        .orElse(false);
    }
}
