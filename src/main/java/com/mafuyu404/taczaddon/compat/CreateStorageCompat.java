package com.mafuyu404.taczaddon.compat;

import com.mafuyu404.taczaddon.inventory.InventoryIntegrations;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.items.IItemHandler;

import java.util.function.Predicate;

/** Optional dependency boundary: no Create types escape this facade. */
public final class CreateStorageCompat {
    private static boolean broken;

    private CreateStorageCompat() {}

    public static boolean isUsable() {
        return !broken && ModList.get() != null && ModList.get().isLoaded("fxntstorage");
    }

    public static boolean visitBackpacks(Player player, Predicate<IItemHandler> visitor) {
        if (player == null || !isUsable()) return false;
        try {
            return CreateStorageBackpacksCompatInner.visit(player, visitor, false);
        } catch (LinkageError failure) {
            disable(failure);
            return false;
        }
    }

    public static boolean mutateBackpacks(ServerPlayer player, Predicate<IItemHandler> visitor) {
        if (player == null || !isUsable()) return false;
        try {
            return CreateStorageBackpacksCompatInner.visit(player, visitor, true);
        } catch (LinkageError failure) {
            disable(failure);
            throw failure;
        }
    }

    public static InventoryIntegrations.BlockAccess bind(BlockEntity block, IItemHandler handler) {
        if (block == null
                || handler == null
                || !block.getClass().getName().startsWith("net.fxnt.fxntstorage.")) return null;
        if (!isUsable()) throw new IllegalStateException("Create Storage integration unavailable");
        try {
            return CreateStorageBlocksCompatInner.bind(block, handler);
        } catch (LinkageError failure) {
            disable(failure);
            throw new IllegalStateException("Create Storage binding failed", failure);
        }
    }

    private static void disable(LinkageError failure) {
        if (!broken)
            com.mojang.logging.LogUtils.getLogger()
                    .warn("Create Storage integration disabled after API failure", failure);
        broken = true;
    }
}
