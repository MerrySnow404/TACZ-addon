package com.mafuyu404.taczaddon.inventory;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ContainerReaderState {
    private static final Map<UUID, Snapshot> SNAPSHOTS = new ConcurrentHashMap<>();

    private ContainerReaderState() {}

    public static void setSnapshot(
            ServerPlayer player,
            List<BlockPos> containerPositions,
            List<BlockPos> backpackPositions) {
        SNAPSHOTS.put(
                player.getUUID(),
                new Snapshot(List.copyOf(containerPositions), List.copyOf(backpackPositions)));
    }

    public static Optional<Snapshot> getSnapshot(Player player) {
        return Optional.ofNullable(SNAPSHOTS.get(player.getUUID()));
    }

    public static void clear(Player player) {
        SNAPSHOTS.remove(player.getUUID());
    }

    public record Snapshot(List<BlockPos> containerPositions, List<BlockPos> backpackPositions) {}
}
