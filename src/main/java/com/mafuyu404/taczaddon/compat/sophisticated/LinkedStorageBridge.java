package com.mafuyu404.taczaddon.compat.sophisticated;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.UUID;

interface LinkedStorageBridge {
    default void requestLocatedSnapshot(UUID groupId, String handlerName, String identifier, int slot, boolean refresh) {
        if (refresh) refreshSnapshot(groupId);
        else requestSnapshot(groupId);
    }
    EndpointResolution resolve(
            ItemStack stack
    );

    /**
     * Force a complete bootstrap snapshot.
     */
    void requestSnapshot(
            UUID groupId
    );

    /**
     * Ask whether the server revision changed.
     *
     * Old/no-op bridges can simply delegate to requestSnapshot because
     * they never actually send anything.
     */
    default void refreshSnapshot(
            UUID groupId
    ) {
        requestSnapshot(groupId);
    }

    void sendSnapshot(
            ServerPlayer player,
            UUID groupId
    );
}
