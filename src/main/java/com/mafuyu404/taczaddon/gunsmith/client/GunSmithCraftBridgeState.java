package com.mafuyu404.taczaddon.gunsmith.client;

import com.mafuyu404.taczaddon.config.ClientConfig;
import com.mafuyu404.taczaddon.config.ClientSyncedConfig;
import com.mafuyu404.taczaddon.gunsmith.network.GunSmithCraftRequestPacket;
import com.mafuyu404.taczaddon.gunsmith.network.GunSmithCraftResultPacket;
import com.mafuyu404.taczaddon.network.NetworkHandler;
import com.mafuyu404.taczaddon.presentation.client.ItemIconToast;
import com.tacz.guns.client.gui.GunSmithTableScreen;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.*;

public final class GunSmithCraftBridgeState {
    private static long nextRequestId;
    private static final Map<Long, Integer> pending = new LinkedHashMap<>();

    private GunSmithCraftBridgeState() {}

    public static int requestedCount(boolean shift) {
        return shift ? ClientSyncedConfig.getBatchCraftMax() : 1;
    }

    public static void request(int containerId, ResourceLocation recipeId, int requested) {
        if (pending.size() >= 16) pending.remove(pending.keySet().iterator().next());
        long requestId = ++nextRequestId;
        pending.put(requestId, containerId);
        NetworkHandler.sendToServer(
                new GunSmithCraftRequestPacket(containerId, requestId, recipeId, requested));
    }

    public static void accept(GunSmithCraftResultPacket result) {
        Integer container = pending.remove(result.requestId());
        if (container == null || container != result.containerId()) return;
        var minecraft = Minecraft.getInstance();
        if (minecraft.player == null
                || minecraft.player.containerMenu.containerId != result.containerId()) return;
        if (minecraft.screen instanceof GunSmithTableScreen screen) screen.updateIngredientCount();
        if (!result.success()
                || result.craftedExecutions() <= 0
                || result.outputPerCraft().isEmpty()) return;
        var output = result.outputPerCraft();
        long total = (long) output.getCount() * result.craftedExecutions();
        if (ClientConfig.enableGunSmithTableCraftToast()) {
            ItemIconToast.show(
                    Component.translatable("gui.taczaddon.gun_smith_table.crafted"),
                    Component.translatable(
                            "gui.taczaddon.gun_smith_table.crafted_item",
                            output.getHoverName(),
                            total),
                    output);
        }
    }

    public static void reset() {
        pending.clear();
        nextRequestId = 0;
    }
}
