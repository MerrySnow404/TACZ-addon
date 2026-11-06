package com.mafuyu404.taczaddon.inventory;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.items.IItemHandler;

/** Request-scoped physical inventory capability; implementations own persistence. */
public interface InventorySource {
    IItemHandler handler();

    Object backendIdentity();

    boolean isValid();

    void markChanged();

    void synchronize(ServerPlayer player);
}
