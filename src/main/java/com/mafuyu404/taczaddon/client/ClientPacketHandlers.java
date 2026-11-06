package com.mafuyu404.taczaddon.client;

import com.mafuyu404.taczaddon.client.gunsmith.ClientGunSmithPacketHandler;
import com.mafuyu404.taczaddon.client.refit.ClientAttachmentDetailRuleHandler;
import com.mafuyu404.taczaddon.client.refit.ClientLiberateAttachmentHandler;
import com.mafuyu404.taczaddon.client.refit.ClientRefitPacketHandler;
import com.mafuyu404.taczaddon.network.AttachmentDetailRuleStatePacket;
import com.mafuyu404.taczaddon.network.ClientPacketReceiver;
import com.mafuyu404.taczaddon.network.GunSmithCraftResultPacket;
import com.mafuyu404.taczaddon.network.GunSmithSourceSnapshotPacket;
import com.mafuyu404.taczaddon.network.LiberateAttachmentStatePacket;
import com.mafuyu404.taczaddon.network.RefitSourceSnapshotPacket;
import com.mafuyu404.taczaddon.network.ServerFeatureConfigSyncPacket;

public final class ClientPacketHandlers implements ClientPacketReceiver {
    @Override
    public void receive(GunSmithCraftResultPacket message) { ClientGunSmithPacketHandler.handleCraftResult(message); }
    @Override
    public void receive(GunSmithSourceSnapshotPacket message) { ClientGunSmithPacketHandler.handleSourceSnapshot(message); }
    @Override
    public void receive(RefitSourceSnapshotPacket message) { ClientRefitPacketHandler.handleSourceSnapshot(message); }
    @Override
    public void receive(ServerFeatureConfigSyncPacket message) { ClientGunSmithPacketHandler.handleConfigSync(message); }
    @Override
    public void receive(AttachmentDetailRuleStatePacket message) { ClientAttachmentDetailRuleHandler.handle(message.enabled()); }
    @Override
    public void receive(LiberateAttachmentStatePacket message) { ClientLiberateAttachmentHandler.handle(message.enabled()); }
}
