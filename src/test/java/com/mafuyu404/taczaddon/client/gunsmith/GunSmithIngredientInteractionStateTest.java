package com.mafuyu404.taczaddon.client.gunsmith;

import com.mafuyu404.taczaddon.testutil.MinecraftTestBootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class GunSmithIngredientInteractionStateTest {
    @BeforeAll
    static void bootstrap() throws Exception {
        MinecraftTestBootstrap.prepare();
    }

    @Test
    void hitboxUsesHalfOpenInterval() {
        GunSmithIngredientInteractionState state =
                new GunSmithIngredientInteractionState();
        state.register(new ItemStack(Items.DIAMOND), 100, 200);

        assertTrue(state.find(100.0, 200.0).isPresent());
        assertTrue(state.find(115.999, 215.999).isPresent());

        assertFalse(state.find(116.0, 215.999).isPresent());
        assertFalse(state.find(115.999, 216.0).isPresent());
        assertFalse(state.find(116.0, 216.0).isPresent());
        assertFalse(state.find(99.999, 200.0).isPresent());
        assertFalse(state.find(100.0, 199.999).isPresent());
    }

    @Test
    void hitboxTopLeftAndInnerCornerUseSpecCoordinates() {
        GunSmithIngredientInteractionState state =
                new GunSmithIngredientInteractionState();
        state.register(new ItemStack(Items.DIAMOND), 10, 20);

        assertTrue(state.find(10.0, 20.0).isPresent());
        assertTrue(state.find(25.999, 35.999).isPresent());

        assertFalse(state.find(26.0, 20.0).isPresent());
        assertFalse(state.find(10.0, 36.0).isPresent());
        assertFalse(state.find(26.0, 36.0).isPresent());
    }

    @Test
    void beginFrameClearsRegisteredTargets() {
        GunSmithIngredientInteractionState state =
                new GunSmithIngredientInteractionState();

        state.beginFrame();
        state.register(new ItemStack(Items.DIAMOND), 10, 10);
        assertTrue(state.find(10.0, 10.0).isPresent());

        state.beginFrame();
        assertFalse(state.find(10.0, 10.0).isPresent());
    }

    @Test
    void registeredTargetHoldsStackSnapshot() {
        GunSmithIngredientInteractionState state =
                new GunSmithIngredientInteractionState();

        ItemStack mutable = new ItemStack(Items.DIAMOND, 3);
        state.register(mutable, 10, 10);
        mutable.setCount(1);

        Optional<ItemStack> found = state.find(10.0, 10.0);
        assertTrue(found.isPresent());
        assertEquals(3, found.get().getCount());
    }

    @Test
    void snapshotSurvivesCountAndTagMutation() {
        GunSmithIngredientInteractionState state =
                new GunSmithIngredientInteractionState();

        ItemStack mutable = new ItemStack(Items.DIAMOND, 2);
        mutable.getOrCreateTag().putString(
                "testKey",
                "original"
        );
        state.register(mutable, 10, 10);

        mutable.setCount(1);
        mutable.getOrCreateTag().putString(
                "testKey",
                "mutated"
        );

        Optional<ItemStack> found = state.find(10.0, 10.0);
        assertTrue(found.isPresent());
        assertEquals(2, found.get().getCount());
        assertEquals(
                "original",
                found.get().getOrCreateTag().getString("testKey")
        );
    }

    @Test
    void multipleTargetsResolveToTheirOwnStacks() {
        GunSmithIngredientInteractionState state =
                new GunSmithIngredientInteractionState();

        state.register(new ItemStack(Items.DIAMOND), 10, 10);
        state.register(new ItemStack(Items.IRON_INGOT), 200, 200);

        assertEquals(
                Items.DIAMOND,
                state.find(10.5, 10.5).orElseThrow().getItem()
        );
        assertEquals(
                Items.IRON_INGOT,
                state.find(200.5, 200.5).orElseThrow().getItem()
        );
        assertFalse(state.find(100.0, 100.0).isPresent());
    }

    @Test
    void adjacentTargetsResolveIndependently() {
        GunSmithIngredientInteractionState state =
                new GunSmithIngredientInteractionState();

        state.register(new ItemStack(Items.DIAMOND), 0, 0);
        state.register(new ItemStack(Items.IRON_INGOT), 32, 0);

        assertEquals(
                Items.DIAMOND,
                state.find(4.0, 4.0).orElseThrow().getItem()
        );
        assertEquals(
                Items.IRON_INGOT,
                state.find(36.0, 4.0).orElseThrow().getItem()
        );
    }

    @Test
    void emptyStackIsNotRegistered() {
        GunSmithIngredientInteractionState state =
                new GunSmithIngredientInteractionState();

        state.register(ItemStack.EMPTY, 10, 10);
        state.register(null, 20, 20);

        assertFalse(state.find(10.0, 10.0).isPresent());
        assertFalse(state.find(20.0, 20.0).isPresent());
    }

    @Test
    void staleTargetDoesNotSurviveIntoNextFrame() {
        GunSmithIngredientInteractionState state =
                new GunSmithIngredientInteractionState();

        state.register(new ItemStack(Items.DIAMOND), 10, 10);
        assertTrue(state.find(10.0, 10.0).isPresent());

        state.beginFrame();
        state.register(new ItemStack(Items.IRON_INGOT), 200, 200);

        assertFalse(state.find(10.0, 10.0).isPresent());
        assertTrue(state.find(200.0, 200.0).isPresent());
    }
}
