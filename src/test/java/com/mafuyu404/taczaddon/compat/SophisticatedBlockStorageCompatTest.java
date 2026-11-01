package com.mafuyu404.taczaddon.compat;

import com.mafuyu404.taczaddon.testutil.CompatibilityFixtures;
import net.p3pp3rf1y.sophisticatedcore.linkedstorage.ILinkedStorageBlockEndpoint;
import net.p3pp3rf1y.sophisticatedcore.linkedstorage.LinkedStorageEndpointData;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.jar.JarFile;

import static org.junit.jupiter.api.Assertions.*;

class SophisticatedBlockStorageCompatTest {
    @org.junit.jupiter.api.BeforeAll
    static void bootstrapMinecraft() throws Exception {
        com.mafuyu404.taczaddon.testutil.MinecraftTestBootstrap.prepare();
    }

    @Test
    void separateEndpointsResolveTheSameGroupAndRelinkingChangesIdentity() {
        UUID group = UUID.randomUUID();
        var firstData = new AtomicReference<>(new LinkedStorageEndpointData(group, UUID.randomUUID()));
        Object first = endpoint(firstData);
        Object second = endpoint(new AtomicReference<>(new LinkedStorageEndpointData(group, UUID.randomUUID())));
        assertNotSame(first, second);
        assertEquals(group, SophisticatedBlockStorageCompat.groupId(first));
        assertEquals(group, SophisticatedBlockStorageCompat.groupId(second));

        UUID other = UUID.randomUUID();
        firstData.set(new LinkedStorageEndpointData(other, UUID.randomUUID()));
        assertEquals(other, SophisticatedBlockStorageCompat.groupId(first));
        firstData.set(null);
        assertNull(SophisticatedBlockStorageCompat.groupId(first));
        assertNull(SophisticatedBlockStorageCompat.groupId(new Object()));
        firstData.set(new LinkedStorageEndpointData(null, UUID.randomUUID()));
        assertThrows(IllegalStateException.class, () -> SophisticatedBlockStorageCompat.groupId(first));
    }

    @Test
    void publishedStorageImplementsTheCoreBlockEndpointContract() throws Exception {
        var path = CompatibilityFixtures.jar("sophisticated", "storage.current", "storage");
        CompatibilityFixtures.requireVersion(path, "1.5.13.2212", "Sophisticated Storage");
        try (var jar = new JarFile(path.toFile())) {
            ApiShapeProbe.ClassBytes source = name -> {
                var entry = jar.getJarEntry(name.replace('.', '/') + ".class");
                if (entry == null) return null;
                try (var input = jar.getInputStream(entry)) {
                    return input.readAllBytes();
                } catch (java.io.IOException e) {
                    throw new java.io.UncheckedIOException(e);
                }
            };
            var shape = ApiShapeProbe.inspect(source,
                    "net.p3pp3rf1y.sophisticatedstorage.block.StorageBlockEntity");
            assertTrue(shape.implementsDirectly(ILinkedStorageBlockEndpoint.class.getName()));
            assertTrue(shape.hasMethod("getLinkedStorageEndpointData",
                    "()Lnet/p3pp3rf1y/sophisticatedcore/linkedstorage/LinkedStorageEndpointData;"));
        }
    }

    @Test
    void previousStorageKeepsItsOrdinaryCapabilityWithoutLinkedEndpoint() throws Exception {
        var path = CompatibilityFixtures.jar("sophisticated", "storage.previous", "storage");
        CompatibilityFixtures.requireVersion(path, "1.4.86.2131", "previous Sophisticated Storage");
        try (var jar = new JarFile(path.toFile())) {
            var entry = jar.getJarEntry("net/p3pp3rf1y/sophisticatedstorage/block/StorageBlockEntity.class");
            assertNotNull(entry);
            byte[] bytes;
            try (var input = jar.getInputStream(entry)) { bytes = input.readAllBytes(); }
            var shape = ApiShapeProbe.inspect(name -> bytes, "ignored");
            assertFalse(shape.implementsDirectly(ILinkedStorageBlockEndpoint.class.getName()));
            assertTrue(shape.hasMethod("getCapability", "(Lnet/minecraftforge/common/capabilities/Capability;"
                    + "Lnet/minecraft/core/Direction;)Lnet/minecraftforge/common/util/LazyOptional;"));
        }
    }

    private static Object endpoint(AtomicReference<LinkedStorageEndpointData> data) {
        return Proxy.newProxyInstance(ILinkedStorageBlockEndpoint.class.getClassLoader(),
                new Class<?>[]{ILinkedStorageBlockEndpoint.class}, (proxy, method, args) -> {
                    if (method.getName().equals("getLinkedStorageEndpointData")) return data.get();
                    throw new UnsupportedOperationException(method.getName());
                });
    }
}
