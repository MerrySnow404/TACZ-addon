package com.mafuyu404.taczaddon.mixin;

import com.mafuyu404.taczaddon.compat.CreateStorageCompat;
import com.mafuyu404.taczaddon.compat.CuriosCompat;
import com.mafuyu404.taczaddon.compat.SophisticatedBackpacksCompat;
import com.mafuyu404.taczaddon.inventory.ReadOnlyCompositeItemHandler;
import com.tacz.guns.api.item.gun.AbstractGunItem;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.EntityCapability;
import net.neoforged.neoforge.items.IItemHandler;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = AbstractGunItem.class, remap = false)
public class AbstractGunItemMixin {
    // Inventory-feed guns query hasInventoryAmmo before firing instead of canReload.
    @Redirect(
            method = {"canReload", "hasInventoryAmmo"},
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/world/entity/LivingEntity;getCapability(Lnet/neoforged/neoforge/capabilities/EntityCapability;Ljava/lang/Object;)Ljava/lang/Object;"),
            require = 2)
    private Object taczaddon$checkBackpackAmmos(
            LivingEntity instance, EntityCapability<IItemHandler, ?> capability, Object context) {
        if (!(instance instanceof Player player) || capability != Capabilities.ItemHandler.ENTITY) {
            return instance.getCapability(Capabilities.ItemHandler.ENTITY, null);
        }

        ReadOnlyCompositeItemHandler.Builder builder = ReadOnlyCompositeItemHandler.builder();
        SophisticatedBackpacksCompat.forEachInventoryBackpackHandler(
                player, handler -> builder.addHandler(handler, "inventory_backpack"));

        CreateStorageCompat.visitBackpacks(
                player,
                handler -> {
                    builder.addHandler(handler, "create_backpack");
                    return false;
                });

        IItemHandler playerHandler = player.getCapability(Capabilities.ItemHandler.ENTITY, null);
        if (playerHandler != null) {
            builder.addHandler(playerHandler, "player_inventory");
        }

        CuriosCompat.forEachCuriosHandler(player, handler -> builder.addHandler(handler, "curios"));

        return builder.buildReadOnly();
    }
}
