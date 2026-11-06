package com.mafuyu404.taczaddon.ammo.network;

import com.mafuyu404.taczaddon.TACZaddon;
import com.mafuyu404.taczaddon.network.ServerboundPacketGuard;
import com.tacz.guns.item.AmmoBoxItem;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public class AmmoBoxCollectPacket implements CustomPacketPayload {
    public static final Type<AmmoBoxCollectPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(TACZaddon.MODID, "ammo_box_collect"));
    public static final StreamCodec<RegistryFriendlyByteBuf, AmmoBoxCollectPacket> STREAM_CODEC =
            ByteBufCodecs.INT
                    .<RegistryFriendlyByteBuf>cast()
                    .map(AmmoBoxCollectPacket::new, AmmoBoxCollectPacket::index);
    private static final int PLAYER_MENU_INVENTORY_START = 9;
    private static final int PLAYER_MENU_INVENTORY_END_EXCLUSIVE = 45;
    private static final int COOLDOWN_TICKS = 4;

    private final int index;

    public AmmoBoxCollectPacket(int index) {
        this.index = index;
    }

    private int index() {
        return index;
    }

    public static void handle(AmmoBoxCollectPacket msg, IPayloadContext context) {
        context.enqueueWork(
                () -> {
                    if (!(context.player() instanceof ServerPlayer player)) return;
                    if (ServerboundPacketGuard.isRateLimited(player, TYPE.id(), COOLDOWN_TICKS))
                        return;
                    if (!isPlayerStorageMenuSlot(msg.index)) return;
                    if (msg.index >= player.inventoryMenu.slots.size()) return;

                    Slot ammoBoxSlot = player.inventoryMenu.getSlot(msg.index);
                    if (!ammoBoxSlot.hasItem()) return;
                    ItemStack ammoBox = ammoBoxSlot.getItem();
                    if (!(ammoBox.getItem() instanceof AmmoBoxItem ammoBoxItem)) return;

                    for (int slotIndex = PLAYER_MENU_INVENTORY_START;
                            slotIndex < PLAYER_MENU_INVENTORY_END_EXCLUSIVE;
                            slotIndex++) {
                        if (slotIndex == msg.index
                                || slotIndex >= player.inventoryMenu.slots.size()) {
                            continue;
                        }

                        Slot slot = player.inventoryMenu.getSlot(slotIndex);
                        if (slot.hasItem()) {
                            ammoBoxItem.overrideStackedOnOther(
                                    ammoBox, slot, ClickAction.SECONDARY, player);
                        }
                    }

                    ammoBoxSlot.setChanged();
                    player.inventoryMenu.broadcastChanges();
                });
    }

    private static boolean isPlayerStorageMenuSlot(int slot) {
        return slot >= PLAYER_MENU_INVENTORY_START && slot < PLAYER_MENU_INVENTORY_END_EXCLUSIVE;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
