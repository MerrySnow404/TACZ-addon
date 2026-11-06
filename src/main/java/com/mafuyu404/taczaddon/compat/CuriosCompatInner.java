package com.mafuyu404.taczaddon.compat;

import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.items.IItemHandler;

import top.theillusivec4.curios.api.CuriosApi;

import java.util.function.Consumer;

public final class CuriosCompatInner {
    private CuriosCompatInner() {}

    public static void forEachCuriosHandler(Player player, Consumer<IItemHandler> action) {
        CuriosApi.getCuriosInventory(player)
                .ifPresent(
                        curiosInventory ->
                                curiosInventory
                                        .getCurios()
                                        .values()
                                        .forEach(
                                                stacksHandler ->
                                                        action.accept(stacksHandler.getStacks())));
    }
}
