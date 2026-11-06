package com.mafuyu404.taczaddon.mixin.tacz.v1_1_8;

import com.mafuyu404.taczaddon.ammo.BackpackAmmoService;

import com.tacz.guns.api.item.gun.AbstractGunItem;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = AbstractGunItem.class, remap = false)
public class AbstractGunItemMixin {
    @Redirect(
            method =
                    "canReload(Lnet/minecraft/world/entity/"
                            + "LivingEntity;Lnet/minecraft/world/item/"
                            + "ItemStack;)Z",
            at = @At(
                    value = "INVOKE",
                    target =
                            "Lnet/minecraft/world/entity/LivingEntity;"
                                    + "getCapability(Lnet/minecraftforge/common/"
                                    + "capabilities/Capability;"
                                    + "Lnet/minecraft/core/Direction;)"
                                    + "Lnet/minecraftforge/common/util/"
                                    + "LazyOptional;",
                    remap = false
            ),
            remap = false,
            require = 1
    )
    private <T> LazyOptional<T> taczaddon$checkBackpackAmmos(LivingEntity instance, Capability<T> capability, Direction facing) {
        if (!(instance instanceof Player player) || capability != ForgeCapabilities.ITEM_HANDLER) {
            return instance.getCapability(capability, facing);
        }

        net.minecraftforge.items.IItemHandler vanilla = instance
                .getCapability(
                        ForgeCapabilities.ITEM_HANDLER,
                        facing
                )
                .orElse(null);
        return ForgeCapabilities.ITEM_HANDLER.orEmpty(
                capability,
                LazyOptional.of(() ->
                        BackpackAmmoService.createQueryHandler(
                                player,
                                vanilla
                        )
                )
        );
    }
}
