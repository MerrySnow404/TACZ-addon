package com.mafuyu404.taczaddon.ammo;

import com.mafuyu404.taczaddon.ammo.api.ConsumptionOutcome;
import com.mafuyu404.taczaddon.compat.createstorage.CreateStorageCompat;
import com.mafuyu404.taczaddon.compat.curios.CuriosCompat;
import com.mafuyu404.taczaddon.compat.sophisticated.SophisticatedBackpacksCompat;
import com.mafuyu404.taczaddon.storage.VirtualInventory;

import com.mojang.logging.LogUtils;
import com.tacz.guns.api.DefaultAssets;
import com.tacz.guns.api.item.IAmmo;
import com.tacz.guns.api.item.IAmmoBox;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.items.IItemHandler;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import javax.annotation.Nullable;

public final class BackpackAmmoService {
    private static final Logger LOGGER = LogUtils.getLogger();

    private BackpackAmmoService() {
    }

    public static boolean hasCompatibleAmmo(
            Player player,
            ItemStack gunStack,
            @Nullable IItemHandler vanillaHandler
    ) {
        if (player == null || gunStack == null || gunStack.isEmpty()) {
            return false;
        }

        boolean foundInBackpack =
                SophisticatedBackpacksCompat.visitInventoryBackpacks(
                        player,
                        handler -> containsCompatibleAmmo(
                                handler,
                                gunStack
                        )
                );
        if (foundInBackpack) {
            return true;
        }

        if (CreateStorageCompat.visitBackpacks(player, backpack -> containsCompatibleAmmo(backpack, gunStack))) {
            return true;
        }

        if (CuriosCompat.visitHandlers(player, curios -> containsCompatibleAmmo(curios, gunStack))) {
            return true;
        }

        IItemHandler handler = vanillaHandler;
        if (handler == null) {
            handler = player.getCapability(
                            ForgeCapabilities.ITEM_HANDLER,
                            null
                    )
                    .orElse(null);
        }
        return handler != null
                && containsCompatibleAmmo(handler, gunStack);
    }

    /**
     * Feeds backpack item handlers to the consumption loop.
     */
    @FunctionalInterface
    public interface BackpackVisitor {
        void visit(Predicate<IItemHandler> visitor);
    }

    private static final class PartialLinkageMutation
            extends LinkageError {
        private final int confirmed;

        private PartialLinkageMutation(
                int confirmed,
                LinkageError cause
        ) {
            super(cause.getMessage());
            this.confirmed = Math.max(0, confirmed);
            initCause(cause);
        }

        private int confirmed() {
            return this.confirmed;
        }
    }

    private static final class PartialRuntimeMutation
            extends RuntimeException {
        private final int confirmed;

        private PartialRuntimeMutation(
                int confirmed,
                RuntimeException cause
        ) {
            super(
                    "Ammo handler failed after a partial mutation",
                    cause
            );
            this.confirmed = Math.max(0, confirmed);
        }

        private int confirmed() {
            return this.confirmed;
        }
    }

    /**
     * Consumes supplemental ammo from Sophisticated Backpacks, then Create Storage backpacks.
     *
     * <p>The confirmed amount is recorded as soon as one slot has been
     * extracted, so a later slot failure never erases earlier progress. A
     * linkage failure means the external API changed under us, so the request
     * stops instead of being retried through another source.
     */
    public static ConsumptionOutcome consumeBackpackAmmo(
            ServerPlayer player,
            ItemStack gunStack,
            int requested
    ) {
        if (player == null
                || gunStack == null
                || gunStack.isEmpty()
                || requested <= 0) {
            return ConsumptionOutcome.confirmed(0);
        }

        return consumeThroughHandlers(
                requested,
                gunStack,
                visitor -> {
                    if (!SophisticatedBackpacksCompat.mutateInventoryBackpacks(player, visitor)) {
                        CreateStorageCompat.mutateBackpacks(player, visitor);
                    }
                }
        );
    }

