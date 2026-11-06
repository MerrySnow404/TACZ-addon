package com.mafuyu404.taczaddon.mixin;

import com.mafuyu404.taczaddon.refit.VirtualAttachmentData;

import net.minecraft.world.item.ItemStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Native official UI and packet handlers must also retire addon virtual attachments. */
@Pseudo
@Mixin(targets = "com.tacz.guns.util.VirtualOemAttachment", remap = false)
public class OfficialVirtualAttachmentMixin {
    @Inject(method = "isMarked", at = @At("RETURN"), cancellable = true, require = 1)
    private static void taczaddon$recognizeBothVirtualMarkers(
            ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValueZ() && VirtualAttachmentData.isVirtual(stack))
            cir.setReturnValue(true);
    }
}
