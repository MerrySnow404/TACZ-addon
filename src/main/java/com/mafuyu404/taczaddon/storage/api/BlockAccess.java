package com.mafuyu404.taczaddon.storage.api;

import java.util.List;
import java.util.function.BooleanSupplier;

public record BlockAccess(List<PhysicalSlot> slots, BooleanSupplier valid) {
    public BlockAccess { slots = List.copyOf(slots); }
}
