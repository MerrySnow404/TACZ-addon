package com.mafuyu404.taczaddon.network;

import net.minecraft.network.FriendlyByteBuf;

public final class RefitSourceRefreshRequestPacket {

    private final long requestId;

    public RefitSourceRefreshRequestPacket(long requestId) {
        this.requestId = requestId;
    }

    public static void encode(
            RefitSourceRefreshRequestPacket message,
            FriendlyByteBuf buffer
    ) {
        buffer.writeLong(message.requestId);
    }

    public static RefitSourceRefreshRequestPacket decode(
            FriendlyByteBuf buffer
    ) {
        return new RefitSourceRefreshRequestPacket(
                buffer.readLong()
        );
    }

    public long requestId() { return requestId; }
}
