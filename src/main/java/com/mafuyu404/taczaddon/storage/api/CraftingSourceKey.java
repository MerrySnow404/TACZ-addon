package com.mafuyu404.taczaddon.storage.api;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.UUID;

public sealed interface CraftingSourceKey
        permits CraftingSourceKey.PlayerInventory,
                CraftingSourceKey.BlockEntity,
                CraftingSourceKey.Backpack {

    String type();

    record Backpack(UUID playerId, String handlerName, String identifier,
                    int inventorySlot, UUID contentsId) implements CraftingSourceKey {
        @Override public String type() { return "backpack"; }
    }

    record PlayerInventory(UUID playerId) implements CraftingSourceKey {
        @Override public String type() { return "player"; }
    }

    record BlockEntity(
            ResourceKey<Level> dimension,
            BlockPos pos
    ) implements CraftingSourceKey {
        @Override public String type() { return "block"; }
    }
}
