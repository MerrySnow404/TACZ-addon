package com.mafuyu404.taczaddon.compat.beyond;

import com.mafuyu404.taczaddon.ammo.api.ConsumptionOutcome;
import com.mafuyu404.taczaddon.ammo.api.IncompleteConsumptionException;
import com.mafuyu404.taczaddon.compat.tacz.TaczBinaryProbe;
import com.mafuyu404.taczaddon.compat.tacz.contract.ClassContract;
import com.mafuyu404.taczaddon.compat.tacz.contract.FeatureContract;
import com.mafuyu404.taczaddon.compat.tacz.contract.MethodContract;
import com.mafuyu404.taczaddon.compat.tacz.contract.TaczFeature;

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
 * Beyond Integration augments TaCZ
 * AbstractGunItem#findAndExtractInventoryAmmo at RETURN and identifies
 * player-backed extraction through PlayerMainInvWrapper.
 *
 * TACZ-addon normally keeps Sophisticated Backpack mutation outside that
 * extensible TaCZ extraction path. When Beyond Integration is present we
 * expose exactly one player-main extraction pass here so its existing TaCZ
 * compatibility hook can consume the remaining network ammo before our
 * backpack fallback.
 *
 * <p>Installed and usable stay separate: this bridge needs both the Beyond
 * Integration mod and TaCZ's verified
 * {@code AbstractGunItem#findAndExtractInventoryAmmo} hook. The TaCZ side is a
 * capability check, not a mod-presence check.
 */
public final class BeyondIntegrationCompat {
    private static final String MOD_ID = "beyond_integration";
    private static volatile boolean linkageBroken;
    private static volatile Boolean taczHookPresent;
    private static final AtomicBoolean LINKAGE_WARNING_LOGGED = new AtomicBoolean();

    private BeyondIntegrationCompat() {
    }

    public static boolean isInstalled() {
        ModList modList = ModList.get();
        return modList != null && modList.isLoaded(MOD_ID);
    }

    /**
     * TaCZ binary capability this bridge depends on.
     *
     * <p>The hook is not part of the other TaCZ feature contracts, so it is
     * probed explicitly here instead of being inferred from the TaCZ version.
     */
    public static boolean isSupported() {
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
     * One TaCZ main-inventory extraction pass so Beyond Integration's own
     * compatibility hook can consume the remaining network ammo before the
     * backpack fallback.
     *
     * <p>A disabled or already tripped bridge reports a normal zero so the
     * other independent sources still work. A linkage failure throws
     * {@link IncompleteConsumptionException}: the opaque hook may already have
     * changed state, so the orchestrator records the previously confirmed
     * rounds and stops the request.
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
            // The external hook may already have changed inventory/network state.
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
