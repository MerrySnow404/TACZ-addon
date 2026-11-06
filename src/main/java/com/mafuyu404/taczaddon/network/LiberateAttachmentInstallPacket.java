package com.mafuyu404.taczaddon.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

/**
 * C2S liberated install request. The server resolves the attachment ID from
 * its own current TaCZ data instead of trusting a client virtual slot index.
 */
public final class LiberateAttachmentInstallPacket {
    private final int gunSlotIndex;
    private final ResourceLocation attachmentId;

    public LiberateAttachmentInstallPacket(
            int gunSlotIndex,
            ResourceLocation attachmentId
    ) {
        this.gunSlotIndex = gunSlotIndex;
        this.attachmentId = attachmentId;
    }

    public static void encode(
            LiberateAttachmentInstallPacket message,
            FriendlyByteBuf buffer
    ) {
        buffer.writeInt(message.gunSlotIndex);
        buffer.writeResourceLocation(message.attachmentId);
    }

    public static LiberateAttachmentInstallPacket decode(
            FriendlyByteBuf buffer
    ) {
        return new LiberateAttachmentInstallPacket(
                buffer.readInt(),
                buffer.readResourceLocation()
        );
    }

    public int gunSlotIndex() { return gunSlotIndex; }
    public ResourceLocation attachmentId() { return attachmentId; }
}
