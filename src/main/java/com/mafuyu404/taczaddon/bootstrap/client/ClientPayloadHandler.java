package com.mafuyu404.taczaddon.bootstrap.client;

import com.mafuyu404.taczaddon.inventory.client.VirtualContainerLoader;
import com.tacz.guns.client.gui.GunRefitScreen;
import com.tacz.guns.client.gui.GunSmithTableScreen;
import com.tacz.guns.resource.modifier.AttachmentPropertyManager;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;

public final class ClientPayloadHandler {
    private ClientPayloadHandler() {}

    public static void handleContainerReader(ArrayList<ItemStack> items) {
        if (Minecraft.getInstance().screen instanceof GunSmithTableScreen screen
                && screen instanceof VirtualContainerLoader loader) {
            loader.taczaddon$setVirtualContainer(items);
            screen.updateIngredientCount();
        }
    }

    public static void handleRefreshRefitScreen() {
        Minecraft minecraft = Minecraft.getInstance();

        LocalPlayer player = minecraft.player;

        if (player == null) {
            return;
        }

        if (!(minecraft.screen instanceof GunRefitScreen screen)) {
            return;
        }

        screen.init();

        /*
         * Intentionally mirrors TaCZ 1.21.1's native
         * ServerMessageRefreshRefitScreen.updateScreen(), which performs the
         * same screen.init() followed by AttachmentPropertyManager
         * .postChangeEvent(player, player.getMainHandItem()) after every
         * successful refit. The server-side postChange in
         * AttachmentRefitService refreshes server-side caches; this
         * client-side call refreshes the client's attachment modifier cache
         * (tooltips/property diagrams) for the synced main-hand gun, so it is
         * not redundant and must be kept.
         */
        AttachmentPropertyManager.postChangeEvent(player, player.getMainHandItem());
    }
}
