package com.mafuyu404.taczaddon.gunsmith;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GunSmithCraftingSessionPendingValidationTest {
    @AfterEach
    void clearManagerState() {
        GunSmithCraftingSessionManager.removeAll();
    }

    @Test
    void fullyValidPendingInteractionIsAccepted() {
        assertTrue(isValid(validState()));
    }

    @Test
    void expiredPendingInteractionIsRejected() {
        assertFalse(isValid(state(true, true, false, true, true)));
    }

    @Test
    void dimensionMismatchIsRejected() {
        assertFalse(isValid(state(true, false, true, true, true)));
    }

    @Test
    void playerMismatchIsRejected() {
        assertFalse(isValid(state(false, true, true, true, true, true)));
    }

    @Test
    void menuDefinitionMismatchIsRejected() {
        assertFalse(isValid(state(true, true, true, false, true)));
    }

    @Test
    void tableDefinitionMismatchIsRejected() {
        assertFalse(isValid(state(true, true, true, true, false)));
    }

    @Test
    void removedTableIsRejected() {
        assertFalse(isValid(state(true, true, true, true, true, false)));
    }

    @Test
    void unloadedTableIsRejected() {
        assertFalse(isValid(state(true, true, true, true, true, true, false)));
    }

    @Test
    void playerBeyondInteractionDistanceIsRejected() {
        assertFalse(isValid(state(
                true, true, true, true, true, true, true, false
        )));
    }

    @Test
    void removeAllClearsPendingInteractions() throws Exception {
        Field pendingInteractions =
                GunSmithCraftingSessionManager.class
                        .getDeclaredField("PENDING_INTERACTIONS");
        pendingInteractions.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<UUID, GunSmithCraftingSessionManager
                .PendingGunSmithInteraction> pending =
                (Map<UUID, GunSmithCraftingSessionManager
                        .PendingGunSmithInteraction>)
                        pendingInteractions.get(null);

        pending.put(
                UUID.fromString(
                        "20000000-0000-0000-0000-000000000002"
                ),
                new GunSmithCraftingSessionManager
                        .PendingGunSmithInteraction(
                                UUID.fromString(
                                        "20000000-0000-0000-0000-000000000002"
                                ),
                                null,
                                new BlockPos(1, 2, 3),
                                ResourceLocation.tryBuild(
                                        "taczaddon",
                                        "test_table"
                                ),
                                10L
                        )
        );

        GunSmithCraftingSessionManager.removeAll();
        assertTrue(pending.isEmpty());
    }

    private static boolean isValid(
            GunSmithCraftingSessionManager.PendingInteractionValidation state
    ) {
        return GunSmithCraftingSessionManager
                .isPendingInteractionValid(state);
    }

    private static GunSmithCraftingSessionManager
            .PendingInteractionValidation validState() {
        return state(true, true, true, true, true);
    }

    private static GunSmithCraftingSessionManager
            .PendingInteractionValidation state(
            boolean withinTtl,
            boolean dimensionMatches,
            boolean menuDefinitionMatches,
            boolean tableEntityActive,
            boolean tableDefinitionMatches
    ) {
        return state(
                true,
                dimensionMatches,
                withinTtl,
                menuDefinitionMatches,
                true,
                true,
                tableEntityActive,
                tableDefinitionMatches,
                true,
                true
        );
    }

    private static GunSmithCraftingSessionManager
            .PendingInteractionValidation state(
            boolean playerMatches,
            boolean dimensionMatches,
            boolean withinTtl,
            boolean menuDefinitionMatches,
            boolean tableEntityActive,
            boolean tableDefinitionMatches
    ) {
        return state(
                playerMatches,
                dimensionMatches,
                withinTtl,
                menuDefinitionMatches,
                true,
                true,
                tableEntityActive,
                tableDefinitionMatches,
                true,
                true
        );
    }

    private static GunSmithCraftingSessionManager
            .PendingInteractionValidation state(
            boolean playerMatches,
            boolean dimensionMatches,
            boolean withinTtl,
            boolean menuDefinitionMatches,
            boolean tableEntityActive,
            boolean tableDefinitionMatches,
            boolean tableLoaded,
            boolean withinDistance
    ) {
        return state(
                playerMatches,
                dimensionMatches,
                withinTtl,
                menuDefinitionMatches,
                tableLoaded,
                true,
                tableEntityActive,
                tableDefinitionMatches,
                true,
                withinDistance
        );
    }

    private static GunSmithCraftingSessionManager
            .PendingInteractionValidation state(
            boolean playerMatches,
            boolean dimensionMatches,
            boolean withinTtl,
            boolean menuDefinitionMatches,
            boolean tableEntityActive,
            boolean tableDefinitionMatches,
            boolean tableLoaded
    ) {
        return state(
                playerMatches,
                dimensionMatches,
                withinTtl,
                menuDefinitionMatches,
                tableLoaded,
                true,
                tableEntityActive,
                tableDefinitionMatches,
                true,
                true
        );
    }

    private static GunSmithCraftingSessionManager
            .PendingInteractionValidation state(
            boolean playerMatches,
            boolean dimensionMatches,
            boolean withinTtl,
            boolean menuDefinitionMatches,
            boolean tableLoaded,
            boolean expectedTableEntityPresent,
            boolean tableEntityActive,
            boolean tableDefinitionMatches,
            boolean menuStillValid,
            boolean withinDistance
    ) {
        return new GunSmithCraftingSessionManager
                .PendingInteractionValidation(
                playerMatches,
                dimensionMatches,
                withinTtl,
                menuDefinitionMatches,
                tableLoaded,
                expectedTableEntityPresent,
                tableEntityActive,
                tableDefinitionMatches,
                menuStillValid,
                withinDistance
        );
    }
}
