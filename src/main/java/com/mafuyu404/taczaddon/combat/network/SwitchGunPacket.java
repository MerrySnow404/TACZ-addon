package com.mafuyu404.taczaddon.combat.network;

import com.mafuyu404.taczaddon.TACZaddon;
import com.mafuyu404.taczaddon.network.ServerboundPacketGuard;
import com.tacz.guns.api.item.IGun;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public class SwitchGunPacket implements CustomPacketPayload {
    public static final Type<SwitchGunPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(TACZaddon.MODID, "switch_gun"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SwitchGunPacket> STREAM_CODEC =
            ByteBufCodecs.INT
                    .<RegistryFriendlyByteBuf>cast()
                    .map(SwitchGunPacket::new, SwitchGunPacket::slot);
    private static final int PLAYER_STORAGE_START = 0;
    private static final int PLAYER_STORAGE_END_EXCLUSIVE = 36;
    private static final int COOLDOWN_TICKS = 2;

    private final int slot;

    public SwitchGunPacket(int slot) {
        this.slot = slot;
    }

    private int slot() {
        return slot;
    }

    public static void handle(SwitchGunPacket msg, IPayloadContext context) {
        context.enqueueWork(
                () -> {
                    if (!(context.player() instanceof ServerPlayer player)) return;
                    if (ServerboundPacketGuard.isRateLimited(player, TYPE.id(), COOLDOWN_TICKS))
                        return;

                    Inventory inventory = player.getInventory();
                    if (msg.slot < PLAYER_STORAGE_START || msg.slot >= PLAYER_STORAGE_END_EXCLUSIVE)
                        return;
                    if (inventory.selected < 0 || inventory.selected >= 9) return;
                    if (msg.slot == inventory.selected) return;

                    ItemStack current = inventory.getSelected();
                    ItemStack target = inventory.getItem(msg.slot);
                    if (IGun.getIGunOrNull(current) == null || IGun.getIGunOrNull(target) == null)
                        return;

                    inventory.setItem(msg.slot, current.copy());
                    inventory.setItem(inventory.selected, target);
                    inventory.setChanged();
                    player.containerMenu.broadcastChanges();
                });
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
