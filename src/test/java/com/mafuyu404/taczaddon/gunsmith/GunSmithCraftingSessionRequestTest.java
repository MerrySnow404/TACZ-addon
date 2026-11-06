package com.mafuyu404.taczaddon.gunsmith;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class GunSmithCraftingSessionRequestTest {
    private static final UUID PLAYER_ID =
            UUID.fromString("10000000-0000-0000-0000-000000000001");

    @AfterEach
    void clearSessions() {
        GunSmithCraftingSessionManager.removeAll();
    }

    @Test
    void oldCraftRequestCannotDeleteNewerSession() throws Exception {
        GunSmithCraftingSession sessionA =
                session(5);
        GunSmithCraftingSession sessionB =
                session(6);
        register(sessionB);

        GunSmithCraftingSessionManager.SessionRequestDecision decision =
                GunSmithCraftingSessionManager.evaluateRequest(
                        GunSmithCraftingSessionManager.getSession(
                                PLAYER_ID
                        ),
                        5,
                        false
                );

        assertFalse(decision.accepted());
        assertFalse(decision.shouldRemoveMatchingSession());
        assertSame(
                sessionB,
                GunSmithCraftingSessionManager.getSession(PLAYER_ID)
        );
        assertTrue(
                GunSmithCraftingSessionManager.evaluateRequest(
                        sessionB,
                        6,
                        true
                ).accepted()
        );
    }

    @Test
    void oldSourceRefreshRequestCannotDeleteNewerSession()
            throws Exception {
        GunSmithCraftingSession sessionB =
                session(6);
        register(sessionB);

        GunSmithCraftingSessionManager.SessionRequestDecision decision =
                GunSmithCraftingSessionManager.evaluateRequest(
                        GunSmithCraftingSessionManager.getSession(
                                PLAYER_ID
                        ),
                        5,
                        false
                );

        assertFalse(decision.accepted());
        assertFalse(decision.shouldRemoveMatchingSession());
        assertSame(
                sessionB,
                GunSmithCraftingSessionManager.getSession(PLAYER_ID)
        );
    }

    @Test
    void oldContainerCloseCannotDeleteNewerSession() throws Exception {
        GunSmithCraftingSession sessionB =
                session(6);
        register(sessionB);

        GunSmithCraftingSessionManager.removeSession(PLAYER_ID, 5);

        assertSame(
                sessionB,
                GunSmithCraftingSessionManager.getSession(PLAYER_ID)
        );
    }

    @Test
    void matchingStructurallyInvalidSessionIsRemoved() throws Exception {
        GunSmithCraftingSession session =
                session(5);
        register(session);

        GunSmithCraftingSessionManager.SessionRequestDecision decision =
                GunSmithCraftingSessionManager.evaluateRequest(
                        GunSmithCraftingSessionManager.getSession(
                                PLAYER_ID
                        ),
                        5,
                        false
                );

        assertFalse(decision.accepted());
        assertTrue(decision.shouldRemoveMatchingSession());

        GunSmithCraftingSessionManager.removeSession(PLAYER_ID, 5);
        assertNull(
                GunSmithCraftingSessionManager.getSession(PLAYER_ID)
        );
    }

    @Test
    void missingSessionIsRejectedWithoutDeletingAnything() {
        GunSmithCraftingSessionManager.SessionRequestDecision decision =
                GunSmithCraftingSessionManager.evaluateRequest(
                        null,
                        5,
                        false
                );

        assertFalse(decision.accepted());
        assertFalse(decision.shouldRemoveMatchingSession());
        assertNull(
                GunSmithCraftingSessionManager.getSession(PLAYER_ID)
        );
    }

    private static GunSmithCraftingSession
    session(int containerId) {
        return new GunSmithCraftingSession(
                PLAYER_ID,
                containerId,
                null,
                new BlockPos(0, 1, 0),
                ResourceLocation.tryBuild("taczaddon", "test_table")
        );
    }

    private static void register(
            GunSmithCraftingSession session
    ) throws Exception {
        Field sessions = GunSmithCraftingSessionManager.class
                .getDeclaredField("SESSIONS");
        sessions.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<UUID, GunSmithCraftingSession>
                map = (Map<UUID, GunSmithCraftingSession>) sessions.get(null);
        map.put(session.getPlayerId(), session);
    }
}
