package com.mafuyu404.taczaddon.network;

import com.mafuyu404.taczaddon.gunsmith.CraftingTransaction;

import io.netty.handler.codec.DecoderException;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;

public final class GunSmithCraftResultPacket {
    private static final int MAX_CRAFT_EXECUTIONS = 64;

    private final int containerId;
    private final long requestId;
    private final boolean success;
    private final int craftedExecutions;
    private final ItemStack outputPerCraft;
    @Nullable
    private final CraftingTransaction.CraftFailure failureReason;

    public GunSmithCraftResultPacket(
            int containerId,
            long requestId,
            boolean success,
            int craftedExecutions,
            ItemStack outputPerCraft,
            @Nullable CraftingTransaction.CraftFailure failureReason
    ) {
        this.containerId = containerId;
        this.requestId = requestId;
        this.success = success;
        this.craftedExecutions = craftedExecutions;
        this.outputPerCraft = outputPerCraft.copy();
        this.failureReason = failureReason;
    }

    public static void encode(
            GunSmithCraftResultPacket message,
            FriendlyByteBuf buffer
    ) {
        buffer.writeInt(message.containerId);
        buffer.writeLong(message.requestId);
        buffer.writeBoolean(message.success);
        buffer.writeInt(message.craftedExecutions);
        buffer.writeItemStack(message.outputPerCraft, false);
        buffer.writeBoolean(message.failureReason != null);
        if (message.failureReason != null) {
            buffer.writeEnum(message.failureReason);
        }
    }

    public static GunSmithCraftResultPacket decode(
            FriendlyByteBuf buffer
    ) {
        int containerId = buffer.readInt();
        long requestId = buffer.readLong();
        boolean success = buffer.readBoolean();
        int craftedExecutions = buffer.readInt();

        if (craftedExecutions < 0
                || craftedExecutions > MAX_CRAFT_EXECUTIONS) {
            throw new DecoderException(
                    "Invalid gunsmith crafted count: "
                            + craftedExecutions
            );
        }

        ItemStack outputPerCraft = buffer.readItem();
        CraftingTransaction.CraftFailure failure =
                buffer.readBoolean()
                        ? buffer.readEnum(
                        CraftingTransaction.CraftFailure.class
                )
                        : null;

        return new GunSmithCraftResultPacket(
                containerId,
                requestId,
                success,
                craftedExecutions,
                outputPerCraft,
                failure
        );
    }

    public int containerId() {
        return this.containerId;
    }

    public long requestId() {
        return this.requestId;
    }

    public boolean success() {
        return this.success;
    }

    public int craftedExecutions() {
        return this.craftedExecutions;
    }

    public ItemStack outputPerCraft() {
        return this.outputPerCraft.copy();
    }

    @Nullable
    public CraftingTransaction.CraftFailure failureReason() {
        return this.failureReason;
    }
}
