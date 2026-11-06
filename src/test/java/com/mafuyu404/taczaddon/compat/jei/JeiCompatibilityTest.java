package com.mafuyu404.taczaddon.compat.jei;

import mezz.jei.api.runtime.IJeiRuntime;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class JeiCompatibilityTest {
    @AfterEach
    void resetBackend() throws Exception {
        var latch = JeiCompat.class.getDeclaredField("linkageBroken");
        latch.setAccessible(true);
        latch.setBoolean(null, false);
        var warning = JeiCompat.class.getDeclaredField("LINKAGE_WARNING_LOGGED");
        warning.setAccessible(true);
        ((AtomicBoolean) warning.get(null)).set(false);
        new JeiPlugin().onRuntimeUnavailable();
    }

    static Stream<LinkageError> brokenApis() {
        return Stream.of(new NoClassDefFoundError("missing JEI class"),
                new NoSuchMethodError("changed JEI method"),
                new AbstractMethodError("changed JEI interface"),
                new ExceptionInInitializerError("JEI initialization failed"));
    }

    @ParameterizedTest
    @MethodSource("brokenApis")
    void brokenApiStopsRetriesWithoutDisablingCurios(LinkageError error) {
        AtomicInteger attempts = new AtomicInteger();
        assertFalse(JeiCompat.runGuarded(() -> {
            attempts.incrementAndGet();
            throw error;
        }));
        assertFalse(JeiCompat.runGuarded(() -> {
            attempts.incrementAndGet();
            return true;
        }));
        assertEquals(1, attempts.get());
        assertTrue(com.mafuyu404.taczaddon.compat.curios.CuriosGuardProbe.runGuarded(() -> true));
    }

    @Test
    void ordinaryMissDoesNotDisableLaterNavigation() {
        assertFalse(JeiCompat.runGuarded(() -> false));
        assertTrue(JeiCompat.runGuarded(() -> true));
    }

    @Test
    void programmingErrorsAreNotTreatedAsOptionalApiFailures() {
        IllegalStateException failure = new IllegalStateException("fixture");
        assertSame(failure, assertThrows(IllegalStateException.class,
                () -> JeiCompat.runGuarded(() -> { throw failure; })));
        assertTrue(JeiCompat.runGuarded(() -> true));
    }

    @Test
    void unavailableRuntimeIsReleasedAndCanBeReplaced() {
        IJeiRuntime runtime = (IJeiRuntime) Proxy.newProxyInstance(
                IJeiRuntime.class.getClassLoader(), new Class<?>[] {IJeiRuntime.class},
                (proxy, method, args) -> { throw new AssertionError("Stale runtime used"); });
        JeiPlugin plugin = new JeiPlugin();
        plugin.onRuntimeAvailable(runtime);
        assertSame(runtime, JeiPlugin.getJeiRuntime().orElseThrow());
        plugin.onRuntimeUnavailable();
        assertTrue(JeiPlugin.getJeiRuntime().isEmpty());
        assertFalse(JeiPlugin.showRecipes(null));
        plugin.onRuntimeAvailable(runtime);
        assertSame(runtime, JeiPlugin.getJeiRuntime().orElseThrow());
    }
}
