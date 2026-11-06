package com.mafuyu404.taczaddon.gunsmith.network;

import com.mafuyu404.taczaddon.TACZaddon;
import com.mafuyu404.taczaddon.gunsmith.client.GunSmithCraftBridgeState;
import com.mafuyu404.taczaddon.gunsmith.crafting.CraftingTransaction.CraftFailure;

import io.netty.handler.codec.DecoderException;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record GunSmithCraftResultPacket(
        int containerId,
        long requestId,
        boolean success,
        int craftedExecutions,
        ItemStack outputPerCraft,
        CraftFailure failureReason)
        implements CustomPacketPayload {

    private static final int MAX_CRAFT_EXECUTIONS = 64;

    public static final Type<GunSmithCraftResultPacket> TYPE =
            new Type<>(
                    ResourceLocation.fromNamespaceAndPath(
                            TACZaddon.MODID, "gunsmith_craft_result"));

    public static final StreamCodec<RegistryFriendlyByteBuf, GunSmithCraftResultPacket>
            STREAM_CODEC =
                    StreamCodec.of(
                            GunSmithCraftResultPacket::encode, GunSmithCraftResultPacket::decode);

    public GunSmithCraftResultPacket {
        if (outputPerCraft == null) {
            outputPerCraft = ItemStack.EMPTY;
        } else {
            outputPerCraft = outputPerCraft.copy();
        }
    }

    private static void encode(RegistryFriendlyByteBuf buffer, GunSmithCraftResultPacket packet) {
        buffer.writeVarInt(packet.containerId());

        buffer.writeLong(packet.requestId());

        buffer.writeBoolean(packet.success());

        buffer.writeVarInt(packet.craftedExecutions());

        ItemStack.OPTIONAL_STREAM_CODEC.encode(buffer, packet.outputPerCraft());

        buffer.writeBoolean(packet.failureReason() != null);

        if (packet.failureReason() != null) {
            buffer.writeEnum(packet.failureReason());
        }
    }

    private static GunSmithCraftResultPacket decode(RegistryFriendlyByteBuf buffer) {
        int containerId = buffer.readVarInt();

        long requestId = buffer.readLong();

        boolean success = buffer.readBoolean();

        int craftedExecutions = buffer.readVarInt();

        if (craftedExecutions < 0 || craftedExecutions > MAX_CRAFT_EXECUTIONS) {
            throw new DecoderException(
                    "Invalid gunsmith crafted execution count: " + craftedExecutions);
        }

        ItemStack outputPerCraft = ItemStack.OPTIONAL_STREAM_CODEC.decode(buffer);

        boolean hasFailureReason = buffer.readBoolean();

        CraftFailure failureReason = hasFailureReason ? buffer.readEnum(CraftFailure.class) : null;

        /*
         * Structural consistency checks.
         *
         * A successful result must contain at least one committed execution
         * and a real output stack. A failed result must not claim committed
         * executions.
         */
        if (success) {
            if (craftedExecutions <= 0 || outputPerCraft.isEmpty()) {
                throw new DecoderException(
                        "Invalid successful gunsmith result: "
                                + "executions="
                                + craftedExecutions
                                + ", output="
                                + outputPerCraft);
            }
        } else if (craftedExecutions != 0) {
            throw new DecoderException(
                    "Failed gunsmith result claimed "
                            + craftedExecutions
                            + " committed executions");
        }

        return new GunSmithCraftResultPacket(
                containerId, requestId, success, craftedExecutions, outputPerCraft, failureReason);
    }

    public static void handle(GunSmithCraftResultPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> GunSmithCraftBridgeState.accept(packet));
    }

    @Override
    public ItemStack outputPerCraft() {
        return outputPerCraft.copy();
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
