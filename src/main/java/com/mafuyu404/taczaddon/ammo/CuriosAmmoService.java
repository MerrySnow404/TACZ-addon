package com.mafuyu404.taczaddon.ammo;

import com.mafuyu404.taczaddon.ammo.api.ConsumptionOutcome;
import com.mafuyu404.taczaddon.compat.curios.CuriosCompat;

import com.mojang.logging.LogUtils;
import com.tacz.guns.api.DefaultAssets;
import com.tacz.guns.api.item.IAmmo;
import com.tacz.guns.api.item.IAmmoBox;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.IItemHandlerModifiable;
import org.slf4j.Logger;

public final class CuriosAmmoService {
    private static final Logger LOGGER = LogUtils.getLogger();

    private CuriosAmmoService() {}

    /**
     * Consumes supplemental ammo from Curios slots.
     *
     * <p>Slots that were already committed stay recorded when a later handler
     * loses linkage, and the request stops instead of continuing elsewhere.
     */
    public static ConsumptionOutcome consumeAmmo(
            ServerPlayer player,
            ItemStack gun,
            int requested
    ) {
        if (player == null
                || gun == null
                || gun.isEmpty()
                || requested <= 0) {
            return ConsumptionOutcome.confirmed(0);
        }

        int[] consumed = {0};

        try {
            CuriosCompat.mutateHandlers(
                    player,
                    handler -> {
                        if (consumed[0] >= requested) {
                            return true;
                        }

                        consumeHandler(
                                handler,
                                gun,
                                requested,
                                consumed
                        );

                        return consumed[0] >= requested;
                    }
            );
        } catch (LinkageError linkageError) {
            LOGGER.warn(
                    "[TACZ-addon/AmmoFallback] Curios lost linkage after "
                            + "{} confirmed rounds; stopping the request",
                    consumed[0],
                    linkageError
            );

            return stoppedUnknownFor(
                    consumed[0]
            );
        } catch (RuntimeException exception) {
            LOGGER.warn(
                    "[TACZ-addon/AmmoFallback] Curios ammo mutation failed "
                            + "after {} confirmed rounds; stopping the request",
                    consumed[0],
                    exception
            );

            return stoppedUnknownFor(
                    consumed[0]
            );
        }

        return ConsumptionOutcome.confirmed(
                consumed[0]
        );
    }

    static int consumeHandler(IItemHandler handler, ItemStack gun, int requested) {
        int[] consumed = {0};
        consumeHandler(handler, gun, requested, consumed);
        return consumed[0];
    }

    /**
     * Outcome-reporting variant of one Curios handler consumption.
     *
     * <p>A handler that throws after already committing slots keeps the
     * confirmed amount and stops the request; a linkage error never silently
     * degrades into a normal zero.
     */
    public static ConsumptionOutcome consumeHandlerOutcome(
            IItemHandler handler,
            ItemStack gun,
            int requested
    ) {
        int[] consumed = {0};

        try {
            consumeHandler(
                    handler,
                    gun,
                    requested,
                    consumed
            );
        } catch (LinkageError linkageError) {
            LOGGER.warn(
                    "[TACZ-addon/AmmoFallback] Curios handler lost linkage "
                            + "after {} confirmed rounds",
                    consumed[0],
                    linkageError
            );

            return stoppedUnknownFor(
                    consumed[0]
            );
        } catch (RuntimeException exception) {
            LOGGER.warn(
                    "[TACZ-addon/AmmoFallback] Curios handler mutation failed "
                            + "after {} confirmed rounds",
                    consumed[0],
                    exception
            );

            return stoppedUnknownFor(
                    consumed[0]
            );
        }

        return ConsumptionOutcome.confirmed(
                consumed[0]
        );
    }

    private static ConsumptionOutcome stoppedUnknownFor(
            int consumed
    ) {
        return ConsumptionOutcome
                .stoppedUnknown()
                .withConsumed(Math.max(0, consumed));
    }

    private static void consumeHandler(IItemHandler handler, ItemStack gun, int requested, int[] consumed) {
        if (handler == null || gun == null || gun.isEmpty()) return;
        for (int slot = 0; slot < handler.getSlots() && consumed[0] < requested; slot++) {
            ItemStack snapshot = handler.getStackInSlot(slot).copy();
            if (snapshot.isEmpty()) continue;
            int remaining = requested - consumed[0];
            if (snapshot.getItem() instanceof IAmmo ammo && ammo.isAmmoOfGun(gun, snapshot)) {
                ItemStack extracted = handler.extractItem(slot, remaining, false);
                consumed[0] += BackpackAmmoService.clampConsumed(remaining, extracted.getCount());
            } else if (snapshot.getItem() instanceof IAmmoBox box && box.isAmmoBoxOfGun(gun, snapshot)
                    && handler instanceof IItemHandlerModifiable modifiable) {
                int count = Math.max(0, box.getAmmoCount(snapshot));
                int amount = Math.min(count, remaining);
                if (amount == 0) continue;
                box.setAmmoCount(snapshot, count - amount);
                if (count == amount) box.setAmmoId(snapshot, DefaultAssets.EMPTY_AMMO_ID);
                modifiable.setStackInSlot(slot, snapshot);
                consumed[0] += amount;
            }
            // Generic handlers cannot guarantee a safe ammo-box writeback; leave that slot untouched.
        }
    }
}
