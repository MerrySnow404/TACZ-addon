package com.mafuyu404.taczaddon.mixin;

import com.tacz.guns.api.item.IGun;
import com.tacz.guns.client.gameplay.LocalPlayerReload;
import com.tacz.guns.client.resource.GunDisplayInstance;
import com.tacz.guns.resource.pojo.data.gun.GunData;

import net.minecraft.world.item.ItemStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = LocalPlayerReload.class, remap = false)
public class LocalPlayerReloadMixin {
    @Inject(
            method = "doReload",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lcom/tacz/guns/api/client/animation/statemachine/LuaAnimationStateMachine;trigger(Ljava/lang/String;)V"))
    private void taczaddon$beforeReloadTrigger(
            IGun iGun,
            GunDisplayInstance display,
            GunData gunData,
            ItemStack mainhandItem,
            CallbackInfo ci) {
        // Reserved hook for reload animation adjustments; current behavior does not cancel TaCZ
        // reloads.
    }
}
