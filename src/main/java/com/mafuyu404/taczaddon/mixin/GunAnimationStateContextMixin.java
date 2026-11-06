package com.mafuyu404.taczaddon.mixin;

import com.mafuyu404.taczaddon.compat.CuriosCompat;
import com.mafuyu404.taczaddon.compat.SophisticatedBackpacksCompat;
import com.tacz.guns.api.item.IAmmo;
import com.tacz.guns.api.item.IAmmoBox;
import com.tacz.guns.client.animation.statemachine.GunAnimationStateContext;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Lets the client gun animation state machine see ammo stored in addon extra storage (inventory
 * backpacks / Curios).
 *
 * <p>Version-bound on the stable source method {@code hasAmmoToConsume()Z} (previously a redirect
 * inside the javac synthetic lambda {@code lambda$hasAmmoToConsume$8}). TaCZ's own logic (remote
 * operators, dummy ammo, the camera entity's inventory handler) keeps running unchanged; the addon
 * only supplements the result when TaCZ already returned {@code false}.
 *
 * <p>The addon scan deliberately excludes the player's own ENTITY item handler: TaCZ's native
 * {@code hasAmmoToConsume} already scans it, so re-scanning it here would be redundant.
 */
@Mixin(value = GunAnimationStateContext.class, remap = false)
public class GunAnimationStateContextMixin {

    @Shadow private ItemStack currentGunItem;

    @Inject(
            method = "hasAmmoToConsume()Z",
            at = @At("RETURN"),
            cancellable = true,
            remap = false,
            require = 1)
    private void taczaddon$includeBackpackAndCuriosAmmo(CallbackInfoReturnable<Boolean> cir) {
        if (Boolean.TRUE.equals(cir.getReturnValue())) {
            return;
        }

        LocalPlayer player = Minecraft.getInstance().player;

        if (player == null || currentGunItem == null || currentGunItem.isEmpty()) {
            return;
        }

        if (taczaddon$hasAmmoInExtraStorage(player)) {
            cir.setReturnValue(true);
        }
    }

    @Unique
    private boolean taczaddon$hasAmmoInExtraStorage(LocalPlayer player) {
        boolean[] found = {false};

        SophisticatedBackpacksCompat.forEachInventoryBackpackHandler(
                player,
                handler -> {
                    if (taczaddon$containsAmmoForGun(handler)) {
                        found[0] = true;
                    }
                });

        if (found[0]) {
            return true;
        }

        CuriosCompat.forEachCuriosHandler(
                player,
                handler -> {
                    if (taczaddon$containsAmmoForGun(handler)) {
                        found[0] = true;
                    }
                });

        return found[0];
    }

    /**
     * Mirrors TaCZ's own ammo check ({@code lambda$hasAmmoToConsume$7} logic in the current jar):
     * any {@link IAmmo} that fits the gun or any {@link IAmmoBox} of the gun counts as consumable
     * ammo.
     */
    @Unique
    private boolean taczaddon$containsAmmoForGun(IItemHandler handler) {
        for (int slot = 0; slot < handler.getSlots(); slot++) {
            ItemStack stack = handler.getStackInSlot(slot);

            if (stack.isEmpty()) {
                continue;
            }

            Item item = stack.getItem();

            if (item instanceof IAmmo ammo && ammo.isAmmoOfGun(currentGunItem, stack)) {
                return true;
            }

            if (item instanceof IAmmoBox ammoBox && ammoBox.isAmmoBoxOfGun(currentGunItem, stack)) {
                return true;
            }
        }

        return false;
    }
}
