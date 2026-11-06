package com.mafuyu404.taczaddon.storage.api;

import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RefitSourceLocatorTest {
    @Test
    void backpackRoundTripPreservesOwnerLocationAndContentsIdentity() {
        var locator = new RefitSourceLocator(new CraftingSourceKey.Backpack(
                UUID.randomUUID(), "curios", "back", 2, UUID.randomUUID()), 7);
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            locator.encode(buffer);
            assertEquals(locator, RefitSourceLocator.decode(buffer));
            assertEquals(0, buffer.readableBytes());
        } finally {
            buffer.release();
        }
    }

    @Test
    void rejectsUnknownSourceType() {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            buffer.writeByte(42);
            assertThrows(IllegalArgumentException.class, () -> RefitSourceLocator.decode(buffer));
        } finally {
            buffer.release();
        }
    }
    @Test
    void roundTripsDimensionPositionAndSlot() {
        RefitSourceLocator locator = new RefitSourceLocator(
                Level.OVERWORLD,
                new BlockPos(10, 70, -5),
                3
        );

        FriendlyByteBuf buffer = new FriendlyByteBuf(
                Unpooled.buffer()
        );
        locator.encode(buffer);
        RefitSourceLocator decoded =
                RefitSourceLocator.decode(buffer);

        assertEquals(locator, decoded);
        assertEquals(0, buffer.readableBytes());
    }
}
