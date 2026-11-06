package com.mafuyu404.taczaddon.network.server;

import com.mafuyu404.taczaddon.gunsmith.GunSmithCraftingSession;
import com.mafuyu404.taczaddon.gunsmith.GunSmithCraftingSessionManager;
import com.mafuyu404.taczaddon.gunsmith.GunSmithCraftingSources;
import com.mafuyu404.taczaddon.gunsmith.GunSmithMenuAccess;
import com.mafuyu404.taczaddon.network.GunSmithSourceRefreshRequestPacket;
import com.mafuyu404.taczaddon.network.GunSmithSourceSnapshotPacket;
import com.mafuyu404.taczaddon.network.NetworkHandler;
import com.mafuyu404.taczaddon.storage.WorkbenchAnchorRegistry;
import com.mafuyu404.taczaddon.storage.api.CraftingItemSource;
import com.mafuyu404.taczaddon.storage.api.WorkbenchAnchor;

import com.mojang.logging.LogUtils;
import com.tacz.guns.crafting.GunSmithTableRecipe;
import com.tacz.guns.inventory.GunSmithTableMenu;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import org.slf4j.Logger;

import java.util.List;
import java.util.function.Supplier;
import javax.annotation.Nullable;

public final class GunSmithSourceRefreshRequestHandler {
    private GunSmithSourceRefreshRequestHandler() {}
    private static final Logger LOGGER = LogUtils.getLogger();
    public static void handle(
            GunSmithSourceRefreshRequestPacket message,
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
            GunSmithSourceRefreshRequestPacket message,
            @Nullable ServerPlayer player
    ) {
        if (player == null) {
            return;
        }

        GunSmithCraftingSession session =
                GunSmithCraftingSessionManager.getSession(
                        player.getUUID()
                );

        boolean structurallyValid =
                session != null
                        && session.validate(
                        player,
                        message.containerId()
                );
        GunSmithCraftingSessionManager.SessionRequestDecision decision =
                GunSmithCraftingSessionManager.evaluateRequest(
                        session,
                        message.containerId(),
                        structurallyValid
                );

        if (decision.shouldRemoveMatchingSession()) {
            GunSmithCraftingSessionManager.removeSession(
                    player.getUUID(),
                    message.containerId()
            );
        }

        /*
         * A legitimate menu existing is independent from external source
         * authorization: without a server-resolved workbench anchor the client
         * keeps the native crafting path and external sources stay closed.
         */
        boolean authorized = decision.accepted()
                && resolveAnchor(player) != null;

        if (!decision.accepted()) {

            NetworkHandler.sendToClient(
                    player,
                    new GunSmithSourceSnapshotPacket(
                            message.containerId(),
                            message.requestId(),
                            0L,
                            false,
                            false,
                            List.of(),
                            new int[0]
                    )
            );
            return;
        }

        if (!session.acceptRefreshRequestId(message.requestId())) {
            return;
        }

        GunSmithCraftingSources.ResolvedSources resolved;
        try {
            resolved =
                    GunSmithCraftingSources.resolve(player, session);
        } catch (RuntimeException exception) {
            LOGGER.error(
                    "Gunsmith source refresh failed for player {}",
                    player.getGameProfile().getName(),
                    exception
            );
            NetworkHandler.sendToClient(
                    player,
                    new GunSmithSourceSnapshotPacket(
                            message.containerId(),
                            message.requestId(),
                            session.getSourceRevision(),
                            List.of()
                    )
            );
            return;
        }

        NetworkHandler.sendToClient(
                player,
                new GunSmithSourceSnapshotPacket(
                        message.containerId(),
                        message.requestId(),
                        session.getSourceRevision(),
                        authorized,
                        resolved.displayTruncated(),
                        resolved.externalStacks(),
                        aggregateCounts(player, message.recipeId(), resolved)
                )
        );
    }

    /**
     * @return the server-resolved anchor for the current menu, or null when
     *         this menu has no trustworthy anchor
     */
    @Nullable
    private static WorkbenchAnchor resolveAnchor(ServerPlayer player) {
        return WorkbenchAnchorRegistry
                .resolve(player, player.containerMenu)
                .orElse(null);
    }

    /**
     * Aggregated per-input counts for the recipe the client is showing.
     *
     * <p>The recipe is re-resolved through TaCZ's own {@code getRecipe}, so
     * the default-table exception, filters and tab membership still apply.
     */
    private static int[] aggregateCounts(
            ServerPlayer player,
            @Nullable ResourceLocation recipeId,
            GunSmithCraftingSources.ResolvedSources resolved
    ) {
        if (recipeId == null
                || !(player.containerMenu
                instanceof GunSmithTableMenu menu)
                || !(menu
                instanceof GunSmithMenuAccess menuAccess)) {
            return new int[0];
        }

        GunSmithTableRecipe recipe = menuAccess
                .taczaddon$invokeGetRecipe(
                        recipeId,
                        player.level().getRecipeManager()
                );
        if (recipe == null) {
            return new int[0];
        }

        List<CraftingItemSource> sources = resolved.sources();
        return GunSmithCraftingSources.countIngredients(
                recipe,
                sources
        );
    }

}
