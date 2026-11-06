package com.mafuyu404.taczaddon.mixin;

import com.mafuyu404.taczaddon.compat.CreateStorageCompat;
import com.mafuyu404.taczaddon.compat.CuriosCompat;
import com.mafuyu404.taczaddon.compat.SophisticatedBackpacksCompat;
import com.mafuyu404.taczaddon.inventory.ReadOnlyCompositeItemHandler;
import com.tacz.guns.item.ModernKineticGunScriptAPI;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.EntityCapability;
import net.neoforged.neoforge.items.IItemHandler;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = ModernKineticGunScriptAPI.class, remap = false)
public class ModernKineticGunScriptAPIHasAmmoMixin {
    @Redirect(
            method = "hasAmmoToConsume",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/world/entity/LivingEntity;getCapability(Lnet/neoforged/neoforge/capabilities/EntityCapability;Ljava/lang/Object;)Ljava/lang/Object;"))
    private Object redirectGetCapability(
            LivingEntity instance, EntityCapability<IItemHandler, ?> capability, Object context) {
        if (!(instance instanceof Player player) || capability != Capabilities.ItemHandler.ENTITY) {
            return instance.getCapability(Capabilities.ItemHandler.ENTITY, null);
        }

        ReadOnlyCompositeItemHandler.Builder builder = ReadOnlyCompositeItemHandler.builder();
        SophisticatedBackpacksCompat.forEachInventoryBackpackHandler(
                player, handler -> builder.addHandler(handler, "inventory_backpack"));

        CreateStorageCompat.visitBackpacks(
                player,
                backpack -> {
                    builder.addHandler(backpack, "create_backpack");
                    return false;
                });

        IItemHandler handler = instance.getCapability(Capabilities.ItemHandler.ENTITY, null);
        if (handler != null) {
            builder.addHandler(handler, "player_inventory");
        }

        CuriosCompat.forEachCuriosHandler(
                player, curiosHandler -> builder.addHandler(curiosHandler, "curios"));

        return builder.buildReadOnly();
    }
}
