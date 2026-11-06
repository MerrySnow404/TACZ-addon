package com.mafuyu404.taczaddon.network.server;

import com.mafuyu404.taczaddon.network.AmmoBoxCollectPacket;

import com.tacz.guns.item.AmmoBoxItem;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public final class AmmoBoxCollectHandler {
    private AmmoBoxCollectHandler() {}
    public static void handle(AmmoBoxCollectPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;

            if (msg.index() < 0 || msg.index() >= player.inventoryMenu.slots.size()) return;

            Slot ammoBoxSlot = player.inventoryMenu.getSlot(msg.index());
            ItemStack ammoBox = ammoBoxSlot.getItem();
            if (!(ammoBox.getItem() instanceof AmmoBoxItem ammoBoxItem)) return;

            player.inventoryMenu.slots.forEach(slot -> {
                if (slot.hasItem()) ammoBoxItem.overrideStackedOnOther(ammoBox, slot, ClickAction.SECONDARY, player);
            });
        });
        ctx.get().setPacketHandled(true);
    }

}
