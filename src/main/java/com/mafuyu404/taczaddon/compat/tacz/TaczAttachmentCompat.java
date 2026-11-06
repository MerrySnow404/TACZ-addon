package com.mafuyu404.taczaddon.compat.tacz;

import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;

public final class TaczAttachmentCompat {
    private TaczAttachmentCompat() {}

    /** 1.2.0's VirtualOemAttachment marker represents a gun option, not an owned item. */
    public static ItemStack returnableAttachment(ItemStack installed) {
        var tag = installed.getTag();
        return tag != null && tag.contains("VirtualOemAttachment", Tag.TAG_BYTE)
                && tag.getBoolean("VirtualOemAttachment") ? ItemStack.EMPTY : installed;
    }
}
