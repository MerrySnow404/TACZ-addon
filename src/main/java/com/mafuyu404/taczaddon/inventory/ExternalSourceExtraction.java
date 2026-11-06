package com.mafuyu404.taczaddon.inventory;

import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandlerModifiable;

/** One reversible physical source slot; snapshot precedes even a throwing extraction. */
public final class ExternalSourceExtraction {
    private final IItemHandlerModifiable handler;
    private final int slot;
    private final ItemStack snapshot;
    private boolean attempted;

    public ExternalSourceExtraction(IItemHandlerModifiable handler, int slot) {
        this.handler = handler;
        this.slot = slot;
        this.snapshot = handler.getStackInSlot(slot).copy();
    }

    public boolean simulateOne() {
        return !snapshot.isEmpty() && matchesOne(handler.extractItem(slot, 1, true), snapshot);
    }

    public ItemStack extractOne() {
        if (!ItemStack.matches(snapshot, handler.getStackInSlot(slot)))
            throw new IllegalStateException("External source changed");
        attempted = true;
        ItemStack extracted = handler.extractItem(slot, 1, false);
        if (!matchesOne(extracted, snapshot))
            throw new IllegalStateException("External extraction did not match simulation");
        ItemStack remaining = snapshot.copyWithCount(snapshot.getCount() - 1);
        if (!ItemStack.matches(remaining, handler.getStackInSlot(slot))) {
            throw new IllegalStateException("External source did not consume exactly one item");
        }
        return extracted;
    }

    public void rollback() {
        if (!attempted) return;
        handler.setStackInSlot(slot, snapshot.copy());
        attempted = false;
    }

    public static boolean matchesOne(ItemStack extracted, ItemStack expected) {
        return !extracted.isEmpty()
                && extracted.getCount() == 1
                && ItemStack.isSameItemSameComponents(extracted, expected);
    }
}
