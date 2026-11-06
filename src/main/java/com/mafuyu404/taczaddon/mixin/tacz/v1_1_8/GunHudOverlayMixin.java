package com.mafuyu404.taczaddon.mixin.tacz.v1_1_8;

import com.mafuyu404.taczaddon.client.ammo.BackpackHudState;

import com.tacz.guns.client.gui.overlay.GunHudOverlay;
import net.minecraft.world.entity.player.Inventory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(value = GunHudOverlay.class, remap = false)
public class GunHudOverlayMixin {
    @ModifyArg(method = "handleCacheCount", at = @At(value = "INVOKE", target = "Lcom/tacz/guns/client/gui/overlay/GunHudOverlay;handleInventoryAmmo(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/entity/player/Inventory;)V"))
    private static Inventory useBackpackAmmo(Inventory inventory) {
        if (BackpackHudState._virtualInventory != null) return BackpackHudState._virtualInventory;
        return inventory;
    }
}
