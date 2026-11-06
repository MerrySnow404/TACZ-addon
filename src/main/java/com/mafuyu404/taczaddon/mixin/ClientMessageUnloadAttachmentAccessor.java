package com.mafuyu404.taczaddon.mixin;

import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.network.message.ClientMessageUnloadAttachment;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Field access for the TaCZ 1.21.1 unload packet.
 *
 * <p>The packet class keeps its fields private and exposes no getters, so the addon's takeover
 * mixin reads them through this accessor instead of relying on javac-generated lambda method names.
 */
@Mixin(value = ClientMessageUnloadAttachment.class, remap = false)
public interface ClientMessageUnloadAttachmentAccessor {

    @Accessor("gunSlotIndex")
    int taczaddon$gunSlotIndex();

    @Accessor("attachmentType")
    AttachmentType taczaddon$attachmentType();
}
