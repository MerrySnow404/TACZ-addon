package com.mafuyu404.taczaddon.compat;

import com.mafuyu404.taczaddon.common.AmmoConsumptionOrchestrator.ConsumptionOutcome;
import com.mafuyu404.taczaddon.common.AmmoConsumptionOrchestrator.IncompleteConsumptionException;
import com.tacz.guns.api.item.gun.AbstractGunItem;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Optional boundary for the NeoForge release's network and terminal consumption paths.
 *
 * <p>Two consumption paths are supported, preferred in this order:
 * <ol>
 *   <li><b>Direct BI API</b> ({@code TaczAmmoExtractor#tryConsumeFromAll}) — returns the exact
 *       consumed amount, no need to infer state from an opaque TaCZ hook.</li>
 *   <li><b>TaCZ hook fallback</b> — BI augments {@code AbstractGunItem#findAndExtractInventoryAmmo}
 *       at RETURN and identifies player-backed extraction through {@code PlayerMainInvWrapper}.</li>
 * </ol>
 *
 * <p>Installed and usable stay separate: this bridge needs both the Beyond Integration mod and at
 * least one of the two capabilities above. Capabilities are probed by class shape, not by mod
 * version, and the invocation itself goes through {@link BeyondAmmoApi}, so the addon keeps
 * building and starting when BI is absent or ships a different package layout.
 */
public final class BeyondIntegrationCompat {
    private static final String MOD_ID = "beyond_integration";
    private static final String EXTRACTOR_CLASS =
            "com.solr98.beyondintegration.feature.ammo.tacz.TaczAmmoExtractor";
    private static final String CONSUME_DESCRIPTOR =
            "(Lnet/minecraft/server/level/ServerPlayer;"
                    + "Lnet/minecraft/world/item/ItemStack;I)I";

    private static volatile boolean broken;
    private static volatile Boolean biApiPresent;
    private static volatile Boolean taczHookPresent;
    private static final AtomicBoolean LINKAGE_WARNING_LOGGED = new AtomicBoolean();

    private BeyondIntegrationCompat() {
    }

    public static boolean isInstalled() {
        ModList modList = ModList.get();
        return modList != null && modList.isLoaded(MOD_ID);
    }

    /** Any supported consumption capability present (direct BI API preferred, TaCZ hook fallback). */
    public static boolean isSupported() {
        return isBiApiPresent() || isTaczHookPresent();
    }

    public static boolean isUsable() {
        return !broken && isInstalled() && isSupported();
    }

    /** BI's stable API shape: {@code TaczAmmoExtractor#tryConsumeFromAll(ServerPlayer, ItemStack, int)}. */
    private static boolean isBiApiPresent() {
        Boolean cached = biApiPresent;
        if (cached != null) {
            return cached;
        }
        boolean present = ApiShapeProbe.hasMethod(
                ApiShapeProbe.sourceFor(BeyondIntegrationCompat.class),
                EXTRACTOR_CLASS,
                "tryConsumeFromAll",
                CONSUME_DESCRIPTOR
        );
        biApiPresent = present;
        return present;
    }

    /** Legacy TaCZ hook shape (used only when the direct BI API is absent). */
    private static boolean isTaczHookPresent() {
        Boolean cached = taczHookPresent;
        if (cached != null) {
            return cached;
        }
        boolean present = ApiShapeProbe.hasMethod(
                ApiShapeProbe.sourceFor(BeyondIntegrationCompat.class),
                "com.tacz.guns.api.item.gun.AbstractGunItem",
                "findAndExtractInventoryAmmo",
                "(Lnet/neoforged/neoforge/items/IItemHandler;"
                        + "Lnet/minecraft/world/item/ItemStack;I)I"
        );
        taczHookPresent = present;
        return present;
    }

    /**
     * One consumption pass for Beyond Integration: prefers the direct BI API, falls back to the
     * TaCZ main-inventory hook.
     *
     * <p>A disabled or already tripped bridge reports a normal zero so the other independent
     * sources still work. A linkage failure throws {@link IncompleteConsumptionException}: the
     * external path may already have changed state, so the orchestrator records the previously
     * confirmed rounds and stops the request.
     */
    public static ConsumptionOutcome consumeThroughTaczInventoryContract(
            ServerPlayer player,
            AbstractGunItem gun,
            ItemStack stack,
            int needed
    ) {
        if (!isUsable() || needed <= 0) {
            return ConsumptionOutcome.confirmed(0);
        }
        try {
            int consumed = isBiApiPresent()
                    ? BeyondAmmoApi.tryConsumeFromAll(player, stack, needed)
                    : BeyondAmmoServerCompatInner.consume(player, gun, stack, needed);
            return ConsumptionOutcome.confirmed(clampConsumed(needed, consumed));
        } catch (LinkageError | RuntimeException failure) {
            broken = true;
            if (LINKAGE_WARNING_LOGGED.compareAndSet(false, true)) {
                com.mojang.logging.LogUtils.getLogger().warn(
                        "Beyond ammo bridge stopped after an uncertain mutation", failure);
            }
            throw new IncompleteConsumptionException(failure);
        }
    }

    private static int clampConsumed(int requested, int consumed) {
        if (requested <= 0) {
            return 0;
        }
        return Math.max(0, Math.min(requested, consumed));
    }
}