    /**
     * The real consumption loop over backpack handlers.
     *
     * <p>Tests drive this method with injected handlers; production drives it
     * through the Sophisticated Backpacks mutation pass. A handler that fails
     * after an earlier handler already extracted rounds stops the request
     * while keeping those rounds recorded.
     */
    static ConsumptionOutcome consumeThroughHandlers(
            int requested,
            ItemStack gunStack,
            BackpackVisitor visitor
    ) {
        if (requested <= 0
                || gunStack == null
                || gunStack.isEmpty()
                || visitor == null) {
            return ConsumptionOutcome.confirmed(0);
        }

        int[] consumed = {0};

        try {
            visitor.visit(handler -> {
                if (consumed[0] >= requested) {
                    return true;
                }

                int remaining = requested - consumed[0];

                int extracted = extractCompatibleAmmoDirectly(
                        handler,
                        gunStack,
                        remaining
                );

                consumed[0] += clampConsumed(
                        remaining,
                        extracted
                );

                return consumed[0] >= requested;
            });
        } catch (PartialLinkageMutation failure) {
            /*
             * Slots completed before the failing slot are known. The failing
             * handler operation itself may have mutated before losing linkage,
             * therefore the total is only a confirmed lower bound.
             */
            int remaining = Math.max(
                    0,
                    requested - consumed[0]
            );

            consumed[0] += clampConsumed(
                    remaining,
                    failure.confirmed()
            );

            LOGGER.warn(
                    "[TACZ-addon/AmmoFallback] backpack linkage failed after "
                            + "{} confirmed rounds; the current mutation amount "
                            + "is unknown, stopping this request",
                    consumed[0],
                    failure
            );

            return ConsumptionOutcome
                    .stoppedUnknown()
                    .withConsumed(consumed[0]);
        } catch (PartialRuntimeMutation failure) {
            int remaining = Math.max(
                    0,
                    requested - consumed[0]
            );

            consumed[0] += clampConsumed(
                    remaining,
                    failure.confirmed()
            );

            LOGGER.warn(
                    "[TACZ-addon/AmmoFallback] backpack handler failed after "
                            + "{} confirmed rounds; the current mutation amount "
                            + "is unknown, stopping this request",
                    consumed[0],
                    failure
            );

            return ConsumptionOutcome
                    .stoppedUnknown()
                    .withConsumed(consumed[0]);
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
                    linkageError
            );

            return ConsumptionOutcome
                    .stoppedUnknown()
                    .withConsumed(consumed[0]);
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
                    exception
            );

