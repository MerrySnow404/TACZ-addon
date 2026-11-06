package com.mafuyu404.taczaddon.refit;

import com.mafuyu404.taczaddon.storage.api.CraftingItemSource;
import com.mafuyu404.taczaddon.storage.api.CraftingSourceKey;
import com.mafuyu404.taczaddon.storage.api.RefitSourceLocator;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RefitSourceResolverTest {
    @org.junit.jupiter.api.BeforeAll
    static void bootstrap() throws Exception {
        com.mafuyu404.taczaddon.testutil.MinecraftTestBootstrap.prepare();
    }

    @Test
    void visibleButUnextractableTemplatesAreNotCandidates() {
        var source = fakeSource(BlockPos.ZERO, new ItemStack(Items.IRON_INGOT));
        assertTrue(RefitSourceResolver.extractableStack(source, 0).isEmpty());
    }

    @Test
    void candidateProbeDoesNotConsumeTheRealBackpackStack() {
        var handler = new net.minecraftforge.items.ItemStackHandler(1);
        handler.setStackInSlot(0, new ItemStack(Items.IRON_INGOT, 3));
        var source = new com.mafuyu404.taczaddon.storage.api.BackpackItemSource(
                new CraftingSourceKey.Backpack(UUID.randomUUID(), "main", "", 0, UUID.randomUUID()),
                handler, () -> true, () -> {}, () -> {});
        org.junit.jupiter.api.Assertions.assertEquals(3, RefitSourceResolver.extractableStack(source, 0).getCount());
        org.junit.jupiter.api.Assertions.assertEquals(3, handler.getStackInSlot(0).getCount());
    }

    @Test
    void staleOrForeignBackpackLocatorCannotResolveAnotherSource() {
        UUID owner = UUID.randomUUID();
        UUID contents = UUID.randomUUID();
        var key = new CraftingSourceKey.Backpack(owner, "main", "", 4, contents);
        var source = fakeSource(key, new ItemStack(Items.IRON_INGOT));
        var sources = List.of(source);
        assertSame(source, RefitSourceResolver.findSource(sources, new RefitSourceLocator(key, 0)).orElseThrow());
        for (var stale : List.of(
                new CraftingSourceKey.Backpack(owner, "main", "", 5, contents),
                new CraftingSourceKey.Backpack(owner, "curios", "back", 4, contents),
                new CraftingSourceKey.Backpack(owner, "main", "", 4, UUID.randomUUID()),
                new CraftingSourceKey.Backpack(UUID.randomUUID(), "main", "", 4, contents))) {
            assertTrue(RefitSourceResolver.findSource(sources, new RefitSourceLocator(stale, 0)).isEmpty());
        }
    }
    @Test
    void identicalStacksInDifferentSourcesStayIndependent() {
        ItemStack scope = new ItemStack(Items.IRON_INGOT);
        CraftingItemSource sourceA = fakeSource(
                new BlockPos(1, 64, 1),
                scope.copy()
        );
        CraftingItemSource sourceB = fakeSource(
                new BlockPos(2, 64, 1),
                scope.copy()
        );

        List<CraftingItemSource> sources =
                List.of(sourceA, sourceB);

        RefitSourceLocator locator = new RefitSourceLocator(
                Level.OVERWORLD,
                new BlockPos(2, 64, 1),
                0
        );
        assertTrue(RefitSourceResolver.findSource(
                sources,
                locator
        ).isPresent());
        assertSame(
                sourceB,
                RefitSourceResolver.findSource(sources, locator).get()
        );
    }

    private static CraftingItemSource fakeSource(
            BlockPos pos,
            ItemStack stack
    ) {
        return fakeSource(new CraftingSourceKey.BlockEntity(Level.OVERWORLD, pos), stack);
    }

    private static CraftingItemSource fakeSource(CraftingSourceKey key, ItemStack stack) {
        return new CraftingItemSource() {
            @Override
            public CraftingSourceKey key() {
                return key;
            }

            @Override
            public int slotCount() {
                return 1;
            }

            @Override
            public ItemStack getStackInSlot(int slot) {
                return slot == 0 ? stack.copy() : ItemStack.EMPTY;
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
        };
    }
}
