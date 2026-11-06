package com.mafuyu404.taczaddon.mixin;

import com.mafuyu404.taczaddon.ammo.AmmoConsumptionOrchestrator;
import com.mafuyu404.taczaddon.ammo.AmmoConsumptionOrchestrator.ConsumptionOutcome;
import com.mafuyu404.taczaddon.ammo.BackpackAmmoService;
import com.mafuyu404.taczaddon.ammo.CuriosAmmoService;
import com.mafuyu404.taczaddon.compat.BeyondIntegrationCompat;
import com.mafuyu404.taczaddon.compat.CreateStorageCompat;
import com.mafuyu404.taczaddon.compat.CuriosCompat;
import com.mafuyu404.taczaddon.compat.SophisticatedBackpacksCompat;
import com.tacz.guns.api.entity.IGunOperator;
import com.tacz.guns.api.item.gun.AbstractGunItem;
import com.tacz.guns.item.ModernKineticGunScriptAPI;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = ModernKineticGunScriptAPI.class, remap = false, priority = 1100)
public class ModernKineticGunScriptAPIMixin {
    @Shadow private LivingEntity shooter;

    @Shadow private ItemStack itemStack;

    @Shadow private AbstractGunItem abstractGunItem;

    /* Run our RETURN handler before Beyond's default-priority script handler.
     * Its bound-network and terminal paths are invoked explicitly for the remaining amount;
     * setting the final return value then prevents a second network consumption.
     */
    @Inject(
            method = "consumeAmmoFromPlayer(I)I",
            at = @At("RETURN"),
            cancellable = true,
            remap = false,
            require = 1)
    private void taczaddon$consumeBackpackAmmo(
            int neededAmount, CallbackInfoReturnable<Integer> cir) {
        if (neededAmount <= 0
                || this.abstractGunItem == null
                || this.shooter == null
                || this.itemStack == null
                || this.itemStack.isEmpty()) {
            return;
        }

        /*
         * Creative/infinite ammo keeps TaCZ's native semantics: no
         * supplemental source is consulted for such a gun.
         */
        if (AmmoConsumptionOrchestrator.usesNativeVirtualAmmo(
                this.abstractGunItem.useInventoryAmmo(this.itemStack),
                IGunOperator.fromLivingEntity(this.shooter).needCheckAmmo(),
                this.abstractGunItem.useDummyAmmo(this.itemStack))) {
            return;
        }

        /*
         * Cheap capability pre-filter only: an installed but unsupported or
         * ABI-broken integration must not enter this path, and every facade
         * re-checks its own usable state before the backend is called.
         */
        if (!(this.shooter instanceof ServerPlayer player)
                || (!SophisticatedBackpacksCompat.isInstalled()
                        && !CuriosCompat.isInstalled()
                        && !CreateStorageCompat.isUsable())) {
            return;
        }

        AbstractGunItem gun = this.abstractGunItem;
        ItemStack gunStack = this.itemStack;
        boolean beyondActive = BeyondIntegrationCompat.isUsable();
        int consumedSoFar =
                AmmoConsumptionOrchestrator.clampConsumed(neededAmount, cir.getReturnValueI());
        /*
         * One orchestration layer owns the whole TaCZ -> Beyond ->
         * Sophisticated -> Curios priority. Every source receives the real
         * remaining amount and is skipped once the requirement is satisfied,
         * so a finished path is never executed again.
         */
        ConsumptionOutcome outcome =
                AmmoConsumptionOrchestrator.consumeRemaining(
                        neededAmount,
                        consumedSoFar,
                        beyondActive
                                ? remaining ->
                                        BeyondIntegrationCompat.consumeThroughTaczInventoryContract(
                                                player, gun, gunStack, remaining)
                                : remaining -> ConsumptionOutcome.confirmed(0),
                        remaining ->
                                BackpackAmmoService.consumeBackpackAmmo(
                                        player, gunStack, remaining),
                        remaining -> CuriosAmmoService.consumeAmmo(player, gunStack, remaining));
        int finalConsumed = outcome.consumed();

        if (beyondActive || outcome.stoppedAbnormally() || cir.getReturnValueI() != finalConsumed) {
            cir.setReturnValue(finalConsumed);
        }
    }
}
