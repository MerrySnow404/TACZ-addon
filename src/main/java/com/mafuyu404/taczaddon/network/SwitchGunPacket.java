package com.mafuyu404.taczaddon.network;

import net.minecraft.network.FriendlyByteBuf;

public class SwitchGunPacket {
    private final int slot;

    public SwitchGunPacket(int slot) {
        this.slot = slot;
    }

    public static void encode(SwitchGunPacket msg, FriendlyByteBuf buffer) {
        buffer.writeInt(msg.slot);
    }

    public static SwitchGunPacket decode(FriendlyByteBuf buffer) {
        return new SwitchGunPacket(buffer.readInt());
    }

    public int slot() { return slot; }
}
