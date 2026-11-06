package com.mafuyu404.taczaddon.gunsmith;

import com.mafuyu404.taczaddon.storage.api.CraftingItemSource;
import com.mafuyu404.taczaddon.storage.api.CraftingSourceKey;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class GunSmithCraftingSourcesDedupTest {
    @Test
    void sameBackendExposedThroughTwoPositionsContributesOnce() {
        Object backend = new Object();
        ArrayList<CraftingItemSource> sources = new ArrayList<>();
        ArrayList<ItemStack> externalStacks = new ArrayList<>();
        LinkedKeys sourceKeys = new LinkedKeys();
        Set<Object> backendIdentities =
                Collections.newSetFromMap(new IdentityHashMap<>());

        assertTrue(GunSmithCraftingSources.addUniqueSource(
                sources,
                externalStacks,
                sourceKeys,
                backendIdentities,
                fakeSource(new BlockPos(1, 0, 0), backend),
                List.of(),
                256
        ));
        assertFalse(GunSmithCraftingSources.addUniqueSource(
                sources,
                externalStacks,
                sourceKeys,
                backendIdentities,
                fakeSource(new BlockPos(2, 0, 0), backend),
                List.of(),
                256
        ));

        assertEquals(1, sources.size());
        assertEquals(0, externalStacks.size());
        assertEquals(1, sourceKeys.size());
    }

    @Test
    void unrelatedBackendsAreNotDeduplicatedByEqualContents() {
        Object backendA = new Object();
        Object backendB = new Object();
        ArrayList<CraftingItemSource> sources = new ArrayList<>();
        ArrayList<ItemStack> externalStacks = new ArrayList<>();
        LinkedKeys sourceKeys = new LinkedKeys();
        Set<Object> backendIdentities =
                Collections.newSetFromMap(new IdentityHashMap<>());

        assertTrue(GunSmithCraftingSources.addUniqueSource(
                sources,
                externalStacks,
                sourceKeys,
                backendIdentities,
                fakeSource(new BlockPos(1, 0, 0), backendA),
                List.of(),
                256
        ));
        assertTrue(GunSmithCraftingSources.addUniqueSource(
                sources,
                externalStacks,
                sourceKeys,
                backendIdentities,
                fakeSource(new BlockPos(2, 0, 0), backendB),
                List.of(),
                256
        ));

        assertEquals(2, sources.size());
        assertEquals(2, sourceKeys.size());
    }

    private static FakeSource fakeSource(
            BlockPos pos,
            Object backendIdentity
    ) {
        return new FakeSource(
                new CraftingSourceKey.BlockEntity(
                        null,
                        pos
                ),
                backendIdentity
        );
    }

    private static final class LinkedKeys
            extends java.util.LinkedHashSet<CraftingSourceKey> {
    }

    private static final class FakeSource implements CraftingItemSource {
        private final CraftingSourceKey key;
        private final Object backendIdentity;

        private FakeSource(
                CraftingSourceKey key,
                Object backendIdentity
        ) {
            this.key = key;
            this.backendIdentity = backendIdentity;
        }

        @Override
        public CraftingSourceKey key() {
            return this.key;
        }

        @Override
        public Object backendIdentity() {
            return this.backendIdentity;
        }

        @Override
        public int slotCount() {
            return 0;
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            return ItemStack.EMPTY;
        }

        @Override
        public ItemStack extractItem(
                int slot,
                int amount,
                boolean simulate
        ) {
            return ItemStack.EMPTY;
        }

        @Override
        public ItemStack insertItem(
                int slot,
                ItemStack stack,
                boolean simulate
        ) {
            return stack.copy();
        }

        @Override
        public boolean isValid(ServerPlayer player) {
            return true;
        }

        @Override
        public void markChanged() {
        }

        @Override
        public void synchronize(ServerPlayer player) {
        }
    }
}
