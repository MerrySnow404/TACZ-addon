package com.mafuyu404.taczaddon.network.server;

import com.mafuyu404.taczaddon.network.NetworkHandler;
import com.mafuyu404.taczaddon.network.RefitSourceRefreshRequestPacket;
import com.mafuyu404.taczaddon.network.RefitSourceSnapshotPacket;
import com.mafuyu404.taczaddon.refit.RefitSourceResolver;

import com.mojang.logging.LogUtils;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import org.slf4j.Logger;

import java.util.function.Supplier;
import javax.annotation.Nullable;

public final class RefitSourceRefreshRequestHandler {
    private RefitSourceRefreshRequestHandler() {}
    private static final Logger LOGGER = LogUtils.getLogger();
    public static void handle(
            RefitSourceRefreshRequestPacket message,
            Supplier<NetworkEvent.Context> contextSupplier
    ) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> handleOnServer(
                message,
                context.getSender()
        ));
        context.setPacketHandled(true);
    }

    private static void handleOnServer(
            RefitSourceRefreshRequestPacket message,
            @Nullable ServerPlayer player
    ) {
        if (player == null) {
            return;
        }

        try {
            NetworkHandler.sendToClient(
                    player,
                    new RefitSourceSnapshotPacket(
                            message.requestId(),
                            RefitSourceResolver
                                    .resolveExternalCandidates(player)
                    )
            );
        } catch (RuntimeException | LinkageError failure) {
            LOGGER.error(
                    "Refit source refresh failed for player {}",
                    player.getGameProfile().getName(),
                    failure
            );

            NetworkHandler.sendToClient(
                    player,
                    new RefitSourceSnapshotPacket(
                            message.requestId(),
                            java.util.List.of()
                    )
            );
        }
    }

}
