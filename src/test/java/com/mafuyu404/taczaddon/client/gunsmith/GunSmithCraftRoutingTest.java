package com.mafuyu404.taczaddon.client.gunsmith;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Exactly one crafting path per button press.
 */
class GunSmithCraftRoutingTest {

    @Test
    void defaultSingleCraftWithOwnMaterialsUsesTheNativeCallback() {
        assertEquals(
                GunSmithCraftRouting.Route.NATIVE,
                GunSmithCraftRouting.decide(false, true, true)
        );
    }

    @Test
    void externalMaterialsRouteToTheAddonTransaction() {
        assertEquals(
                GunSmithCraftRouting.Route.EXTENDED,
                GunSmithCraftRouting.decide(false, false, true)
        );
    }

    @Test
    void batchRequestRoutesToTheAddonTransaction() {
        assertEquals(
                GunSmithCraftRouting.Route.EXTENDED,
                GunSmithCraftRouting.decide(true, true, true)
        );
    }

    @Test
    void unauthorizedMenuKeepsOneNativeOperation() {
        assertEquals(
                GunSmithCraftRouting.Route.NATIVE,
                GunSmithCraftRouting.decide(true, false, false),
                "without a server-confirmed anchor no batch may be simulated"
        );
        assertEquals(
                GunSmithCraftRouting.Route.NATIVE,
                GunSmithCraftRouting.decide(false, false, false)
        );
    }

    @Test
    void timedOutRequestIsNeverResentAndNeverFallsBack() {
        long[] now = {1_000L};
        List<String> sent = new ArrayList<>();

        GunSmithCraftBridgeState state = newState(now, sent);

        assertEquals(
                GunSmithCraftBridgeState.RequestStatus.SENT,
                state.tryRequestCraft(
                        7,
                        net.minecraft.resources.ResourceLocation.tryBuild(
                                "taczaddon_test",
                                "recipe"
                        ),
                        false,
                        1
                )
        );
        assertEquals(1, sent.size());

        now[0] += 5_000L;
        assertEquals(
                GunSmithCraftBridgeState.RequestStatus.BUSY,
                state.tryRequestCraft(
                        7,
                        net.minecraft.resources.ResourceLocation.tryBuild(
                                "taczaddon_test",
                                "recipe"
                        ),
                        false,
                        1
                ),
                "an in-flight operation must not be re-sent"
        );
        assertEquals(1, sent.size());

        now[0] += 6_000L;
        assertEquals(
                true,
                state.tick(),
                "the timeout releases the waiting state without re-sending"
        );
        assertEquals(false, state.isWaiting());
        assertEquals(1, sent.size());
        assertEquals(
                false,
                state.tick(),
                "an idle state never sends anything"
        );
        assertEquals(1, sent.size());

        assertEquals(
                GunSmithCraftBridgeState.RequestStatus.SENT,
                state.tryRequestCraft(
                        7,
                        net.minecraft.resources.ResourceLocation.tryBuild(
                                "taczaddon_test",
                                "recipe"
                        ),
                        false,
                        1
                ),
                "a fresh click starts a new operation with a new id"
        );
        assertEquals(2, sent.size());

        assertEquals(
                true,
                state.acceptCraftResult(7, state.pendingRequestId())
        );
        assertEquals(false, state.isWaiting());
    }

    @Test
    void missingRecipeIsNeverSent() {
        long[] now = {1_000L};
        List<String> sent = new ArrayList<>();
        GunSmithCraftBridgeState state = newState(now, sent);

        assertEquals(
                GunSmithCraftBridgeState.RequestStatus.NO_RECIPE,
                state.tryRequestCraft(7, null, false, 1)
        );
        assertEquals(0, sent.size());
    }

    @Test
    void requestCraftKeepsTheBeyondIntegrationVoidAbi()
            throws ReflectiveOperationException {
        Method method = GunSmithCraftBridgeState.class.getMethod(
                "requestCraft",
                int.class,
                net.minecraft.resources.ResourceLocation.class,
                boolean.class,
                int.class
        );

        assertTrue(
                Modifier.isPublic(method.getModifiers()),
                "Beyond Integration injects into the public requestCraft method"
        );
        assertEquals(
                void.class,
                method.getReturnType(),
                "requestCraft must keep the external void ABI; status-returning tests use tryRequestCraft"
        );
        assertEquals(
                GunSmithCraftBridgeState.RequestStatus.class,
                GunSmithCraftBridgeState.class.getMethod(
                        "tryRequestCraft",
                        int.class,
                        net.minecraft.resources.ResourceLocation.class,
                        boolean.class,
                        int.class
                ).getReturnType()
        );
    }

    /**
     * The native/extended decision must use the same capacity-allocation
     * semantics as the server transaction. Independent per-ingredient counts
     * are not enough: overlapping predicates can double-count one stack.
     */
    @Test
    void nativeRoutingUsesAllocationFeasibilityAndCreativeExemption()
            throws IOException {
        String routing = Files.readString(
                Path.of(
                        "src/main/java/com/mafuyu404/taczaddon/client/gunsmith/GunSmithCraftRouting.java"
                ),
                StandardCharsets.UTF_8
        );

        assertTrue(
                routing.contains("player.isCreative()"),
                "a creative player must keep the native material-exempt path"
        );
        assertTrue(
                routing.contains(
                        "CraftingTransaction.canSatisfyStacks("
                ),
                "routing must reuse the max-flow feasibility check"
        );
        assertFalse(
                routing.contains("computeCombinedIngredientCounts("),
                "independent per-ingredient counts cannot prove simultaneous capacity"
        );
    }

    private static GunSmithCraftBridgeState newState(
            long[] now,
            List<String> sent
    ) {
        return new GunSmithCraftBridgeState(
                () -> now[0],
                (containerId, requestId, recipeId, requestedCount) ->
                        sent.add(
                                "container=" + containerId
                                        + ",request=" + requestId
                                        + ",recipe=" + recipeId
                                        + ",count=" + requestedCount
                        )
        );
    }
}
