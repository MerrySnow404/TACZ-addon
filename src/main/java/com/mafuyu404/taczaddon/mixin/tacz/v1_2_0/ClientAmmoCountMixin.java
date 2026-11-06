package com.mafuyu404.taczaddon.mixin.tacz.v1_2_0;

import com.mafuyu404.taczaddon.client.ammo.BackpackHudState;
import com.mafuyu404.taczaddon.compat.beyond.BeyondAmmoClientCompat;

import com.tacz.guns.api.entity.IGunOperator;
import com.tacz.guns.api.item.IGun;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Shared by the 1.2.0 HUD, sight counters and inventory-fed firing checks. */
@Pseudo
@Mixin(targets = "com.tacz.guns.client.gameplay.ClientAmmoCount", remap = false)
public class ClientAmmoCountMixin {
    @Redirect(method = "get(Lnet/minecraft/client/player/LocalPlayer;Lnet/minecraft/world/item/ItemStack;Lcom/tacz/guns/api/item/IGun;)I",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/player/LocalPlayer;getInventory()Lnet/minecraft/world/entity/player/Inventory;",
                    remap = true), require = 1)
    private static Inventory taczaddon$ammoInventory(LocalPlayer player) {
        return BackpackHudState._virtualInventory != null
                ? BackpackHudState._virtualInventory : player.getInventory();
    }

    @Inject(method = "get(Lnet/minecraft/client/player/LocalPlayer;Lnet/minecraft/world/item/ItemStack;Lcom/tacz/guns/api/item/IGun;)I",
            at = @At("RETURN"), cancellable = true, require = 1)
    private static void taczaddon$networkAmmo(LocalPlayer player, ItemStack stack, IGun gun,
                                            CallbackInfoReturnable<Integer> cir) {
        if (gun.useDummyAmmo(stack) || !IGunOperator.fromLivingEntity(player).needCheckAmmo()) return;
        int network = BeyondAmmoClientCompat.confirmedAmmoCount(stack);
        if (network > 0) {
            cir.setReturnValue((int) Math.min(9999L, (long) cir.getReturnValueI() + network));
        }
    }
}
