package com.mafuyu404.taczaddon.mixin;

import com.mafuyu404.taczaddon.config.ClientSyncedConfig;
import com.tacz.guns.client.gameplay.LocalPlayerDraw;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(value = LocalPlayerDraw.class, remap = false)
public class LocalPlayerDrawMixin {
    @ModifyVariable(method = "draw", at = @At("STORE"), name = "putAwayTime")
    private long modifyPutAwayTime(long putAwayTime) {
        return ClientSyncedConfig.enableFastSwapGun() ? 0L : putAwayTime;
    }

    @ModifyVariable(method = "draw", at = @At("STORE"), name = "drawTime")
    private long modifyDrawTime(long drawTime) {
        return ClientSyncedConfig.enableFastSwapGun() ? 0L : drawTime;
    }
}
