package com.mafuyu404.taczaddon.compat;

import com.mafuyu404.taczaddon.ammo.AmmoConsumptionOrchestrator.ConsumptionOutcome;
import com.mafuyu404.taczaddon.ammo.AmmoConsumptionOrchestrator.IncompleteConsumptionException;
import com.tacz.guns.api.item.gun.AbstractGunItem;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;

/** Optional boundary for the NeoForge release's network and terminal consumption paths. */
public final class BeyondIntegrationCompat {
    private static boolean broken;

    private BeyondIntegrationCompat() {}

    public static boolean isUsable() {
        return !broken && ModList.get() != null && ModList.get().isLoaded("beyond_integration");
    }

    public static ConsumptionOutcome consumeThroughTaczInventoryContract(
            ServerPlayer player, AbstractGunItem gun, ItemStack stack, int needed) {
        if (!isUsable() || needed <= 0) return ConsumptionOutcome.confirmed(0);
        try {
            return ConsumptionOutcome.confirmed(
                    Math.max(
                            0,
                            Math.min(
                                    needed,
                                    BeyondAmmoServerCompatInner.consume(
                                            player, gun, stack, needed))));
        } catch (LinkageError | RuntimeException failure) {
            broken = true;
            com.mojang.logging.LogUtils.getLogger()
                    .warn("Beyond ammo bridge stopped after an uncertain mutation", failure);
            throw new IncompleteConsumptionException(failure);
        }
    }
}
