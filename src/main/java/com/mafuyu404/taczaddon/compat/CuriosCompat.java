package com.mafuyu404.taczaddon.compat;

import net.minecraft.world.entity.player.Player;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.items.IItemHandler;

import java.util.function.Consumer;

public final class CuriosCompat {
    private static final String MOD_ID = "curios";

    private CuriosCompat() {}

    public static boolean isInstalled() {
        return ModList.get() != null && ModList.get().isLoaded(MOD_ID);
    }

    public static boolean visitHandlers(
            Player player, java.util.function.Predicate<IItemHandler> action) {
        if (!isInstalled() || player == null) return false;
        boolean[] stopped = {false};
        CuriosCompatInner.forEachCuriosHandler(
                player,
                handler -> {
                    if (!stopped[0]) stopped[0] = action.test(handler);
                });
        return stopped[0];
    }

    public static boolean mutateHandlers(
            Player player, java.util.function.Predicate<IItemHandler> action) {
        try {
            return visitHandlers(player, action);
        } finally {
            if (player != null) {
                player.getInventory().setChanged();
                player.containerMenu.broadcastChanges();
            }
        }
    }

    public static void forEachCuriosHandler(Player player, Consumer<IItemHandler> action) {
        if (!isInstalled() || player == null || action == null) {
            return;
        }

        CuriosCompatInner.forEachCuriosHandler(player, action);
    }
}
