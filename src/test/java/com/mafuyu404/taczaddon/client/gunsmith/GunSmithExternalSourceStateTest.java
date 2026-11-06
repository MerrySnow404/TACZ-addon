package com.mafuyu404.taczaddon.client.gunsmith;

import com.mafuyu404.taczaddon.client.gunsmith.GunSmithSourceScreenAccess;
import com.mafuyu404.taczaddon.testutil.MinecraftTestBootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class GunSmithExternalSourceStateTest {
    @BeforeAll
    static void bootstrap() throws Exception {
        MinecraftTestBootstrap.prepare();
    }

    @Test
    void initialScreenRefreshCarriesSelectedRecipe() {
        var recipeId =
                net.minecraft.resources.ResourceLocation.tryParse(
                        "test:first_recipe"
                );

        List<net.minecraft.resources.ResourceLocation>
                sentRecipes = new java.util.ArrayList<>();

        GunSmithExternalSourceState state =
                new GunSmithExternalSourceState(
                        (containerId, requestId, sentRecipeId) ->
                                sentRecipes.add(sentRecipeId)
                );

        state.onScreenInit(
                7,
                recipeId
        );

        assertEquals(
                List.of(recipeId),
                sentRecipes
        );
    }

    @Test
    void periodicRefreshKeepsTheSelectedRecipe() {
        var recipeId =
                net.minecraft.resources.ResourceLocation.tryParse(
                        "test:periodic_recipe"
                );

        List<net.minecraft.resources.ResourceLocation>
                sentRecipes = new java.util.ArrayList<>();

        GunSmithExternalSourceState state =
                new GunSmithExternalSourceState(
                        (containerId, requestId, sentRecipeId) ->
                                sentRecipes.add(sentRecipeId)
                );

        state.onScreenInit(
                7,
                recipeId
        );

        assertEquals(
                GunSmithSourceScreenAccess.AcceptResult.UPDATED,
                state.acceptSourceSnapshot(
                        7,
                        1L,
                        1L,
                        true,
                        false,
                        List.of(),
                        new int[]{3, 4}
                )
        );

        for (int tick = 0;
             tick < 30;
             tick++) {
            state.tickSourceRefresh(7);
        }

        assertEquals(
                List.of(
                        recipeId,
                        recipeId
                ),
                sentRecipes
        );
    }

    @Test
    void switchingRecipeInvalidatesOldAggregateAndBindsNewOne() {
        var recipeA =
                net.minecraft.resources.ResourceLocation.tryParse(
                        "test:recipe_a"
                );

        var recipeB =
                net.minecraft.resources.ResourceLocation.tryParse(
                        "test:recipe_b"
                );

        GunSmithExternalSourceState state =
                state();

        state.onScreenInit(
                7,
                recipeA
        );

        assertEquals(
                GunSmithSourceScreenAccess.AcceptResult.UPDATED,
                state.acceptSourceSnapshot(
                        7,
                        1L,
                        10L,
                        true,
                        false,
                        List.of(),
                        new int[]{5, 10}
                )
        );

        assertArrayEquals(
                new int[]{5, 10},
                state.aggregateCountsFor(recipeA)
        );

        assertNull(
                state.aggregateCountsFor(recipeB)
        );

        state.observeRecipe(
                7,
                recipeB
        );

        /*
         * Old recipe data becomes unusable immediately,
         * before the B response arrives.
         */
        assertNull(
                state.aggregateCountsFor(recipeA)
        );

        assertNull(
                state.aggregateCountsFor(recipeB)
        );

        /*
         * Deliberately use exactly the same source revision,
         * stacks and counts as recipe A.
         *
         * Recipe identity alone must make this UPDATED.
         */
        assertEquals(
                GunSmithSourceScreenAccess.AcceptResult.UPDATED,
                state.acceptSourceSnapshot(
                        7,
                        2L,
                        10L,
                        true,
                        false,
                        List.of(),
                        new int[]{5, 10}
                )
        );

        assertNull(
                state.aggregateCountsFor(recipeA)
        );

        assertArrayEquals(
                new int[]{5, 10},
                state.aggregateCountsFor(recipeB)
        );
    }

    @Test
    void staleRecipeResponseCannotReplaceNewerRecipeRequest() {
        var recipeA =
                net.minecraft.resources.ResourceLocation.tryParse(
                        "test:recipe_a"
                );

        var recipeB =
                net.minecraft.resources.ResourceLocation.tryParse(
                        "test:recipe_b"
                );

        GunSmithExternalSourceState state =
                state();

        state.onScreenInit(
                7,
                recipeA
        );

        state.observeRecipe(
                7,
                recipeB
        );

        /*
         * Request 1 belongs to A but request 2 for B superseded it.
         */
        assertEquals(
                GunSmithSourceScreenAccess.AcceptResult.REJECTED,
                state.acceptSourceSnapshot(
                        7,
                        1L,
                        1L,
                        true,
                        false,
                        List.of(),
                        new int[]{99}
                )
        );

        assertEquals(
                GunSmithSourceScreenAccess.AcceptResult.UPDATED,
                state.acceptSourceSnapshot(
                        7,
                        2L,
                        1L,
                        true,
                        false,
                        List.of(),
                        new int[]{7}
                )
        );

        assertNull(
                state.aggregateCountsFor(recipeA)
        );

        assertArrayEquals(
                new int[]{7},
                state.aggregateCountsFor(recipeB)
        );
    }

    @Test
    void unchangedSourceRevisionReturnsUnchanged() {
        GunSmithExternalSourceState state = state();

        state.requestSourceRefresh(7);
        assertEquals(
                GunSmithSourceScreenAccess.AcceptResult.UPDATED,
                state.acceptSourceSnapshot(
                        7,
                        1L,
                        1L,
                        List.of()
                )
        );

        state.requestSourceRefresh(7);
        assertEquals(
                GunSmithSourceScreenAccess.AcceptResult.UNCHANGED,
                state.acceptSourceSnapshot(
                        7,
                        2L,
                        1L,
                        List.of()
                )
        );
    }

    @Test
    void changedSourceRevisionReturnsUpdated() {
        GunSmithExternalSourceState state = state();

        state.requestSourceRefresh(7);
        state.acceptSourceSnapshot(
                7,
                1L,
                1L,
                List.of()
        );

        state.requestSourceRefresh(7);
        assertEquals(
                GunSmithSourceScreenAccess.AcceptResult.UPDATED,
                state.acceptSourceSnapshot(
                        7,
                        2L,
                        2L,
                        List.of()
                )
        );
    }

    @Test
    void contentChangeWithSameRevisionReturnsUpdated() {
        GunSmithExternalSourceState state = state();

        state.requestSourceRefresh(7);
        state.acceptSourceSnapshot(
                7,
                1L,
                1L,
                List.of()
        );

        state.requestSourceRefresh(7);
        assertEquals(
                GunSmithSourceScreenAccess.AcceptResult.UPDATED,
                state.acceptSourceSnapshot(
                        7,
                        2L,
                        1L,
                        List.of(new ItemStack(Items.DIRT, 2))
                )
        );

        state.requestSourceRefresh(7);
        assertEquals(
                GunSmithSourceScreenAccess.AcceptResult.UNCHANGED,
                state.acceptSourceSnapshot(
                        7,
                        3L,
                        1L,
                        List.of(new ItemStack(Items.DIRT, 2))
                )
        );
    }

    @Test
    void staleRequestIdIsRejected() {
        GunSmithExternalSourceState state = state();

        state.requestSourceRefresh(7);
        state.acceptSourceSnapshot(
                7,
                1L,
                1L,
                List.of()
        );

        state.requestSourceRefresh(7);
        assertEquals(
                GunSmithSourceScreenAccess.AcceptResult.REJECTED,
                state.acceptSourceSnapshot(
                        7,
                        1L,
                        1L,
                        List.of()
                )
        );
    }

    @Test
    void wrongContainerIdIsRejected() {
        GunSmithExternalSourceState state = state();

        state.requestSourceRefresh(7);
        assertEquals(
                GunSmithSourceScreenAccess.AcceptResult.REJECTED,
                state.acceptSourceSnapshot(
                        8,
                        1L,
                        1L,
                        List.of()
                )
        );
        assertEquals(
                GunSmithSourceScreenAccess.AcceptResult.UPDATED,
                state.acceptSourceSnapshot(
                        7,
                        1L,
                        1L,
                        List.of()
                )
        );
    }

    @Test
    void staleResponseDoesNotClearNewerPendingRequest() {
        GunSmithExternalSourceState state = state();

        state.requestSourceRefresh(7);
        state.requestSourceRefresh(7);

        assertEquals(
                GunSmithSourceScreenAccess.AcceptResult.REJECTED,
                state.acceptSourceSnapshot(
                        7,
                        1L,
                        1L,
                        List.of()
                )
        );
        assertEquals(
                GunSmithSourceScreenAccess.AcceptResult.UPDATED,
                state.acceptSourceSnapshot(
                        7,
                        2L,
                        1L,
                        List.of()
                )
        );
    }

    @Test
    void duplicateAlreadyAcceptedRequestIsRejected() {
        GunSmithExternalSourceState state = state();

        state.requestSourceRefresh(7);
        assertEquals(
                GunSmithSourceScreenAccess.AcceptResult.UPDATED,
                state.acceptSourceSnapshot(
                        7,
                        1L,
                        1L,
                        List.of()
                )
        );
        assertEquals(
                GunSmithSourceScreenAccess.AcceptResult.REJECTED,
                state.acceptSourceSnapshot(
                        7,
                        1L,
                        1L,
                        List.of()
                )
        );
    }

    @Test
    void refreshTimeoutReissuesRequest() {
        AtomicInteger sends = new AtomicInteger();
        GunSmithExternalSourceState state = new GunSmithExternalSourceState(
                (containerId, requestId, recipeId) ->
                        sends.incrementAndGet()
        );

        state.requestSourceRefresh(7);
        for (int tick = 0; tick < 100; tick++) {
            state.tickSourceRefresh(7);
        }

        assertEquals(2, sends.get());
        assertEquals(
                GunSmithSourceScreenAccess.AcceptResult.UPDATED,
                state.acceptSourceSnapshot(
                        7,
                        2L,
                        1L,
                        List.of()
                )
        );
    }

    private static GunSmithExternalSourceState state() {
        return new GunSmithExternalSourceState(
                (containerId, requestId, recipeId) -> {
                }
        );
    }
}
