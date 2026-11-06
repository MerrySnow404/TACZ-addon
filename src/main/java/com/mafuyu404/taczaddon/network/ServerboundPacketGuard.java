package com.mafuyu404.taczaddon.network;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class ServerboundPacketGuard {
    private static final Map<Key, Long> LAST_REQUEST_TICK = new HashMap<>();

    private ServerboundPacketGuard() {}

    public static boolean isRateLimited(
            ServerPlayer player, ResourceLocation packetId, int cooldownTicks) {
        long now = player.level().getGameTime();
        Key key = new Key(player.getUUID(), packetId);
        Long last = LAST_REQUEST_TICK.get(key);
        if (last != null && now - last < cooldownTicks) {
            return true;
        }
        LAST_REQUEST_TICK.put(key, now);
        return false;
    }

    private record Key(UUID playerId, ResourceLocation packetId) {}
}
