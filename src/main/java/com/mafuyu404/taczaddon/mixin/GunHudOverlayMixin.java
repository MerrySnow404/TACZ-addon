package com.mafuyu404.taczaddon.mixin;

import com.mafuyu404.taczaddon.ammo.client.ExtraAmmoCount;
import com.tacz.guns.client.gui.overlay.GunHudOverlay;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = GunHudOverlay.class, remap = false)
public abstract class GunHudOverlayMixin {
    private static final int MAX_AMMO_COUNT = 9999;

    @Shadow private static int cacheInventoryAmmoCount;

    /**
     * Let TaCZ count the normal player inventory first, then append ammunition from Sophisticated
     * Backpacks.
     *
     * <p>This avoids temporarily replacing the Inventory argument with a null or rebuilding
     * VirtualInventory instance.
     */
    @Inject(method = "handleInventoryAmmo", at = @At("RETURN"), remap = false)
    private static void taczaddon$appendBackpackAmmo(
            ItemStack gunStack, Inventory inventory, CallbackInfo ci) {
        if (cacheInventoryAmmoCount >= MAX_AMMO_COUNT) {
            return;
        }

        Player player = inventory.player;
        if (player == null) {
            return;
        }

        cacheInventoryAmmoCount = ExtraAmmoCount.append(player, gunStack, cacheInventoryAmmoCount);
    }
}
