package com.mafuyu404.taczaddon.compat.curios;

import net.minecraft.world.entity.player.Player;
import net.minecraftforge.items.IItemHandler;
import top.theillusivec4.curios.api.CuriosApi;

import java.util.TreeMap;
import java.util.function.Predicate;

/** The only class linking to Curios 5.14.1 APIs. Cosmetic slots are not ammo sources. */
final class CuriosCompatInner {
    private CuriosCompatInner() {}
    static boolean visitHandlers(Player player, Predicate<IItemHandler> visitor) {
        var inventory = CuriosApi.getCuriosInventory(player).resolve();
        if (inventory.isEmpty()) return false;
        for (var handler : new TreeMap<>(inventory.get().getCurios()).values()) {
            if (visitor.test(handler.getStacks())) return true;
        }
        return false;
    }
}
