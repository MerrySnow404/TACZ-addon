package com.mafuyu404.taczaddon.network.server;

import com.mafuyu404.taczaddon.network.SwitchGunPacket;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public final class SwitchGunHandler {
    private SwitchGunHandler() {}
    public static void handle(SwitchGunPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;

            Inventory inventory = player.getInventory();
            if (msg.slot() < 0 || msg.slot() >= inventory.getContainerSize()) return;

            ItemStack target = inventory.getItem(msg.slot()).copy();
            inventory.setItem(msg.slot(), player.getMainHandItem());
            inventory.setItem(inventory.selected, target);
        });
        ctx.get().setPacketHandled(true);
    }

}
