package com.mafuyu404.taczaddon.ammo;

import static com.mafuyu404.taczaddon.ammo.AmmoSlotOperations.*;

import com.mafuyu404.taczaddon.ammo.AmmoConsumptionOrchestrator.ConsumptionOutcome;
import com.mafuyu404.taczaddon.compat.CreateStorageCompat;
import com.mafuyu404.taczaddon.compat.CuriosCompat;
import com.mafuyu404.taczaddon.compat.SophisticatedBackpacksCompat;
import com.mafuyu404.taczaddon.inventory.VirtualInventory;
import com.mojang.logging.LogUtils;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;

import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.function.Predicate;

import javax.annotation.Nullable;

public final class BackpackAmmoService {
    private static final Logger LOGGER = LogUtils.getLogger();

    private BackpackAmmoService() {}

    public static boolean hasCompatibleAmmo(
            Player player, ItemStack gunStack, @Nullable IItemHandler vanillaHandler) {
        if (player == null || gunStack == null || gunStack.isEmpty()) {
            return false;
        }

        boolean foundInBackpack =
                SophisticatedBackpacksCompat.visitInventoryBackpacks(
                        player, handler -> containsCompatibleAmmo(handler, gunStack));
        if (foundInBackpack) {
            return true;
        }

        if (CreateStorageCompat.visitBackpacks(
                player, backpack -> containsCompatibleAmmo(backpack, gunStack))) {
            return true;
        }

        if (CuriosCompat.visitHandlers(
                player, curios -> containsCompatibleAmmo(curios, gunStack))) {
            return true;
        }

        IItemHandler handler = vanillaHandler;
        if (handler == null) {
            handler = player.getCapability(Capabilities.ItemHandler.ENTITY, null);
        }
        return handler != null && containsCompatibleAmmo(handler, gunStack);
    }

    /** Feeds backpack item handlers to the consumption loop. */
    @FunctionalInterface
    public interface BackpackVisitor {
        void visit(Predicate<IItemHandler> visitor);
    }

    /**
     * Consumes supplemental ammo from Sophisticated Backpacks, then Create Storage backpacks.
     *
     * <p>The confirmed amount is recorded as soon as one slot has been extracted, so a later slot
     * failure never erases earlier progress. A linkage failure means the external API changed under
     * us, so the request stops instead of being retried through another source.
     */
    public static ConsumptionOutcome consumeBackpackAmmo(
            ServerPlayer player, ItemStack gunStack, int requested) {
        if (player == null || gunStack == null || gunStack.isEmpty() || requested <= 0) {
            return ConsumptionOutcome.confirmed(0);
        }

        return consumeThroughHandlers(
                requested,
                gunStack,
                visitor -> {
                    if (!SophisticatedBackpacksCompat.mutateInventoryBackpacks(player, visitor)) {
                        CreateStorageCompat.mutateBackpacks(player, visitor);
                    }
                });
    }

