package com.mafuyu404.taczaddon.inventory;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.items.IItemHandler;

import java.util.UUID;
import java.util.function.BooleanSupplier;

/** Provider location plus an inventory capability, with no feature-specific locator. */
public record CarriedInventorySource(
        String inventoryName,
        String identifier,
        int slot,
        UUID storageId,
        IItemHandler handler,
        BooleanSupplier valid,
        Runnable changed)
        implements InventorySource {
    public Object backendIdentity() {
        return handler;
    }

    public boolean isValid() {
        return valid.getAsBoolean();
    }

    public void markChanged() {
        changed.run();
    }

    public void synchronize(ServerPlayer player) {
        changed.run();
    }
}
