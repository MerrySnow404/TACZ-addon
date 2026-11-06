package com.mafuyu404.taczaddon.mixin.tacz.v1_1_8;

import com.mafuyu404.taczaddon.ammo.BackpackAmmoService;

import com.tacz.guns.client.animation.statemachine.GunAnimationStateContext;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import java.util.function.Function;

@Mixin(value = GunAnimationStateContext.class, remap = false)
public class GunAnimationStateContextMixin {
    @Shadow
    private ItemStack currentGunItem;

    @ModifyArg(
            method = "hasAmmoToConsume()Z",
            at = @At(
                    value = "INVOKE",
                    target =
                            "Lcom/tacz/guns/client/animation/"
                                    + "statemachine/GunAnimationStateContext;"
                                    + "processCameraEntity("
                                    + "Ljava/util/function/Function;)"
                                    + "Ljava/util/Optional;",
                    remap = false
            ),
            index = 0,
            remap = false,
            require = 1
    )
    private Function<Entity, Boolean> taczaddon$includeBackpackAmmo(
            Function<Entity, Boolean> original
    ) {
        return entity -> {
            if (!(entity instanceof LocalPlayer player)) {
                return original.apply(entity);
            }
            return BackpackAmmoService.hasCompatibleAmmo(
                    player,
                    this.currentGunItem,
                    null
            );
        };
    }
}
