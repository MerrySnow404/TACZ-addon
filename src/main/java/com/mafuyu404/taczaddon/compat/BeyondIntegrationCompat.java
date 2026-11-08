package com.mafuyu404.taczaddon.compat;

import com.mafuyu404.taczaddon.common.AmmoConsumptionOrchestrator.ConsumptionOutcome;
import com.mafuyu404.taczaddon.common.AmmoConsumptionOrchestrator.IncompleteConsumptionException;
import com.mafuyu404.taczaddon.compat.tacz.TaczBinaryProbe;
import com.mafuyu404.taczaddon.compat.tacz.TaczFeature;
import com.mafuyu404.taczaddon.compat.tacz.contract.ClassContract;
import com.mafuyu404.taczaddon.compat.tacz.contract.FeatureContract;
import com.mafuyu404.taczaddon.compat.tacz.contract.MethodContract;
import com.mojang.logging.LogUtils;
import com.tacz.guns.api.item.gun.AbstractGunItem;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.items.wrapper.PlayerMainInvWrapper;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.IntSupplier;

/**
 * Deliberately narrow compatibility bridge for Beyond Integration.
 *
 * <p>Two consumption paths are supported, preferred in this order:
 * <ol>
 *   <li><b>Direct BI API</b> ({@code TaczAmmoExtractor#tryConsumeFromAll}) — returns the exact
 *       consumed amount, no need to infer state from an opaque TaCZ hook.</li>
 *   <li><b>TaCZ hook fallback</b> — BI augments {@code AbstractGunItem#findAndExtractInventoryAmmo}
 *       at RETURN and identifies player-backed extraction through {@code PlayerMainInvWrapper};
 *       one player-main pass lets that hook consume remaining network ammo before our backpack
 *       fallback.</li>
 * </ol>
 *
 * <p>Installed and usable stay separate: this bridge needs both the Beyond Integration mod and
 * at least one of the two capabilities above. Capabilities are probed by class shape, not by
 * mod version.
 */
public final class BeyondIntegrationCompat {
    private static final String MOD_ID = "beyond_integration";
    private static volatile boolean linkageBroken;
    private static volatile Boolean biApiPresent;
    private static volatile Boolean taczHookPresent;
    private static final AtomicBoolean LINKAGE_WARNING_LOGGED = new AtomicBoolean();

    private BeyondIntegrationCompat() {
    }

    public static boolean isInstalled() {
        ModList modList = ModList.get();
        return modList != null && modList.isLoaded(MOD_ID);
    }

    /**
     * Any supported consumption capability present (direct BI API preferred, TaCZ hook fallback).
     *
     * <p>The hook is not part of the other TaCZ feature contracts, so it is probed explicitly here
     * instead of being inferred from the TaCZ version.
     */
    public static boolean isSupported() {
        return isBiApiPresent() || isTaczHookPresent();
    }

    /** BI's stable API shape: {@code TaczAmmoExtractor#tryConsumeFromAll(ServerPlayer, ItemStack, int)}. */
    private static boolean isBiApiPresent() {
        Boolean cached = biApiPresent;
        if (cached != null) {
            return cached;
        }
        boolean present = TaczBinaryProbe.inspect(
                new FeatureContract(
                        TaczFeature.BACKPACK_AMMO_QUERY,
                        "beyond-integration-api",
                        new ClassContract(
                                "com.solr98.beyondintegration.feature.ammo.tacz.TaczAmmoExtractor"
                        ).withMethods(new MethodContract(
                                "tryConsumeFromAll",
                                "(Lnet/minecraft/server/level/ServerPlayer;"
                                        + "Lnet/minecraft/world/item/ItemStack;I)I"
                        ))
                )
        ).passed();
        biApiPresent = present;
        return present;
    }

    /** Legacy TaCZ hook shape (used only when the direct BI API is absent). */
    private static boolean isTaczHookPresent() {
        Boolean cached = taczHookPresent;
        if (cached != null) {
            return cached;
        }
        boolean present = TaczBinaryProbe.inspect(
                new FeatureContract(
                        TaczFeature.BACKPACK_AMMO_QUERY,
                        "beyond-integration-hook",
                        new ClassContract(
                                "com.tacz.guns.api.item.gun.AbstractGunItem"
                        ).withMethods(new MethodContract(
                                "findAndExtractInventoryAmmo",
                                "(Lnet/minecraftforge/items/IItemHandler;"
                                        + "Lnet/minecraft/world/item/"
                                        + "ItemStack;I)I"
                        ))
                )
        ).passed();
        taczHookPresent = present;
        return present;
    }

    public static boolean isUsable() {
        return isInstalled() && isSupported() && !linkageBroken;
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
            ItemStack gunStack,
            int requested
    ) {
        if (!isUsable()
                || player == null
                || gun == null
                || gunStack == null
                || gunStack.isEmpty()
                || requested <= 0) {
            return ConsumptionOutcome.confirmed(0);
        }

        if (isBiApiPresent()) {
            return ConsumptionOutcome.confirmed(
                    runGuarded(requested, () ->
                            BeyondAmmoApi.tryConsumeFromAll(player, gunStack, requested))
            );
        }

        return ConsumptionOutcome.confirmed(
                runGuarded(requested, () -> {
                    PlayerMainInvWrapper playerMain =
                            new PlayerMainInvWrapper(
                                    player.getInventory()
                            );

                    return gun.findAndExtractInventoryAmmo(
                            playerMain,
                            gunStack,
                            requested
                    );
                })
        );
    }

    static int runGuarded(int requested, IntSupplier operation) {
        if (linkageBroken || requested <= 0) {
            return 0;
        }
        try {
            return clampConsumed(requested, operation.getAsInt());
        } catch (LinkageError error) {
            linkageBroken = true;
            if (LINKAGE_WARNING_LOGGED.compareAndSet(false, true)) {
                LogUtils.getLogger().warn(
                        "[TACZ-addon] Beyond Integration ammo bridge unavailable; disabled for this session. "
                                + "Current supplemental consumption stopped because partial extraction is unknown", error);
            }
            // The external path may already have changed inventory/network state.
            // Do not claim zero consumption and trigger another source for the same deficit.
            throw new IncompleteConsumptionException(error);
        }
    }

    private static int clampConsumed(
            int requested,
            int consumed
    ) {
        if (requested <= 0) {
            return 0;
        }
        return Math.max(0, Math.min(requested, consumed));
    }
}
