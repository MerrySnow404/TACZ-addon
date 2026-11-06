package com.mafuyu404.taczaddon.compat.sophisticated;

import com.mafuyu404.taczaddon.compat.sophisticated.EndpointResolution;
import com.mafuyu404.taczaddon.compat.sophisticated.LinkedStorageBridge;
import com.mafuyu404.taczaddon.compat.camera.PerspectiveApiCompat;
import com.mafuyu404.taczaddon.compat.camera.PerspectiveApiHandle;
import com.mafuyu404.taczaddon.compat.camera.ShoulderSurfing5Compat;
import com.mafuyu404.taczaddon.compat.sophisticated.SophisticatedBackpacksCompat;
import com.mafuyu404.taczaddon.compat.sophisticated.SophisticatedLinkedStorageCompat;
import com.mafuyu404.taczaddon.compat.sophisticated.SophisticatedStorageClientCompat;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.reflect.Field;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class OptionalCompatCleanupTest {
    @ParameterizedTest
    @ValueSource(strings = {"resolve", "request", "refresh", "send"})
    void everyLinkedOperationLatchesOnlyLinkedBridge(String operation) throws Exception {
        Field bridge = field(SophisticatedLinkedStorageCompat.class, "BRIDGE");
        Object original = bridge.get(null);
        AtomicBoolean warning = (AtomicBoolean) field(SophisticatedLinkedStorageCompat.class, "WARNING_LOGGED").get(null);
        boolean warned = warning.get();
        AtomicInteger calls = new AtomicInteger();
        try {
            bridge.set(null, new LinkedStorageBridge() {
                private void fail() {
                    calls.incrementAndGet();
                    throw new NoSuchFieldError("linked API changed");
                }
                @Override public EndpointResolution resolve(ItemStack stack) {
                    fail(); return null;
                }
                @Override public void requestSnapshot(UUID group) { fail(); }
                @Override public void refreshSnapshot(UUID group) { fail(); }
                @Override public void sendSnapshot(ServerPlayer player, UUID group) { fail(); }
            });
            assertTrue(SophisticatedLinkedStorageCompat.supported());
            switch (operation) {
                case "resolve" -> assertTrue(SophisticatedLinkedStorageCompat.resolve(null).bridgeUnavailable());
                case "request" -> SophisticatedLinkedStorageCompat.requestSnapshot(null);
                case "refresh" -> SophisticatedLinkedStorageCompat.refreshSnapshot(null);
                case "send" -> SophisticatedLinkedStorageCompat.sendSnapshot(null, null);
                default -> fail(operation);
            }
            assertEquals(1, calls.get());
            assertFalse(SophisticatedLinkedStorageCompat.supported());
            assertTrue(warning.get());
            assertTrue(SophisticatedLinkedStorageCompat.resolve(null).bridgeUnavailable());
            SophisticatedLinkedStorageCompat.requestSnapshot(null);
            SophisticatedLinkedStorageCompat.refreshSnapshot(null);
            SophisticatedLinkedStorageCompat.sendSnapshot(null, null);
            assertEquals(1, calls.get());
            assertFalse(field(SophisticatedBackpacksCompat.class, "linkageBroken").getBoolean(null));
            assertFalse(field(SophisticatedStorageClientCompat.class, "linkageBroken").getBoolean(null));
        } finally {
            bridge.set(null, original);
            warning.set(warned);
        }
    }

    @Test
    void failedPerspectiveReleaseIsNotRetriedButOtherOutstandingHandlesStillCleanUp() throws Exception {
        Field latch = field(PerspectiveApiCompat.class, "linkageBroken");
        AtomicBoolean warning = (AtomicBoolean) field(PerspectiveApiCompat.class, "LINKAGE_WARNING_LOGGED").get(null);
        boolean broken = latch.getBoolean(null);
        boolean warned = warning.get();
        AtomicInteger failedCalls = new AtomicInteger();
        AtomicInteger cleanupCalls = new AtomicInteger();
        try {
            var failing = safeHandle(() -> {
                failedCalls.incrementAndGet();
                throw new NoSuchMethodError("unregister changed");
            });
            var outstanding = safeHandle(cleanupCalls::incrementAndGet);
            assertDoesNotThrow(failing::restore);
            assertDoesNotThrow(failing::restore);
            assertEquals(1, failedCalls.get());
            assertTrue(latch.getBoolean(null));
            assertTrue(warning.get());
            // Cleanup is intentionally allowed after the query/acquisition breaker trips.
            assertDoesNotThrow(outstanding::restore);
            assertDoesNotThrow(outstanding::restore);
            assertEquals(1, cleanupCalls.get());
            assertFalse(field(ShoulderSurfing5Compat.class, "linkageBroken").getBoolean(null));
        } finally {
            latch.setBoolean(null, broken);
            warning.set(warned);
        }
    }

    private static PerspectiveApiHandle safeHandle(
            PerspectiveApiHandle delegate) throws Exception {
        Class<?> wrapper = Class.forName(PerspectiveApiCompat.class.getName() + "$SafePerspectiveApiHandle");
        var constructor = wrapper.getDeclaredConstructor(PerspectiveApiHandle.class);
        constructor.setAccessible(true);
        return (PerspectiveApiHandle) constructor.newInstance(delegate);
    }

    private static Field field(Class<?> owner, String name) throws Exception {
        Field result = owner.getDeclaredField(name);
        result.setAccessible(true);
        return result;
    }
}
