package com.mafuyu404.taczaddon.network;

public interface ClientPacketReceiver {
    void receive(GunSmithCraftResultPacket message);
    void receive(GunSmithSourceSnapshotPacket message);
    void receive(RefitSourceSnapshotPacket message);
    void receive(ServerFeatureConfigSyncPacket message);
    void receive(AttachmentDetailRuleStatePacket message);
    void receive(LiberateAttachmentStatePacket message);
}
