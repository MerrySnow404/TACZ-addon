package com.mafuyu404.taczaddon.inventory.network;

import com.mafuyu404.taczaddon.TACZaddon;
import com.mafuyu404.taczaddon.bootstrap.client.ClientPayloadHandler;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;

public class ContainerReaderPacket implements CustomPacketPayload {
    public static final Type<ContainerReaderPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(TACZaddon.MODID, "container_reader"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ContainerReaderPacket> STREAM_CODEC =
            ItemStack.OPTIONAL_LIST_STREAM_CODEC.map(
                    ContainerReaderPacket::new, ContainerReaderPacket::items);

    private final List<ItemStack> items;

    public ContainerReaderPacket(List<ItemStack> items) {
        this.items = List.copyOf(items);
    }

    public List<ItemStack> items() {
        return items;
    }

    public static void handle(ContainerReaderPacket msg, IPayloadContext context) {
        context.enqueueWork(
                () -> {
                    ClientPayloadHandler.handleContainerReader(new ArrayList<>(msg.items));
                });
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
