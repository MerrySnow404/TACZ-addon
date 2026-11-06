package com.mafuyu404.taczaddon.storage.api;

import net.minecraft.world.level.block.entity.BlockEntity;

public record PhysicalSlot(BlockEntity owner, int slot) {}
