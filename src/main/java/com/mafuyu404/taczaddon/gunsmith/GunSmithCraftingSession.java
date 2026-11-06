package com.mafuyu404.taczaddon.gunsmith;

import com.mafuyu404.taczaddon.storage.api.CraftingSourceKey;

import com.tacz.guns.block.entity.GunSmithTableBlockEntity;
import com.tacz.guns.inventory.GunSmithTableMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

public final class GunSmithCraftingSession {
    static final double MAX_INTERACTION_DISTANCE_SQUARED = 64.0D;

    private final UUID playerId;
    private final int containerId;
    private final ResourceKey<Level> dimension;
    private final BlockPos tablePos;
    private final ResourceLocation tableBlockId;

    private long lastAcceptedCraftRequestId = -1L;
    private long lastAcceptedRefreshRequestId = -1L;
    private List<CraftingSourceKey> sourceKeys = List.of();

    /*
     * Structural/addon-owned mutation revision only. It does not detect
     * arbitrary external inventory changes; every craft and refresh
     * re-resolves live server sources.
     */
    private long sourceRevision;

    GunSmithCraftingSession(
            UUID playerId,
            int containerId,
            ResourceKey<Level> dimension,
            BlockPos tablePos,
            ResourceLocation tableBlockId
    ) {
        this.playerId = playerId;
        this.containerId = containerId;
        this.dimension = dimension;
        this.tablePos = tablePos;
        this.tableBlockId = tableBlockId;
    }

    public boolean validate(
            ServerPlayer player,
            int suppliedContainerId
    ) {
        GunSmithTableMenu menu = player.containerMenu
                instanceof GunSmithTableMenu activeMenu
                ? activeMenu
                : null;

        boolean tableLoaded =
                player.level().isLoaded(this.tablePos);
        BlockEntity blockEntity = tableLoaded
                ? player.level().getBlockEntity(this.tablePos)
                : null;
        GunSmithTableBlockEntity table = blockEntity
                instanceof GunSmithTableBlockEntity activeTable
                ? activeTable
                : null;

        double distanceSquared =
                player.distanceToSqr(
                        this.tablePos.getX() + 0.5D,
                        this.tablePos.getY() + 0.5D,
                        this.tablePos.getZ() + 0.5D
                );
        return isLifecycleValid(new SessionLifecycleState(
                player.getUUID().equals(this.playerId),
                suppliedContainerId == this.containerId,
                player.level().dimension().equals(this.dimension),
                menu != null,
                menu != null
                        && menu.containerId == suppliedContainerId
                        && menu.containerId == this.containerId,
                menu != null && Objects.equals(
                        menu.getBlockId(),
                        this.tableBlockId
                ),
                menu != null && menu.stillValid(player),
                tableLoaded,
                table != null,
                table != null && !table.isRemoved(),
                table != null && Objects.equals(
                        table.getId(),
                        this.tableBlockId
                ),
                distanceSquared
        ));
    }

    public synchronized boolean acceptCraftRequestId(
            long requestId
    ) {
        if (requestId < 0L
                || requestId <= this.lastAcceptedCraftRequestId) {
            return false;
        }

        this.lastAcceptedCraftRequestId = requestId;
        return true;
    }

    public synchronized boolean acceptRefreshRequestId(
            long requestId
    ) {
        if (requestId < 0L
                || requestId
                <= this.lastAcceptedRefreshRequestId) {
            return false;
        }

        this.lastAcceptedRefreshRequestId = requestId;
        return true;
    }

    public synchronized void updateSourceKeys(
            List<CraftingSourceKey> keys
    ) {
        List<CraftingSourceKey> copy = List.copyOf(keys);
        if (!this.sourceKeys.equals(copy)) {
            this.sourceKeys = copy;
            // Structural source-set change, not a content fingerprint.
            this.sourceRevision++;
        }
    }

    public synchronized void markSourcesChanged() {
        // Addon-owned craft mutation marker, not an external content scan.
        this.sourceRevision++;
    }

    public UUID getPlayerId() {
        return this.playerId;
    }

    public int getContainerId() {
        return this.containerId;
    }

    public ResourceKey<Level> getDimension() {
        return this.dimension;
    }

    public BlockPos getTablePos() {
        return this.tablePos;
    }

    public ResourceLocation getTableBlockId() {
        return this.tableBlockId;
    }

    public synchronized List<CraftingSourceKey> getSourceKeys() {
        return this.sourceKeys;
    }

    public synchronized long getSourceRevision() {
        return this.sourceRevision;
    }
    /**
     * The complete lifetime policy for a gunsmith session. Elapsed game time
     * is deliberately absent: an idle session remains valid while its real
     * server-side menu and table relationship remains valid.
     */

    static boolean isLifecycleValid(SessionLifecycleState state) {
        return state.playerMatches()
                && state.suppliedContainerMatches()
                && state.dimensionMatches()
                && state.gunsmithMenuOpen()
                && state.activeMenuContainerMatches()
                && state.menuTableDefinitionMatches()
                && state.menuStillValid()
                && state.tableLoaded()
                && state.expectedTableEntityPresent()
                && state.tableEntityActive()
                && state.tableDefinitionMatches()
                && state.distanceSquared()
                <= MAX_INTERACTION_DISTANCE_SQUARED;
    }
    record SessionLifecycleState(
            boolean playerMatches,
            boolean suppliedContainerMatches,
            boolean dimensionMatches,
            boolean gunsmithMenuOpen,
            boolean activeMenuContainerMatches,
            boolean menuTableDefinitionMatches,
            boolean menuStillValid,
            boolean tableLoaded,
            boolean expectedTableEntityPresent,
            boolean tableEntityActive,
            boolean tableDefinitionMatches,
            double distanceSquared
    ) {
    }
}
