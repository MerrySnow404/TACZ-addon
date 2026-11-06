package com.mafuyu404.taczaddon.storage.api;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;

import java.util.Optional;

/**
 * Optional adapter for a fully custom workbench whose block, block entity, and
 * menu do not inherit TaCZ's standard gunsmith table contract.
 */
@FunctionalInterface
public interface WorkbenchAnchorProvider {
    Optional<WorkbenchAnchor> resolve(
            ServerPlayer player,
            AbstractContainerMenu menu
    );
}
