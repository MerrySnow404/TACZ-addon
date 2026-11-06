package com.mafuyu404.taczaddon.ammo;

import com.tacz.guns.api.DefaultAssets;
import com.tacz.guns.api.item.IAmmo;
import com.tacz.guns.api.item.IAmmoBox;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;

import java.util.function.Predicate;

public final class AmmoSlotOperations {
    private AmmoSlotOperations() {}

    public static int countAmmo(IItemHandler handler, ItemStack gun) {
        long result = 0;
        for (int slot = 0; slot < handler.getSlots(); slot++) {
            ItemStack stack = handler.getStackInSlot(slot);
            if (stack.getItem() instanceof IAmmo ammo && ammo.isAmmoOfGun(gun, stack))
                result += stack.getCount();
            else if (stack.getItem() instanceof IAmmoBox box && box.isAmmoBoxOfGun(gun, stack))
                result += Math.max(0, box.getAmmoCount(stack));
        }
        return (int) Math.min(9999, result);
    }

    static boolean containsCompatibleAmmoInHandlers(
            Iterable<? extends IItemHandler> backpackHandlers, Predicate<ItemStack> matcher) {
        for (IItemHandler handler : backpackHandlers) {
            for (int slot = 0; slot < handler.getSlots(); slot++) {
                if (matcher.test(handler.getStackInSlot(slot))) {
                    return true;
                }
            }
        }
        return false;
    }

    static int extractCompatibleAmmoDirectly(
            IItemHandler handler, ItemStack gunStack, int requested) {
        if (requested <= 0 || handler == null || gunStack == null || gunStack.isEmpty()) {
            return 0;
        }

        int consumed = 0;

        final int slots;
        try {
            slots = handler.getSlots();
        } catch (LinkageError linkageError) {
            throw new PartialLinkageMutation(0, linkageError);
        } catch (RuntimeException exception) {
            throw new PartialRuntimeMutation(0, exception);
        }

        for (int slot = 0; slot < slots && consumed < requested; slot++) {
            try {
                ItemStack stack = handler.getStackInSlot(slot);

                if (stack == null || stack.isEmpty()) {
                    continue;
                }

                int remaining = requested - consumed;
                Item item = stack.getItem();

                if (item instanceof IAmmo ammo && ammo.isAmmoOfGun(gunStack, stack)) {
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
                        if (stack == null
                                || stack.isEmpty()
                                || !(stack.getItem() instanceof IAmmo nextAmmo)
                                || !nextAmmo.isAmmoOfGun(gunStack, stack)) {
                            break;
                        }
                    }

                    continue;
                }

                if (item instanceof IAmmoBox ammoBox && ammoBox.isAmmoBoxOfGun(gunStack, stack)) {
                    int boxAmmoCount = Math.max(0, ammoBox.getAmmoCount(stack));

                    int extracted = Math.min(boxAmmoCount, remaining);

                    if (extracted <= 0) {
                        continue;
                    }

                    int newCount = boxAmmoCount - extracted;

                    /*
                     * Once setAmmoCount returns normally, this quantity is
                     * confirmed as consumed. Record it before the secondary
                     * AmmoId cleanup so an error there does not erase the known
                     * amount.
                     */
                    ammoBox.setAmmoCount(stack, newCount);
                    consumed += extracted;

                    if (newCount <= 0) {
                        ammoBox.setAmmoId(stack, DefaultAssets.EMPTY_AMMO_ID);
                    }
                }
            } catch (PartialLinkageMutation | PartialRuntimeMutation failure) {
                throw failure;
            } catch (LinkageError linkageError) {
                throw new PartialLinkageMutation(consumed, linkageError);
            } catch (RuntimeException exception) {
                throw new PartialRuntimeMutation(consumed, exception);
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

    static boolean containsCompatibleAmmo(IItemHandler handler, ItemStack gunStack) {
        for (int slot = 0; slot < handler.getSlots(); slot++) {
            if (isCompatibleAmmo(gunStack, handler.getStackInSlot(slot))) {
                return true;
            }
        }
        return false;
    }

    static boolean isCompatibleAmmo(ItemStack gunStack, ItemStack candidate) {
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

    static final class PartialLinkageMutation extends LinkageError {
        final int confirmed;

        PartialLinkageMutation(int confirmed, LinkageError cause) {
            super(cause.getMessage());
            this.confirmed = Math.max(0, confirmed);
            initCause(cause);
        }

        int confirmed() {
            return this.confirmed;
        }
    }

    static final class PartialRuntimeMutation extends RuntimeException {
        final int confirmed;

        PartialRuntimeMutation(int confirmed, RuntimeException cause) {
            super("Ammo handler failed after a partial mutation", cause);
            this.confirmed = Math.max(0, confirmed);
        }

        int confirmed() {
            return this.confirmed;
        }
    }
}
