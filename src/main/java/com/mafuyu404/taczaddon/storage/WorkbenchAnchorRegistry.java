package com.mafuyu404.taczaddon.storage;

import com.mafuyu404.taczaddon.storage.api.WorkbenchAnchor;
import com.mafuyu404.taczaddon.storage.api.WorkbenchAnchorProvider;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

public final class WorkbenchAnchorRegistry {
    private static final List<WorkbenchAnchorProvider> PROVIDERS =
            new CopyOnWriteArrayList<>();

    private WorkbenchAnchorRegistry() {
    }

    public static void register(WorkbenchAnchorProvider provider) {
        PROVIDERS.add(provider);
    }

    public static Optional<WorkbenchAnchor> resolve(
            ServerPlayer player,
            AbstractContainerMenu menu
    ) {
        for (WorkbenchAnchorProvider provider : PROVIDERS) {
            Optional<WorkbenchAnchor> anchor =
                    provider.resolve(player, menu);
            if (anchor.isPresent()) {
                return anchor;
            }
        }
        return Optional.empty();
    }

    static List<WorkbenchAnchorProvider> providers() {
        return List.copyOf(PROVIDERS);
    }
}
