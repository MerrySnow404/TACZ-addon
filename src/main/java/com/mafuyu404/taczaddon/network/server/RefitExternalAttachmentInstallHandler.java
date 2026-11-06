package com.mafuyu404.taczaddon.network.server;

import com.mafuyu404.taczaddon.network.RefitExternalAttachmentInstallPacket;
import com.mafuyu404.taczaddon.refit.RefitExternalInstallService;
import com.mafuyu404.taczaddon.refit.RefitInstallRequest;

import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public final class RefitExternalAttachmentInstallHandler {
    private RefitExternalAttachmentInstallHandler() {}
    public static void handle(
            RefitExternalAttachmentInstallPacket message,
            Supplier<NetworkEvent.Context> contextSupplier
    ) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() ->
                RefitExternalInstallService.handle(
                        new RefitInstallRequest(message.locator(), message.expectedAttachmentId(),
                                message.expectedType(), message.gunSlotIndex(), message.snapshotRequestId()),
                        context.getSender()
                )
        );
        context.setPacketHandled(true);
    }
}
