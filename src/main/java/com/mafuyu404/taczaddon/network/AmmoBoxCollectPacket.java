package com.mafuyu404.taczaddon.network;

import net.minecraft.network.FriendlyByteBuf;

public class AmmoBoxCollectPacket {
    private final int index;

    public AmmoBoxCollectPacket(int index) {
        this.index = index;
    }

    public static void encode(AmmoBoxCollectPacket msg, FriendlyByteBuf buffer) {
        buffer.writeInt(msg.index);
    }

    public static AmmoBoxCollectPacket decode(FriendlyByteBuf buffer) {
        return new AmmoBoxCollectPacket(buffer.readInt());
    }

    public int index() { return index; }
}
