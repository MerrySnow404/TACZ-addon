package com.mafuyu404.taczaddon.inventory;

import static org.junit.jupiter.api.Assertions.*;

import net.minecraft.core.BlockPos;

import org.junit.jupiter.api.Test;

class NearbyInventorySourceResolverTest {
    @Test
    void includesRadiusBoundaryAndOnlyAdjacentVerticalLayers() {
        BlockPos anchor = new BlockPos(100, 63, -200);
        for (int x = -4; x <= 4; x++)
            for (int y = -2; y <= 2; y++)
                for (int z = -4; z <= 4; z++) {
                    assertEquals(
                            Math.abs(x) <= 3 && Math.abs(y) <= 1 && Math.abs(z) <= 3,
                            NearbyInventorySourceResolver.inRange(
                                    anchor, anchor.offset(x, y, z), 3, 1));
                }
    }

    @Test
    void extremeLocatorDoesNotOverflowIntoRange() {
        assertFalse(
                NearbyInventorySourceResolver.inRange(
                        new BlockPos(Integer.MIN_VALUE, 0, 0),
                        new BlockPos(Integer.MAX_VALUE, 0, 0),
                        3,
                        1));
    }
}
