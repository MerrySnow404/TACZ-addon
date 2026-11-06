package com.mafuyu404.taczaddon.network;

import net.minecraft.network.FriendlyByteBuf;

/**
 * Type-safe S2C mirror of the server-owned liberateAttachment gamerule.
 */
public final class LiberateAttachmentStatePacket {
    private final boolean enabled;

    public LiberateAttachmentStatePacket(boolean enabled) {
        this.enabled = enabled;
    }

    public static void encode(
            LiberateAttachmentStatePacket message,
            FriendlyByteBuf buffer
    ) {
        buffer.writeBoolean(message.enabled);
    }

    public static LiberateAttachmentStatePacket decode(
            FriendlyByteBuf buffer
    ) {
        return new LiberateAttachmentStatePacket(
                buffer.readBoolean()
        );
    }

    public boolean enabled() { return enabled; }
}
