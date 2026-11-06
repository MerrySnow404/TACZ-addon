package com.mafuyu404.taczaddon.network;

import net.minecraft.network.FriendlyByteBuf;

/**
 * Type-safe S2C mirror of the server-owned showAttachmentDetail gamerule.
 */
public final class AttachmentDetailRuleStatePacket {
    private final boolean enabled;

    public AttachmentDetailRuleStatePacket(boolean enabled) {
        this.enabled = enabled;
    }

    public static void encode(
            AttachmentDetailRuleStatePacket message,
            FriendlyByteBuf buffer
    ) {
        buffer.writeBoolean(message.enabled);
    }

    public static AttachmentDetailRuleStatePacket decode(
            FriendlyByteBuf buffer
    ) {
        return new AttachmentDetailRuleStatePacket(
                buffer.readBoolean()
        );
    }

    public boolean enabled() { return enabled; }
}
