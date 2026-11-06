package com.mafuyu404.taczaddon.mixin;

import net.minecraft.world.inventory.SmithingMenu;
import net.minecraft.world.item.ItemStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(SmithingMenu.class)
public class SmithingMenuMixin {
    @Inject(method = "canMoveIntoInputSlots", at = @At("RETURN"), cancellable = true)
    private void onIsValidSlot(ItemStack itemStack, CallbackInfoReturnable<Boolean> cir) {

        cir.setReturnValue(true);
    }
}
