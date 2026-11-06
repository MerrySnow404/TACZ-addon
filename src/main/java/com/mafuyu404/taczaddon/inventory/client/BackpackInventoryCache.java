package com.mafuyu404.taczaddon.inventory.client;

import com.mafuyu404.taczaddon.TACZaddon;
import com.mafuyu404.taczaddon.compat.SophisticatedBackpacksCompat;
import com.mafuyu404.taczaddon.compat.sophisticated.client.BackpackCacheTickPlanner;
import com.mafuyu404.taczaddon.inventory.VirtualInventory;
import com.tacz.guns.api.item.IGun;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@EventBusSubscriber(modid = TACZaddon.MODID, value = Dist.CLIENT)
public final class BackpackInventoryCache {
    private static VirtualInventory virtualInventory;
    private static final BackpackCacheTickPlanner BACKPACK_CACHE_PLANNER =
            new BackpackCacheTickPlanner();

    @SubscribeEvent
    public static void storageBackpack(ClientTickEvent.Pre event) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;

        if (player == null) {
            clearClientCaches();
            return;
        }

        if (BACKPACK_CACHE_PLANNER.isNewIdentity(player, player.level())) {
            virtualInventory = null;
        }

        long gameTime = player.level().getGameTime();

        /*
         * TaCZ's ammunition HUD only examines the main-hand gun, so periodic
         * recovery synchronization is only requested while a main-hand gun is
         * present. The unconditional bootstrap request above does not depend
         * on holdingGun: a player must never need to open a backpack or equip
         * a gun before TACZAddon requests authoritative backpack contents.
         */
        boolean holdingGun = IGun.getIGunOrNull(player.getMainHandItem()) != null;

        switch (BACKPACK_CACHE_PLANNER.tick(player, player.level(), holdingGun, gameTime)) {
            case BOOTSTRAP_REQUEST -> {
                /*
                 * Request-only tick. The server response is asynchronous, so
                 * the virtual inventory must not be built from an
                 * unsynchronized client wrapper yet.
                 */
                SophisticatedBackpacksCompat.syncAllBackpack(player);
                virtualInventory = null;
            }
            case PERIODIC_SYNC_REQUEST -> {
                /*
                 * Recovery only: ordinary consumption correctness comes from
                 * the immediate server contents payload + cache invalidation.
                 */
                SophisticatedBackpacksCompat.syncAllBackpack(player);
            }
            case REBUILD_CACHE -> {
                refreshBackpackCache(player);
            }
            case WAIT -> {
                // Nothing to do this tick.
            }
        }
    }

    public static Optional<Inventory> getVirtualInventory() {
        return Optional.ofNullable(virtualInventory);
    }

    public static void invalidateBackpackCache() {
        virtualInventory = null;
        BACKPACK_CACHE_PLANNER.invalidateCache();
    }

    private static void refreshBackpackCache(Player player) {
        List<ItemStack> combinedInventory =
                new ArrayList<>(SophisticatedBackpacksCompat.getItemsFromInventoryBackpack(player));

        /*
         * The backpack items are inserted first to preserve the existing
         * virtual-inventory ordering. TaCZ only counts ammunition, so the
         * ordering does not affect the displayed total.
         */
        combinedInventory.addAll(player.getInventory().items);

        VirtualInventory updatedInventory = new VirtualInventory(combinedInventory.size(), player);

        for (int slot = 0; slot < combinedInventory.size(); slot++) {
            updatedInventory.setItem(slot, combinedInventory.get(slot));
        }

        virtualInventory = updatedInventory;
    }

    public static void clearClientCaches() {
        virtualInventory = null;
        BACKPACK_CACHE_PLANNER.reset();
    }
}
