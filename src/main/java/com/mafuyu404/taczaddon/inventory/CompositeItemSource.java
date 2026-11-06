package com.mafuyu404.taczaddon.inventory;

import net.neoforged.neoforge.items.IItemHandler;

public record CompositeItemSource(IItemHandler handler, int slot, String sourceType) {}
