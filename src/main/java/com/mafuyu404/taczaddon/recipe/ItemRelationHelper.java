package com.mafuyu404.taczaddon.recipe;

import com.tacz.guns.api.DefaultAssets;
import com.tacz.guns.api.item.IAmmo;
import com.tacz.guns.api.item.IAmmoBox;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.builder.AmmoItemBuilder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

public final class ItemRelationHelper {
    private ItemRelationHelper() {
    }

    public static boolean areRelated(ItemStack first, ItemStack second) {
        try {
            return isRelatedToGun(first, second) || isRelatedToGun(second, first);
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private static boolean isRelatedToGun(ItemStack gunStack, ItemStack candidateStack) {
        if (gunStack.isEmpty() || candidateStack.isEmpty()) {
            return false;
        }

        IGun iGun = IGun.getIGunOrNull(gunStack);
        if (iGun == null) {
            return false;
        }

        boolean isAttachment = iGun.allowAttachment(gunStack, candidateStack);

        IAmmo iAmmo = IAmmo.getIAmmoOrNull(candidateStack);
        boolean isAmmo = iAmmo != null && iAmmo.isAmmoOfGun(gunStack, candidateStack);

        boolean isAmmoBox = isAmmoBoxOfGun(gunStack, candidateStack);

        return isAttachment || isAmmo || isAmmoBox;
    }

    private static boolean isAmmoBoxOfGun(ItemStack gunStack, ItemStack ammoBoxStack) {
        if (!(ammoBoxStack.getItem() instanceof IAmmoBox ammoBox)) {
            return false;
        }

        if (ammoBox.isAllTypeCreative(ammoBoxStack)) {
            return true;
        }

        ResourceLocation ammoId = ammoBox.getAmmoId(ammoBoxStack);

        if (ammoId.equals(DefaultAssets.EMPTY_AMMO_ID)) {
            return false;
        }

        if (!ammoBox.isCreative(ammoBoxStack) && ammoBox.getAmmoCount(ammoBoxStack) <= 0) {
            return false;
        }

        ItemStack virtualAmmoStack = AmmoItemBuilder.create()
                .setId(ammoId)
                .setCount(1)
                .build();

        IAmmo virtualAmmo = IAmmo.getIAmmoOrNull(virtualAmmoStack);

        return virtualAmmo != null && virtualAmmo.isAmmoOfGun(gunStack, virtualAmmoStack);
    }
}
