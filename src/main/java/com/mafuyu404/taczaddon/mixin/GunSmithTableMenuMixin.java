package com.mafuyu404.taczaddon.mixin;

import com.tacz.guns.inventory.GunSmithTableMenu;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = GunSmithTableMenu.class, remap = false)
public class GunSmithTableMenuMixin {
    // All crafting now uses the validated addon request/transaction. Prevent a
    // native packet from reaching TaCZ's separate nontransactional extraction.
    @Inject(method = "doCraft", at = @At("HEAD"), cancellable = true, require = 1)
    private void taczaddon$bridgeCraft(ResourceLocation recipe, Player player, CallbackInfo ci) {
        ci.cancel();
    }
}
