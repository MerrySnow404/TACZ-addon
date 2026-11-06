package com.mafuyu404.taczaddon.refit;

import com.mafuyu404.taczaddon.inventory.InventorySource;

import net.neoforged.neoforge.items.IItemHandler;

/** Feature locator paired with a request-scoped inventory capability. */
public record RefitSource(RefitSourceLocator locator, InventorySource inventory) {
    public IItemHandler handler() {
        return inventory.handler();
    }

    public boolean isValid() {
        return inventory.isValid();
    }

    public void markChanged() {
        inventory.markChanged();
    }
}
