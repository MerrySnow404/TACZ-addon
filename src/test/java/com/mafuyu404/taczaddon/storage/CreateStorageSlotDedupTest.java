package com.mafuyu404.taczaddon.storage;

import com.mafuyu404.taczaddon.storage.ContainerItemSource;

import com.mafuyu404.taczaddon.storage.api.PhysicalSlot;
import com.mafuyu404.taczaddon.testutil.MinecraftTestBootstrap;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CreateStorageSlotDedupTest {
    @BeforeAll
    static void bootstrap() throws Exception { MinecraftTestBootstrap.prepare(); }

    @Test
    void complementaryFilteredEndpointsAndDirectBoxCountEachPhysicalSlotOnce() {
        var a = new ChestBlockEntity(BlockPos.ZERO, Blocks.CHEST.defaultBlockState());
        var b = new ChestBlockEntity(new BlockPos(1, 0, 0), Blocks.CHEST.defaultBlockState());
        var seen = new HashSet<PhysicalSlot>();
        // First endpoint exposes A; a second exposes A and B. Keep B at its original slot 1.
        assertTrue(ContainerItemSource.excludeClaimedSlots(List.of(new PhysicalSlot(a, 0)), seen).isEmpty());
        var second = ContainerItemSource.excludeClaimedSlots(List.of(new PhysicalSlot(a, 0), new PhysicalSlot(b, 0)), seen);
        assertTrue(second.get(0));
        assertFalse(second.get(1));
        assertTrue(ContainerItemSource.excludeClaimedSlots(List.of(new PhysicalSlot(b, 0)), seen).get(0));
        assertEquals(2, seen.size());
    }

    @Test
    void directBoxFirstDoesNotHideOtherNetworkMembersOrConsumeGhostSlots() {
        var a = new ChestBlockEntity(BlockPos.ZERO, Blocks.CHEST.defaultBlockState());
        var seen = new HashSet<PhysicalSlot>();
        var direct = ContainerItemSource.excludeClaimedSlots(List.of(new PhysicalSlot(a, 0), new PhysicalSlot(a, -1)), seen);
        assertFalse(direct.get(0));
        assertTrue(direct.get(1));
        var otherSlot = ContainerItemSource.excludeClaimedSlots(List.of(new PhysicalSlot(a, 1)), seen);
        assertFalse(otherSlot.get(0));
        // Replacing a box at the same coordinates is a different backend.
        var replacement = new ChestBlockEntity(BlockPos.ZERO, Blocks.CHEST.defaultBlockState());
        assertTrue(ContainerItemSource.excludeClaimedSlots(List.of(new PhysicalSlot(replacement, 0)), seen).isEmpty());
    }
}
