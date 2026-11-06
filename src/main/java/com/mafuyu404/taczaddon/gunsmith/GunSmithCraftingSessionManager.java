package com.mafuyu404.taczaddon.gunsmith;

import com.tacz.guns.block.entity.GunSmithTableBlockEntity;
import com.tacz.guns.inventory.GunSmithTableMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nullable;

public final class GunSmithCraftingSessionManager {

    private static final long PENDING_INTERACTION_TTL_TICKS = 20L;

    private static final Map<UUID, GunSmithCraftingSession> SESSIONS =
            new ConcurrentHashMap<>();

    private static final Map<UUID, PendingGunSmithInteraction>
            PENDING_INTERACTIONS = new ConcurrentHashMap<>();

    private GunSmithCraftingSessionManager() {
    }

    public static void rememberTableInteraction(
            ServerPlayer player,
            BlockPos tablePos,
            GunSmithTableBlockEntity table
    ) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(tablePos, "tablePos");
        Objects.requireNonNull(table, "table");

        ResourceLocation blockId = table.getId();

        if (blockId == null || table.isRemoved()) {
            PENDING_INTERACTIONS.remove(player.getUUID());
            return;
        }

        PENDING_INTERACTIONS.put(
                player.getUUID(),
                new PendingGunSmithInteraction(
                        player.getUUID(),
                        player.level().dimension(),
                        tablePos.immutable(),
                        blockId,
                        player.level().getGameTime()
                )
        );
    }

    public static boolean createSessionFromPending(
            ServerPlayer player,
            GunSmithTableMenu menu
    ) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(menu, "menu");

        UUID playerId = player.getUUID();
        PendingGunSmithInteraction pending =
                PENDING_INTERACTIONS.remove(playerId);

        // Opening a new gunsmith menu invalidates any previous active session.
        SESSIONS.remove(playerId);

        if (pending == null) {
            return false;
        }

        ResourceLocation menuBlockId = menu.getBlockId();
        boolean tableLoaded =
                player.level().isLoaded(pending.tablePos());
        BlockEntity blockEntity = tableLoaded
                ? player.level().getBlockEntity(pending.tablePos())
                : null;
        GunSmithTableBlockEntity table = blockEntity
                instanceof GunSmithTableBlockEntity activeTable
                ? activeTable
                : null;
        long age =
                player.level().getGameTime()
                        - pending.capturedGameTime();
        double distanceSquared =
                player.distanceToSqr(
                        pending.tablePos().getX() + 0.5D,
                        pending.tablePos().getY() + 0.5D,
                        pending.tablePos().getZ() + 0.5D
                );

        boolean valid = isPendingInteractionValid(
                new PendingInteractionValidation(
                        playerId.equals(pending.playerId()),
                        player.level().dimension().equals(
                                pending.dimension()
                        ),
                        age >= 0L
                                && age
                                <= PENDING_INTERACTION_TTL_TICKS,
                        menuBlockId != null
                                && Objects.equals(
                                menuBlockId,
                                pending.tableBlockId()
                        ),
                        tableLoaded,
                        table != null,
                        table != null && !table.isRemoved(),
                        table != null && Objects.equals(
                                table.getId(),
                                pending.tableBlockId()
                        ),
                        menu.stillValid(player),
                        distanceSquared
                                <= GunSmithCraftingSession.MAX_INTERACTION_DISTANCE_SQUARED
                )
        );

        if (!valid) {
            return false;
        }

        createSession(
                player,
                menu.containerId,
                pending.tablePos(),
                pending.tableBlockId()
        );
        return true;
    }

    public static void clearPendingInteraction(UUID playerId) {
        PENDING_INTERACTIONS.remove(playerId);
    }

    public static void clearPlayerState(UUID playerId) {
        SESSIONS.remove(playerId);
        PENDING_INTERACTIONS.remove(playerId);
    }

    public static GunSmithCraftingSession createSession(
            ServerPlayer player,
            int containerId,
            BlockPos tablePos,
            ResourceLocation tableBlockId
    ) {
        GunSmithCraftingSession session =
                new GunSmithCraftingSession(
                        player.getUUID(),
                        containerId,
                        player.level().dimension(),
                        tablePos.immutable(),
                        Objects.requireNonNull(
                                tableBlockId,
                                "tableBlockId"
                        )
                );

        SESSIONS.put(player.getUUID(), session);
        return session;
    }

    @Nullable
    public static GunSmithCraftingSession getSession(UUID playerId) {
        return SESSIONS.get(playerId);
    }

    public static void removeSession(UUID playerId) {
        SESSIONS.remove(playerId);
    }

    public static void removeSession(
            UUID playerId,
            int containerId
    ) {
        SESSIONS.computeIfPresent(
                playerId,
                (ignored, session) ->
                        session.getContainerId() == containerId
                                ? null
                                : session
        );
    }

    public static void removeSession(
            UUID playerId,
            GunSmithCraftingSession session
    ) {
        SESSIONS.remove(playerId, session);
    }

    public static void removeAll() {
        SESSIONS.clear();
        PENDING_INTERACTIONS.clear();
    }

    static boolean isPendingInteractionValid(
            PendingInteractionValidation state
    ) {
        return state.playerMatches()
                && state.dimensionMatches()
                && state.withinTtl()
                && state.menuDefinitionMatches()
                && state.tableLoaded()
                && state.expectedTableEntityPresent()
                && state.tableEntityActive()
                && state.tableDefinitionMatches()
                && state.menuStillValid()
                && state.withinDistance();
    }

    record PendingGunSmithInteraction(
            UUID playerId,
            ResourceKey<Level> dimension,
            BlockPos tablePos,
            ResourceLocation tableBlockId,
            long capturedGameTime
    ) {
    }

    record PendingInteractionValidation(
            boolean playerMatches,
            boolean dimensionMatches,
            boolean withinTtl,
            boolean menuDefinitionMatches,
            boolean tableLoaded,
            boolean expectedTableEntityPresent,
            boolean tableEntityActive,
            boolean tableDefinitionMatches,
            boolean menuStillValid,
            boolean withinDistance
    ) {
    }

    /**
     * Policy for packets that reference a gunsmith container.
     *
     * A request for an old container must never delete the player's current
     * session. A request that matches the current container may delete that
     * matching session when its server-side structural validation fails.
     */
    public static SessionRequestDecision evaluateRequest(
            @Nullable GunSmithCraftingSession session,
            int suppliedContainerId,
            boolean structurallyValid
    ) {
        if (session == null) {
            return SessionRequestDecision.REJECTED_NO_SESSION;
        }
        if (session.getContainerId() != suppliedContainerId) {
            return SessionRequestDecision.REJECTED_STALE_CONTAINER;
        }
        return structurallyValid
                ? SessionRequestDecision.ACCEPTED
                : SessionRequestDecision.REJECTED_INVALID_MATCHING_SESSION;
    }

    public enum SessionRequestDecision {
        REJECTED_NO_SESSION(false, false),
        REJECTED_STALE_CONTAINER(false, false),
        REJECTED_INVALID_MATCHING_SESSION(false, true),
        ACCEPTED(true, false);

        private final boolean accepted;
        private final boolean removeMatchingSession;

        SessionRequestDecision(
                boolean accepted,
                boolean removeMatchingSession
        ) {
            this.accepted = accepted;
            this.removeMatchingSession = removeMatchingSession;
        }

        public boolean accepted() {
            return this.accepted;
        }

        public boolean shouldRemoveMatchingSession() {
            return this.removeMatchingSession;
        }
    }

}
