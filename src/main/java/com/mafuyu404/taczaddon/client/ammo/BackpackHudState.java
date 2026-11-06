package com.mafuyu404.taczaddon.client.ammo;

import com.mafuyu404.taczaddon.compat.createstorage.CreateStorageCompat;
import com.mafuyu404.taczaddon.compat.curios.CuriosCompat;
import com.mafuyu404.taczaddon.compat.sophisticated.SophisticatedBackpacksCompat;
import com.mafuyu404.taczaddon.storage.VirtualInventory;

import com.tacz.guns.api.item.IGun;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.TickEvent;

import java.util.ArrayList;

public final class BackpackHudState {
    private BackpackHudState() {}
    public static void reset() {
        _virtualInventory = null;
        taczaddon$hudCachePlayer = null;
        taczaddon$hudCacheLevel = null;
        taczaddon$hudTicksUntilRefresh = 0;
        taczaddon$backpackTicksUntilSync = 0;
        taczaddon$hudInitialSyncRequested = false;
    }
    public static VirtualInventory _virtualInventory;

    private static final int
            BACKPACK_HUD_REFRESH_INTERVAL_TICKS = 5;

    /*
     * While holding a gun, refresh carried backpacks every 5 ticks. Ordinary
     * backpacks need a contents request even after bootstrap (including newly
     * acquired backpacks). Linked groups use revision probes so unchanged
     * contents do not require another full snapshot.
     */
    private static final int
            BACKPACK_SYNC_INTERVAL_TICKS = 5;

    private static Player taczaddon$hudCachePlayer;
    private static Level taczaddon$hudCacheLevel;

    private static int
            taczaddon$hudTicksUntilRefresh;

    private static int
            taczaddon$backpackTicksUntilSync;

    private static boolean
            taczaddon$hudInitialSyncRequested;

    public static void storageBackpack(
            TickEvent.ClientTickEvent event
    ) {
        if (event.phase == TickEvent.Phase.END) {
            return;
        }

        Minecraft minecraft =
                Minecraft.getInstance();

        Player player =
                minecraft.player;

        Level level =
                minecraft.level;

        if (player == null || level == null) {
            reset();
            return;
        }

        if (taczaddon$hudCachePlayer != player
                || taczaddon$hudCacheLevel != level) {
            taczaddon$hudCachePlayer = player;
            taczaddon$hudCacheLevel = level;

            taczaddon$hudTicksUntilRefresh = 0;
            taczaddon$backpackTicksUntilSync = 0;

            taczaddon$hudInitialSyncRequested =
                    false;

        }

        if (!taczaddon$hudInitialSyncRequested) {
            /*
             * Full bootstrap:
             *
             * ordinary backpack -> UUID contents request
             * linked backpack   -> knownRevision=-1 full snapshot request
             */
            SophisticatedBackpacksCompat
                    .syncAllBackpack(
                            player
                    );

            taczaddon$hudInitialSyncRequested =
                    true;

            /*
             * Request-only tick: both normal and linked server responses are
             * asynchronous.
             */
            taczaddon$hudTicksUntilRefresh =
                    BACKPACK_HUD_REFRESH_INTERVAL_TICKS;

            taczaddon$backpackTicksUntilSync =
                    BACKPACK_SYNC_INTERVAL_TICKS;

            _virtualInventory = null;
            return;
        }

        taczaddon$tickBackpackRefresh(
                player
        );

        if (--taczaddon$hudTicksUntilRefresh
                > 0) {
            return;
        }

        rebuildBackpackHudInventory(
                player
        );

        taczaddon$hudTicksUntilRefresh =
                BACKPACK_HUD_REFRESH_INTERVAL_TICKS;
    }

    private static void
    taczaddon$tickBackpackRefresh(
            Player player
    ) {
        /*
         * Reserve-ammo coherence only matters while the player is actively using
         * a gun. Avoid permanent background polling for players doing unrelated
         * gameplay.
         */
        if (IGun.getIGunOrNull(
                player.getMainHandItem()
        ) == null) {
            /*
             * The next time a gun becomes active, refresh immediately.
             */
            taczaddon$backpackTicksUntilSync = 0;
            return;
        }

        if (--taczaddon$backpackTicksUntilSync
                > 0) {
            return;
        }

        SophisticatedBackpacksCompat
                .refreshBackpackSnapshots(
                        player
                );

        taczaddon$backpackTicksUntilSync =
                BACKPACK_SYNC_INTERVAL_TICKS;
    }

    private static void rebuildBackpackHudInventory(Player player) {
        ArrayList<ItemStack> combined = new ArrayList<>();

        CreateStorageCompat.visitBackpacks(player, handler -> {
            for (int slot = 0; slot < handler.getSlots(); slot++) {
                ItemStack stack = handler.getStackInSlot(slot);
                if (!stack.isEmpty()) combined.add(stack.copy());
            }
            return false;
        });

        SophisticatedBackpacksCompat.visitInventoryBackpacks(
                player,
                handler -> {
                    for (int index = 0;
                         index < handler.getSlots();
                         index++) {
                        ItemStack stack =
                                handler.getStackInSlot(index);
                        if (!stack.isEmpty()) {
                            combined.add(stack.copy());
                        }
                    }
                    return false;
                }
        );

        CuriosCompat.visitHandlers(player, handler -> {
            for (int slot = 0; slot < handler.getSlots(); slot++) {
                ItemStack stack = handler.getStackInSlot(slot);
                if (!stack.isEmpty()) combined.add(stack.copy());
            }
            return false;
        });
        combined.addAll(player.getInventory().items);
        combined.addAll(player.getInventory().armor);
        combined.addAll(player.getInventory().offhand);

        VirtualInventory virtualInventory =
                new VirtualInventory(combined.size(), player);

        for (int index = 0;
             index < combined.size();
             index++) {
            virtualInventory.setItem(index, combined.get(index));
        }

        _virtualInventory = virtualInventory;
    }

}
