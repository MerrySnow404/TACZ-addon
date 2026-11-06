package com.mafuyu404.taczaddon.compat;

import com.mafuyu404.taczaddon.compat.camera.PerspectiveApiCompat;
import com.mafuyu404.taczaddon.compat.camera.ShoulderSurfing5Compat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class OptionalCompatCircuitBreakerTest {
    @AfterEach
    void resetBreakers() throws Exception {
        setField(
                ShoulderSurfing5Compat.class,
                "linkageBroken",
                false
        );
        setField(
                PerspectiveApiCompat.class,
                "linkageBroken",
                false
        );
        atomicField(ShoulderSurfing5Compat.class,
                "LINKAGE_WARNING_LOGGED").set(false);
        atomicField(PerspectiveApiCompat.class,
                "LINKAGE_WARNING_LOGGED").set(false);
    }

    @Test
    void ssrLinkageBreakerDisablesBackendAndLogsOnce()
            throws Exception {
        invokeBreakLinkage(
                ShoulderSurfing5Compat.class,
                new LinkageError("ssr api broken")
        );

        assertTrue((boolean) field(
                ShoulderSurfing5Compat.class,
                "linkageBroken"
        ).get(null));
        assertTrue(atomicField(
                ShoulderSurfing5Compat.class,
                "LINKAGE_WARNING_LOGGED"
        ).get());
        assertFalse(ShoulderSurfing5Compat.isShoulderSurfing());
        assertFalse(ShoulderSurfing5Compat.isFreeLooking());
        assertFalse(
                ShoulderSurfing5Compat
                        .showCrosshairWhenShoulderSurfing()
        );
    }

    @Test
    void perspectiveLinkageBreakerDisablesBackendAndLogsOnce()
            throws Exception {
        invokeBreakLinkage(
                PerspectiveApiCompat.class,
                new LinkageError("perspective api broken")
        );

        assertTrue((boolean) field(
                PerspectiveApiCompat.class,
                "linkageBroken"
        ).get(null));
        assertTrue(atomicField(
                PerspectiveApiCompat.class,
                "LINKAGE_WARNING_LOGGED"
        ).get());
        assertNull(
                PerspectiveApiCompat
                        .currentNonVanillaPerspectiveId()
        );
        assertFalse(PerspectiveApiCompat.isFirstPersonActive());
        assertNull(PerspectiveApiCompat.requestFirstPerson());
    }

    private static void invokeBreakLinkage(
            Class<?> type,
            LinkageError error
    ) throws Exception {
        Method method = type.getDeclaredMethod(
                "breakLinkage",
                LinkageError.class
        );
        method.setAccessible(true);
        method.invoke(null, error);
    }

    private static Field field(Class<?> type, String name)
            throws Exception {
        Field field = type.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    private static AtomicBoolean atomicField(
            Class<?> type,
            String name
    ) throws Exception {
        return (AtomicBoolean) field(type, name).get(null);
    }

    private static void setField(
            Class<?> type,
            String name,
            boolean value
    ) throws Exception {
        field(type, name).setBoolean(null, value);
    }
}
