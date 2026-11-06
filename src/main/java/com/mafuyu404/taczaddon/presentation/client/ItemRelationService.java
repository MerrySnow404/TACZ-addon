package com.mafuyu404.taczaddon.presentation.client;

import com.mojang.logging.LogUtils;
import com.tacz.guns.api.DefaultAssets;
import com.tacz.guns.api.item.IAmmo;
import com.tacz.guns.api.item.IAmmoBox;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.builder.AmmoItemBuilder;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import org.slf4j.Logger;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

public final class ItemRelationService {
    private static final Logger LOGGER = LogUtils.getLogger();

    private static boolean loggedRelationFailure;

    private ItemRelationService() {}

    public static Set<Slot> findRelatedSlots(
            AbstractContainerMenu menu, Slot hoveredSlot, ItemStack hoveredStack) {
        Set<Slot> relatedSlots = Collections.newSetFromMap(new IdentityHashMap<>());

        if (menu == null || hoveredSlot == null || hoveredStack.isEmpty()) {
            return relatedSlots;
        }

        for (Slot candidateSlot : menu.slots) {
            if (candidateSlot == hoveredSlot
                    || !candidateSlot.isActive()
                    || !candidateSlot.hasItem()) {
                continue;
            }

            ItemStack candidateStack = candidateSlot.getItem();

            if (candidateStack.isEmpty()) {
                continue;
            }

            if (isRelatedSafely(hoveredStack, candidateStack)
                    || isRelatedSafely(candidateStack, hoveredStack)) {
                relatedSlots.add(candidateSlot);
            }
        }

        return relatedSlots;
    }

    private static boolean isRelatedSafely(ItemStack gunItem, ItemStack itemStack) {
        try {
            return isRelated(gunItem, itemStack);
        } catch (IllegalArgumentException | NullPointerException ex) {
            if (!loggedRelationFailure) {
                loggedRelationFailure = true;

                LOGGER.debug(
                        "Unable to evaluate TaCZ item relation " + "for malformed item data", ex);
            }

            return false;
        }
    }

    private static boolean isRelated(ItemStack gunItem, ItemStack itemStack) {
        if (gunItem.isEmpty() || itemStack.isEmpty()) {
            return false;
        }

        IGun gun = IGun.getIGunOrNull(gunItem);

        if (gun == null) {
            return false;
        }

        IAmmo ammo = IAmmo.getIAmmoOrNull(itemStack);

        return gun.allowAttachment(gunItem, itemStack)
                || (ammo != null && ammo.isAmmoOfGun(gunItem, itemStack))
                || isAmmoBoxOfGun(gunItem, itemStack);
    }

    private static boolean isAmmoBoxOfGun(ItemStack gunItem, ItemStack ammoBoxStack) {
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

        ItemStack virtualAmmoStack = AmmoItemBuilder.create().setId(ammoId).setCount(1).build();

        IAmmo virtualAmmo = IAmmo.getIAmmoOrNull(virtualAmmoStack);

        return virtualAmmo != null && virtualAmmo.isAmmoOfGun(gunItem, virtualAmmoStack);
    }
}