    /**
     * The real consumption loop over backpack handlers.
     *
     * <p>Tests drive this method with injected handlers; production drives it through the
     * Sophisticated Backpacks mutation pass. A handler that fails after an earlier handler already
     * extracted rounds stops the request while keeping those rounds recorded.
     */
    static ConsumptionOutcome consumeThroughHandlers(
            int requested, ItemStack gunStack, BackpackVisitor visitor) {
        if (requested <= 0 || gunStack == null || gunStack.isEmpty() || visitor == null) {
            return ConsumptionOutcome.confirmed(0);
        }

        int[] consumed = {0};

        try {
            visitor.visit(
                    handler -> {
                        if (consumed[0] >= requested) {
                            return true;
                        }

                        int remaining = requested - consumed[0];

                        int extracted = extractCompatibleAmmoDirectly(handler, gunStack, remaining);

                        consumed[0] += clampConsumed(remaining, extracted);

                        return consumed[0] >= requested;
                    });
        } catch (PartialLinkageMutation failure) {
            /*
             * Slots completed before the failing slot are known. The failing
             * handler operation itself may have mutated before losing linkage,
             * therefore the total is only a confirmed lower bound.
             */
            int remaining = Math.max(0, requested - consumed[0]);

            consumed[0] += clampConsumed(remaining, failure.confirmed());

            LOGGER.warn(
                    "[TACZ-addon/AmmoFallback] backpack linkage failed after "
                            + "{} confirmed rounds; the current mutation amount "
                            + "is unknown, stopping this request",
                    consumed[0],
                    failure);

            return ConsumptionOutcome.stoppedUnknown().withConsumed(consumed[0]);
        } catch (PartialRuntimeMutation failure) {
            int remaining = Math.max(0, requested - consumed[0]);

            consumed[0] += clampConsumed(remaining, failure.confirmed());

            LOGGER.warn(
                    "[TACZ-addon/AmmoFallback] backpack handler failed after "
                            + "{} confirmed rounds; the current mutation amount "
                            + "is unknown, stopping this request",
                    consumed[0],
                    failure);

            return ConsumptionOutcome.stoppedUnknown().withConsumed(consumed[0]);
        } catch (LinkageError linkageError) {
            /*
             * Failure happened outside the slot-level adapter. Previous
             * confirmed rounds remain valid, but this external operation cannot
             * prove that nothing else changed.
             */
            LOGGER.warn(
                    "[TACZ-addon/AmmoFallback] backpack integration lost "
                            + "linkage after {} confirmed rounds",
                    consumed[0],
                    linkageError);

            return ConsumptionOutcome.stoppedUnknown().withConsumed(consumed[0]);
        } catch (RuntimeException exception) {
            /*
             * At this point slot-level mutation failures have already been
             * wrapped above. A generic runtime failure therefore normally comes
             * from traversal/finalisation after the recorded extractions.
             *
             * Keep the confirmed amount, but stop the request.
             */
            LOGGER.warn(
                    "[TACZ-addon/AmmoFallback] backpack ammo finalisation "
                            + "failed after {} confirmed rounds",
                    consumed[0],
                    exception);

            return consumed[0] > 0
                    ? ConsumptionOutcome.stoppedConfirmed(consumed[0])
                    : ConsumptionOutcome.stoppedUnknown();
        }

        return ConsumptionOutcome.confirmed(consumed[0]);
    }

    public static IItemHandler createQueryHandler(
            Player player, @Nullable IItemHandler vanillaHandler) {
        ArrayList<ItemStack> allItems = new ArrayList<>();
        CreateStorageCompat.visitBackpacks(
                player,
                backpack -> {
                    for (int slot = 0; slot < backpack.getSlots(); slot++) {
                        ItemStack stack = backpack.getStackInSlot(slot);
                        if (!stack.isEmpty()) allItems.add(stack.copy());
                    }
                    return false;
                });
        SophisticatedBackpacksCompat.visitInventoryBackpacks(
                player,
                handler -> {
                    for (int index = 0; index < handler.getSlots(); index++) {
                        ItemStack stack = handler.getStackInSlot(index);
                        if (!stack.isEmpty()) {
                            allItems.add(stack.copy());
                        }
                    }
                    return false;
                });

        CuriosCompat.visitHandlers(
                player,
                curios -> {
                    for (int slot = 0; slot < curios.getSlots(); slot++) {
                        ItemStack stack = curios.getStackInSlot(slot);
                        if (!stack.isEmpty()) allItems.add(stack.copy());
                    }
                    return false;
                });

        IItemHandler handler = vanillaHandler;
        if (handler == null && player != null) {
            handler = player.getCapability(Capabilities.ItemHandler.ENTITY, null);
        }
        if (handler != null) {
            for (int index = 0; index < handler.getSlots(); index++) {
                allItems.add(handler.getStackInSlot(index));
            }
        }

        if (player == null) {
            /*
             * Aggregate-only view: without a player there is no inventory
             * backing a VirtualInventory, and the query only ever reads.
             */
            return new AggregateQueryHandler(allItems);
        }

        VirtualInventory inventory = new VirtualInventory(allItems.size(), player);
        for (int index = 0; index < allItems.size(); index++) {
            inventory.setItem(index, allItems.get(index));
        }
        return new net.neoforged.neoforge.items.wrapper.InvWrapper(inventory);
    }
}
