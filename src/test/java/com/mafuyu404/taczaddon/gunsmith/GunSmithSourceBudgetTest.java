package com.mafuyu404.taczaddon.gunsmith;

import com.mafuyu404.taczaddon.storage.api.CraftingItemSource;
import com.mafuyu404.taczaddon.storage.api.CraftingSourceKey;
import com.mafuyu404.taczaddon.testutil.MinecraftTestBootstrap;
import com.tacz.guns.crafting.GunSmithTableIngredient;
import com.tacz.guns.crafting.GunSmithTableRecipe;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The display budget must never decide material availability.
 */
class GunSmithSourceBudgetTest {

    @BeforeAll
    static void bootstrap() throws Exception {
        MinecraftTestBootstrap.prepare();
    }

    @Test
    void sourceWithMoreEntriesThanTheDisplayBudgetStaysUsable() {
        List<CraftingItemSource> sources = new ArrayList<>();
        List<ItemStack> display = new ArrayList<>();
        Set<CraftingSourceKey> keys = new LinkedHashSet<>();
        Set<Object> identities = Collections.newSetFromMap(
                new IdentityHashMap<>()
        );
        boolean[] truncated = {false};

        assertTrue(GunSmithCraftingSources.addUniqueSource(
                sources,
                display,
                keys,
                identities,
                new FakeSource(0, 257),
                stacks(257),
                256,
                truncated
        ));

        assertEquals(1, sources.size(), "the source itself stays registered");
        assertEquals(256, display.size(), "only the display list is capped");
        assertTrue(truncated[0], "clients can detect the truncated page");

        int[] counts = GunSmithCraftingSources.countIngredients(
                recipe(257),
                sources
        );
        assertEquals(
                257,
                counts[0],
                "a source beyond the display budget still supplies materials"
        );
    }

    @Test
    void aggregateCountsCoverEverySourceBeyondTheDisplayBudget() {
        List<CraftingItemSource> sources = new ArrayList<>();
        List<ItemStack> display = new ArrayList<>();
        Set<CraftingSourceKey> keys = new LinkedHashSet<>();
        Set<Object> identities = Collections.newSetFromMap(
                new IdentityHashMap<>()
        );
        boolean[] truncated = {false};

        GunSmithCraftingSources.addUniqueSource(
                sources,
                display,
                keys,
                identities,
                new FakeSource(0, 300),
                stacks(300),
                256,
                truncated
        );
        GunSmithCraftingSources.addUniqueSource(
                sources,
                display,
                keys,
                identities,
                new FakeSource(1, 40),
                stacks(40),
                256,
                truncated
        );

        assertTrue(truncated[0]);
        assertEquals(2, sources.size());
        assertEquals(
                256,
                display.size(),
                "the packet stays bounded even with several large sources"
        );

        int[] counts = GunSmithCraftingSources.countIngredients(
                recipe(100),
                sources
        );
        assertEquals(
                340,
                counts[0],
                "undisplayed but legal materials still count"
        );
    }

    @Test
    void duplicateBackendsContributeOnceWithoutLosingTheDisplayBudget() {
        Object backend = new Object();
        List<CraftingItemSource> sources = new ArrayList<>();
        List<ItemStack> display = new ArrayList<>();
        Set<CraftingSourceKey> keys = new LinkedHashSet<>();
        Set<Object> identities = Collections.newSetFromMap(
                new IdentityHashMap<>()
        );
        boolean[] truncated = {false};

        assertTrue(GunSmithCraftingSources.addUniqueSource(
                sources,
                display,
                keys,
                identities,
                new FakeSource(0, 10, backend),
                stacks(10),
                256,
                truncated
        ));
        assertFalse(GunSmithCraftingSources.addUniqueSource(
                sources,
                display,
                keys,
                identities,
                new FakeSource(1, 10, backend),
                stacks(10),
                256,
                truncated
        ));

        assertFalse(truncated[0]);
        assertEquals(1, sources.size());
        assertEquals(10, display.size());
    }

    private static List<ItemStack> stacks(int count) {
        ArrayList<ItemStack> stacks = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            stacks.add(new ItemStack(Items.IRON_INGOT));
        }
        return stacks;
    }

    private static GunSmithTableRecipe recipe(int required) {
        return new GunSmithTableRecipe(
                Objects.requireNonNull(ResourceLocation.tryBuild(
                        "taczaddon",
                        "budget_test"
                )),
                null,
                List.of(new GunSmithTableIngredient(
                        Ingredient.of(Items.IRON_INGOT),
                        required
                ))
        );
    }

    private static final class FakeSource implements CraftingItemSource {
        private final CraftingSourceKey key;
        private final List<ItemStack> stacks = new ArrayList<>();
        private final Object identity;

        private FakeSource(int position, int count) {
            this(position, count, null);
        }

        private FakeSource(
                int position,
                int count,
                Object identity
        ) {
            this.key = new CraftingSourceKey.BlockEntity(
                    null,
                    new BlockPos(position, 0, 0)
            );
            this.identity = identity == null ? this : identity;
            this.stacks.add(new ItemStack(Items.IRON_INGOT, count));
        }

        @Override
        public CraftingSourceKey key() {
            return this.key;
        }

        @Override
        public Object backendIdentity() {
            return this.identity;
        }

        @Override
        public int slotCount() {
            return this.stacks.size();
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            return this.stacks.get(slot);
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
            return stack;
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
