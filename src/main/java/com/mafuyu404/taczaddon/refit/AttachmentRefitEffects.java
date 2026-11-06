package com.mafuyu404.taczaddon.refit;

import com.mafuyu404.taczaddon.compat.TaczOemCompat;
import com.mafuyu404.taczaddon.inventory.MainInventoryTransaction;
import com.mafuyu404.taczaddon.network.NetworkHandler;
import com.mafuyu404.taczaddon.refit.network.RefreshRefitScreenPacket;
import com.mojang.logging.LogUtils;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.resource.modifier.AttachmentPropertyManager;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import org.slf4j.Logger;

final class AttachmentRefitEffects {
    private static final Logger LOGGER = LogUtils.getLogger();

    /**
     * Shared TaCZ side effects after a successful install/unload. Only invoked once the transaction
     * has fully committed.
     */
    static void postChange(
            ServerPlayer player,
            ItemStack gunStack,
            AttachmentType type,
            Inventory inventory,
            ItemStack beforeRefit) {
        TaczOemCompat.triggerRefit(player, beforeRefit, gunStack);
        AttachmentPropertyManager.postChangeEvent(player, gunStack);

        if (type == AttachmentType.EXTENDED_MAG) {
            IGun gun = IGun.getIGunOrNull(gunStack);

            if (gun != null) {
                gun.dropAllAmmo(player, gunStack);
            }
        }

        inventory.setChanged();
        player.inventoryMenu.broadcastChanges();

        NetworkHandler.sendToClient(player, new RefreshRefitScreenPacket(true));
    }

    /**
     * Attempts a transaction rollback and logs the true recovery outcome.
     *
     * <p>The {@link MainInventoryTransaction} snapshot is the only rollback authority: it restores
     * all 36 main slots, including the gun slot with its complete ItemStack. No stale gun reference
     * is touched afterwards.
     *
     * <p>Returns true only when rollback completed. On rollback failure the situation is logged as
     * CRITICAL (never as "nothing lost"), and the caller must not pretend recovery succeeded.
     */
    static boolean safeRollback(
            MainInventoryTransaction transaction, String operation, Object detail) {
        try {
            transaction.rollback();

            LOGGER.error(
                    "[taczaddon] Transaction failed during {} (detail={}); "
                            + "rollback completed. Main inventory (including "
                            + "the gun slot) restored to the pre-transaction "
                            + "state. Nothing was dropped or duplicated.",
                    operation,
                    detail);

            return true;
        } catch (RuntimeException rollbackException) {
            LOGGER.error(
                    "[taczaddon] CRITICAL: transaction failed during {} "
                            + "(detail={}) and rollback itself also failed. "
                            + "Player inventory may require manual "
                            + "inspection.",
                    operation,
                    detail,
                    rollbackException);

            return false;
        }
    }

    /**
     * Unified recovery handling shared by the install and unload transactions.
     *
     * <p>On rollback success the authoritative container state is synced and the refit screen is
     * refreshed. On rollback failure a best-effort full-state sync is still attempted so the client
     * cannot keep a stale or predicted view of a possibly partially-mutated server state.
     */
    static void handleRollbackOutcome(
            MainInventoryTransaction transaction,
            ServerPlayer player,
            Inventory inventory,
            String operation,
            Object detail) {
        boolean rollbackSucceeded = safeRollback(transaction, operation, detail);

        if (rollbackSucceeded) {
            syncAfterRollback(player, inventory);
        } else {
            bestEffortSyncAfterRecoveryFailure(player, inventory, operation, detail);
        }
    }

    /**
     * Synchronizes a successfully rolled-back transaction to the client.
     *
     * <p>Only called for ROLLBACK_SUCCESS; see {@link
     * #bestEffortSyncAfterRecoveryFailure(ServerPlayer, Inventory, String, Object)} for the
     * rollback-failure path.
     *
     * <p>Order: container sync first, then the refit-screen refresh, so the client re-inits its GUI
     * from the restored server state instead of a stale intermediate one. No success side effects
     * ({@code postChangeEvent} / ammo drop) are executed here: the gun is back in its old state and
     * must not be treated as a successful attachment transaction.
     */
    static void syncAfterRollback(ServerPlayer player, Inventory inventory) {
        inventory.setChanged();
        player.inventoryMenu.broadcastChanges();

        NetworkHandler.sendToClient(player, new RefreshRefitScreenPacket(true));
    }

    /**
     * Best-effort authoritative sync after a ROLLBACK FAILURE.
     *
     * <p>The server is the only authority at this point, but the rollback may have failed or
     * partially restored state. A full-state container sync ({@code broadcastFullState()},
     * available in MC 1.21.1) is preferred over a change-diff sync so the client cannot keep a
     * stale/predicted view. Every step is guarded so a third-layer recovery exception cannot escape
     * the network task; the transaction result stays {@code INTERNAL_FAILURE}.
     *
     * <p>The refit screen is only refreshed if the selected slot still holds a gun, so the
     * client-side attachment cache refresh never runs against a possibly corrupted/half-restored
     * gun state.
     */
    static void bestEffortSyncAfterRecoveryFailure(
            ServerPlayer player, Inventory inventory, String operation, Object detail) {
        try {
            inventory.setChanged();
            player.inventoryMenu.broadcastFullState();
        } catch (RuntimeException syncException) {
            LOGGER.error(
                    "[taczaddon] CRITICAL: recovery sync also failed after "
                            + "a failed rollback during {} (detail={}). The "
                            + "client may not reflect the server inventory "
                            + "state.",
                    operation,
                    detail,
                    syncException);
            return;
        }

        try {
            if (IGun.getIGunOrNull(inventory.getSelected()) != null) {
                NetworkHandler.sendToClient(player, new RefreshRefitScreenPacket(true));
            }
        } catch (RuntimeException refreshException) {
            LOGGER.error(
                    "[taczaddon] CRITICAL: refit screen refresh also failed "
                            + "after a failed rollback during {} (detail={}).",
                    operation,
                    detail,
                    refreshException);
        }
    }

    static void logInternalFailure(String operation, Object detail, ItemStack remainder) {
        LOGGER.error(
                "[taczaddon] Internal transaction failure during {} "
                        + "(detail={}, remainder={}).",
                operation,
                detail,
                remainder.toString());
    }

    static void logInternalFailure(String operation, Object detail, RuntimeException exception) {
        LOGGER.error(
                "[taczaddon] Internal transaction failure during {} " + "(detail={}).",
                operation,
                detail,
                exception);
    }
}
