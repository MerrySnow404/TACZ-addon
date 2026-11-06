package com.mafuyu404.taczaddon.mixin;

import com.mafuyu404.taczaddon.ammo.client.ExtraAmmoCount;
import com.tacz.guns.api.entity.IGunOperator;
import com.tacz.guns.api.item.IGun;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Official 1.2.0 shares this count between HUD, animation and inventory-feed gameplay. */
@Pseudo
@Mixin(targets = "com.tacz.guns.client.gameplay.ClientAmmoCount", remap = false)
public class ClientAmmoCountMixin {
    @Inject(method = "get", at = @At("RETURN"), cancellable = true, require = 1)
    private static void taczaddon$appendExtraAmmo(
            LocalPlayer player, ItemStack stack, IGun gun, CallbackInfoReturnable<Integer> cir) {
        if (IGunOperator.fromLivingEntity(player).needCheckAmmo() && !gun.useDummyAmmo(stack)) {
            cir.setReturnValue(
                    ExtraAmmoCount.appendForOfficial(player, stack, cir.getReturnValueI()));
        }
    }
}