            return consumed[0] > 0
                    ? ConsumptionOutcome
                    .stoppedConfirmed(consumed[0])
                    : ConsumptionOutcome.stoppedUnknown();
        }

        return ConsumptionOutcome.confirmed(consumed[0]);
    }

    public static IItemHandler createQueryHandler(
            Player player,
            @Nullable IItemHandler vanillaHandler
    ) {
        ArrayList<ItemStack> allItems = new ArrayList<>();
        CreateStorageCompat.visitBackpacks(player, backpack -> {
            for (int slot = 0; slot < backpack.getSlots(); slot++) {
                ItemStack stack = backpack.getStackInSlot(slot);
                if (!stack.isEmpty()) allItems.add(stack.copy());
            }
            return false;
        });
        SophisticatedBackpacksCompat.visitInventoryBackpacks(
                player,
                handler -> {
                    for (int index = 0;
                         index < handler.getSlots();
                         index++) {
                        ItemStack stack =
                                handler.getStackInSlot(index);
                        if (!stack.isEmpty()) {
                            allItems.add(stack.copy());
                        }
                    }
                    return false;
                }
        );

        CuriosCompat.visitHandlers(player, curios -> {
            for (int slot = 0; slot < curios.getSlots(); slot++) {
                ItemStack stack = curios.getStackInSlot(slot);
                if (!stack.isEmpty()) allItems.add(stack.copy());
            }
            return false;
        });

        IItemHandler handler = vanillaHandler;
        if (handler == null && player != null) {
            handler = player.getCapability(
                            ForgeCapabilities.ITEM_HANDLER,
                            null
                    )
                    .orElse(null);
        }
        if (handler != null) {
            for (int index = 0;
                 index < handler.getSlots();
                 index++) {
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

        VirtualInventory inventory = new VirtualInventory(
                allItems.size(),
                player
        );
        for (int index = 0; index < allItems.size(); index++) {
            inventory.setItem(index, allItems.get(index));
        }
        return inventory.getHandler();
    }

    /**
     * Read-biased aggregate handler over copied stacks.
     */
    private static final class AggregateQueryHandler
            implements IItemHandler {
        private final List<ItemStack> stacks;

        private AggregateQueryHandler(List<ItemStack> stacks) {
            this.stacks = stacks;
        }

        @Override
        public int getSlots() {
            return this.stacks.size();
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            if (slot < 0 || slot >= this.stacks.size()) {
                return ItemStack.EMPTY;
            }
            return this.stacks.get(slot);
        }

        @Override
        public ItemStack insertItem(
                int slot,
                ItemStack stack,
                boolean simulate
        ) {
            return stack;
        }

        @Override
        public ItemStack extractItem(
                int slot,
                int amount,
                boolean simulate
        ) {
            if (slot < 0 || slot >= this.stacks.size() || amount <= 0) {
                return ItemStack.EMPTY;
            }
            ItemStack stack = this.stacks.get(slot);
            if (stack.isEmpty()) {
                return ItemStack.EMPTY;
            }
            int extracted = Math.min(amount, stack.getCount());
            ItemStack result = stack.copyWithCount(extracted);
            if (!simulate) {
                stack.shrink(extracted);
            }
            return result;
        }

        @Override
        public int getSlotLimit(int slot) {
            return 64;
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return false;
        }
    }

    static boolean containsCompatibleAmmoInHandlers(
            Iterable<? extends IItemHandler> backpackHandlers,
            Predicate<ItemStack> matcher
    ) {
        for (IItemHandler handler : backpackHandlers) {
            for (int slot = 0;
                 slot < handler.getSlots();
                 slot++) {
                if (matcher.test(handler.getStackInSlot(slot))) {
                    return true;
                }
            }
        }
        return false;
    }

    static int extractCompatibleAmmoDirectly(
            IItemHandler handler,
            ItemStack gunStack,
            int requested
    ) {
        if (requested <= 0
                || handler == null
                || gunStack == null
                || gunStack.isEmpty()) {
            return 0;
        }

        int consumed = 0;

        final int slots;
        try {
            slots = handler.getSlots();
        } catch (LinkageError linkageError) {
            throw new PartialLinkageMutation(
                    0,
                    linkageError
            );
        } catch (RuntimeException exception) {
            throw new PartialRuntimeMutation(
                    0,
                    exception
            );
        }

        for (int slot = 0;
             slot < slots && consumed < requested;
             slot++) {
            try {
                ItemStack stack =
                        handler.getStackInSlot(slot);

                if (stack == null || stack.isEmpty()) {
                    continue;
                }

                int remaining = requested - consumed;
                Item item = stack.getItem();

                if (item instanceof IAmmo ammo
                        && ammo.isAmmoOfGun(
                        gunStack,
                        stack
                )) {
                    // Oversized storage stacks may only yield one normal stack
                    // per call. Keep draining this slot while it supplies ammo.
                    while (true) {
                        ItemStack extracted = handler.extractItem(slot, remaining, false);
                        if (extracted == null || extracted.isEmpty()) {
                            break;
                        }
                        consumed += clampConsumed(remaining, extracted.getCount());
                        remaining = requested - consumed;
                        if (remaining <= 0) {
                            break;
                        }

                        // A handler may replace its stack during extraction.
                        stack = handler.getStackInSlot(slot);
                        if (stack == null || stack.isEmpty()
                                || !(stack.getItem() instanceof IAmmo nextAmmo)
                                || !nextAmmo.isAmmoOfGun(gunStack, stack)) {
                            break;
                        }
                    }

                    continue;
                }

                if (item instanceof IAmmoBox ammoBox
                        && ammoBox.isAmmoBoxOfGun(
                        gunStack,
                        stack
                )) {
                    int boxAmmoCount = Math.max(
                            0,
                            ammoBox.getAmmoCount(stack)
                    );

                    int extracted = Math.min(
                            boxAmmoCount,
                            remaining
                    );

                    if (extracted <= 0) {
                        continue;
                    }

                    int newCount =
                            boxAmmoCount - extracted;

                    /*
                     * Once setAmmoCount returns normally, this quantity is
                     * confirmed as consumed. Record it before the secondary
                     * AmmoId cleanup so an error there does not erase the known
                     * amount.
                     */
                    ammoBox.setAmmoCount(
                            stack,
                            newCount
                    );
                    consumed += extracted;

                    if (newCount <= 0) {
                        ammoBox.setAmmoId(
                                stack,
                                DefaultAssets.EMPTY_AMMO_ID
                        );
                    }
                }
            } catch (PartialLinkageMutation
                     | PartialRuntimeMutation failure) {
                throw failure;
            } catch (LinkageError linkageError) {
                throw new PartialLinkageMutation(
                        consumed,
                        linkageError
                );
            } catch (RuntimeException exception) {
                throw new PartialRuntimeMutation(
                        consumed,
                        exception
                );
            }
        }

        return consumed;
    }

    static int clampConsumed(int requested, int consumed) {
        if (requested <= 0) {
            return 0;
        }
        return Math.max(0, Math.min(requested, consumed));
    }

    static boolean containsCompatibleAmmo(
            IItemHandler handler,
            ItemStack gunStack
    ) {
        for (int slot = 0; slot < handler.getSlots(); slot++) {
            if (isCompatibleAmmo(
                    gunStack,
                    handler.getStackInSlot(slot)
            )) {
                return true;
            }
        }
        return false;
    }

    private static boolean isCompatibleAmmo(
            ItemStack gunStack,
            ItemStack candidate
    ) {
        if (candidate.isEmpty()) {
            return false;
        }

        Item item = candidate.getItem();
        if (item instanceof IAmmo ammo) {
            return ammo.isAmmoOfGun(gunStack, candidate);
        }
        if (item instanceof IAmmoBox ammoBox) {
            return ammoBox.isAmmoBoxOfGun(gunStack, candidate);
        }
        return false;
    }
}
