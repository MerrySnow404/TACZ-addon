package com.mafuyu404.taczaddon.refit;

import static org.junit.jupiter.api.Assertions.*;

import com.mafuyu404.taczaddon.inventory.NearbyInventorySourceResolver.SourceKind;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

import org.junit.jupiter.api.Test;

import java.util.UUID;

class RefitSourceLocatorTest {
    private RefitSourceLocator carried(String inventory, int slot, UUID id) {
        return new RefitSourceLocator(
                Level.OVERWORLD.location(),
                BlockPos.ZERO,
                SourceKind.SOPHISTICATED_BACKPACK,
                4,
                inventory,
                "main",
                slot,
                id);
    }

    @Test
    void movedOrReplacedBackpackCannotMatchAnOldRequest() {
        UUID id = UUID.randomUUID();
        var old = carried("inventory", 1, id);
        assertFalse(old.sameSource(carried("inventory", 2, id)));
        assertFalse(old.sameSource(carried("curios", 1, id)));
        assertFalse(old.sameSource(carried("inventory", 1, UUID.randomUUID())));
        assertTrue(old.sameSource(old.withSlot(9)));
    }

    @Test
    void blockAndCarriedNamespacesCannotCollide() {
        var block =
                new RefitSourceLocator(
                        Level.OVERWORLD.location(),
                        BlockPos.ZERO,
                        SourceKind.SOPHISTICATED_BACKPACK,
                        4);
        assertFalse(block.carried());
        assertFalse(block.sameSource(carried("inventory", 1, UUID.randomUUID())));
    }
}
