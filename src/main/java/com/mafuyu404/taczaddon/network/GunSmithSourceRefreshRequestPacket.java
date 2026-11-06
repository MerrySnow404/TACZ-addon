package com.mafuyu404.taczaddon.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;

public final class GunSmithSourceRefreshRequestPacket {

    private final int containerId;
    private final long requestId;
    @Nullable
    private final ResourceLocation recipeId;

    public GunSmithSourceRefreshRequestPacket(
            int containerId,
            long requestId
    ) {
        this(containerId, requestId, null);
    }

    public GunSmithSourceRefreshRequestPacket(
            int containerId,
            long requestId,
            @Nullable ResourceLocation recipeId
    ) {
        this.containerId = containerId;
        this.requestId = requestId;
        this.recipeId = recipeId;
    }

    public static void encode(
            GunSmithSourceRefreshRequestPacket message,
            FriendlyByteBuf buffer
    ) {
        buffer.writeInt(message.containerId);
        buffer.writeLong(message.requestId);
        buffer.writeBoolean(message.recipeId != null);
        if (message.recipeId != null) {
            buffer.writeResourceLocation(message.recipeId);
        }
    }

    public static GunSmithSourceRefreshRequestPacket decode(
            FriendlyByteBuf buffer
    ) {
        int containerId = buffer.readInt();
        long requestId = buffer.readLong();
        ResourceLocation recipeId = buffer.readBoolean()
                ? buffer.readResourceLocation()
                : null;
        return new GunSmithSourceRefreshRequestPacket(
                containerId,
                requestId,
                recipeId
        );
    }

    public int containerId() { return containerId; }
    public long requestId() { return requestId; }
    public ResourceLocation recipeId() { return recipeId; }
}
