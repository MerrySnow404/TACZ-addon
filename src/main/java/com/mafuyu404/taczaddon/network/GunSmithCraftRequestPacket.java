package com.mafuyu404.taczaddon.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

public final class GunSmithCraftRequestPacket {

    private final int containerId;
    private final long requestId;
    private final ResourceLocation recipeId;
    private final int requestedCount;

    public GunSmithCraftRequestPacket(
            int containerId,
            long requestId,
            ResourceLocation recipeId,
            int requestedCount
    ) {
        this.containerId = containerId;
        this.requestId = requestId;
        this.recipeId = recipeId;
        this.requestedCount = requestedCount;
    }

    public static void encode(
            GunSmithCraftRequestPacket message,
            FriendlyByteBuf buffer
    ) {
        buffer.writeInt(message.containerId);
        buffer.writeLong(message.requestId);
        buffer.writeResourceLocation(message.recipeId);
        buffer.writeInt(message.requestedCount);
    }

    public static GunSmithCraftRequestPacket decode(
            FriendlyByteBuf buffer
    ) {
        return new GunSmithCraftRequestPacket(
                buffer.readInt(),
                buffer.readLong(),
                buffer.readResourceLocation(),
                buffer.readInt()
        );
    }

    public int containerId() { return containerId; }
    public long requestId() { return requestId; }
    public ResourceLocation recipeId() { return recipeId; }
    public int requestedCount() { return requestedCount; }
}
