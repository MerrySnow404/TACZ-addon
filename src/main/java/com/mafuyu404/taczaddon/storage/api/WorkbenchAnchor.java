package com.mafuyu404.taczaddon.storage.api;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

public record WorkbenchAnchor(
        ResourceKey<Level> dimension,
        BlockPos pos
) {
    public WorkbenchAnchor {
        pos = pos.immutable();
    }
}
