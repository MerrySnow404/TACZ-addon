package com.mafuyu404.taczaddon.compat;

import net.minecraftforge.forgespi.language.MavenVersionAdapter;
import org.apache.maven.artifact.versioning.DefaultArtifactVersion;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

class OptionalCompatibilityIsolationTest {
    private static final String PACKAGE = "com.mafuyu404.taczaddon.compat.";
    private static final List<String> OPTIONAL_PACKAGES = List.of(
            "com.solr98.beyondintegration.",
            "net.fxnt.fxntstorage.", "com.simibubi.create.",
            "mezz.jei.", "top.theillusivec4.curios.", "net.p3pp3rf1y.sophisticated",
            "com.github.exopandora.shouldersurfing.", "io.github.leawind.perspectiveapi.");

    @Test
    void facadesLoadAndExposeMethodsWithoutOptionalApisOrBackends() throws Exception {
        ClassLoader loader = new ClassLoader(getClass().getClassLoader()) {
            @Override
            protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                if (OPTIONAL_PACKAGES.stream().anyMatch(name::startsWith)) {
                    throw new ClassNotFoundException(name);
                }
                if (!name.startsWith(PACKAGE)) return super.loadClass(name, resolve);
                assertFalse(name.endsWith("Inner") || name.equals("com.mafuyu404.taczaddon.compat.jei.JeiPlugin")
                        || name.equals("com.mafuyu404.taczaddon.compat.sophisticated.SophisticatedLinkedStorageCompat326"),
                        "Absent integration must not load backend: " + name);
                synchronized (getClassLoadingLock(name)) {
                    Class<?> loaded = findLoadedClass(name);
                    if (loaded == null) {
                        try (var stream = getParent().getResourceAsStream(name.replace('.', '/') + ".class")) {
                            if (stream == null) throw new ClassNotFoundException(name);
                            byte[] bytes = stream.readAllBytes();
                            loaded = defineClass(name, bytes, 0, bytes.length);
                        } catch (IOException error) {
                            throw new ClassNotFoundException(name, error);
                        }
                    }
                    if (resolve) resolveClass(loaded);
                    return loaded;
                }
            }

            /**
             * Absent really means absent: the structural generation probe reads
             * optional class bytes, so resource lookups must hide the same
             * packages that {@link #loadClass(String, boolean)} hides.
             */
            @Override
            public java.io.InputStream getResourceAsStream(String name) {
                if (name != null && name.endsWith(".class")) {
                    String binaryName = name
                            .replace('/', '.');
                    if (OPTIONAL_PACKAGES.stream()
                            .anyMatch(binaryName::startsWith)) {
                        return null;
                    }
                }
                return super.getResourceAsStream(name);
            }
        };
        for (String name : List.of("CuriosCompat", "JeiCompat", "ShoulderSurfing5Compat",
                "PerspectiveApiCompat", "SophisticatedBackpacksCompat", "SophisticatedStorageClientCompat",
                "BeyondIntegrationCompat", "BeyondAmmoClientCompat", "SophisticatedBlockStorageCompat", "CreateStorageCompat")) {
            Class<?> facade = Class.forName(com.mafuyu404.taczaddon.testutil.CompatibilityClassNames.compatClassName(name), true, loader);
            for (var method : facade.getDeclaredMethods()) {
                if (java.lang.reflect.Modifier.isPublic(method.getModifiers())) {
                    // Null object arguments exercise the absent-mod path without bootstrapping a game.
                    Object[] args = new Object[method.getParameterCount()];
                    for (int i = 0; i < args.length; i++) {
                        if (method.getParameterTypes()[i] == boolean.class) args[i] = false;
                        if (method.getParameterTypes()[i] == int.class) args[i] = 0;
                    }
                    assertDoesNotThrow(() -> method.invoke(null, args), name + "." + method.getName());
                }
            }
        }
    }

    @Test
    void optionalMetadataDoesNotRejectUnknownInstalledVersions() throws IOException {
        String metadata = Files.readString(Path.of("src/main/resources/META-INF/mods.toml"));
        int optionalCount = 0;
        for (String dependency : metadata.split("\\[\\[dependencies\\.")) {
            if (!dependency.contains("mandatory=false")) continue;
            optionalCount++;
            var matcher = Pattern.compile("versionRange=\"([^\"]*)\"").matcher(dependency);
            assertTrue(matcher.find(), dependency);
            var range = MavenVersionAdapter.createFromVersionSpec(matcher.group(1));
            assertEquals(1, range.getRestrictions().size(), dependency);
            assertEquals(new DefaultArtifactVersion("0"),
                    range.getRestrictions().get(0).getLowerBound(), dependency);
            assertNull(range.getRestrictions().get(0).getUpperBound(), dependency);
            for (String version : List.of("1.5.0.2316", "3.26.0.2119", "1.4.86.2131",
                    "5.14.1+1.20.1", "3.0.3-beta+forge-1.20.1", "0.1-alpha", "999.0")) {
                assertTrue(range.containsVersion(new DefaultArtifactVersion(version)),
                        () -> "Forge loader would reject installed version " + version + ": " + dependency);
            }
            assertTrue(dependency.contains("ordering=\"AFTER\""), dependency);
        }
        assertEquals(8, optionalCount);
        /*
         * Loader tolerance is not a support claim: the runtime capability
         * gates decide what is actually usable, and the metadata comment has to
         * say so.
         */
        assertTrue(
                metadata.contains("loader-tolerant"),
                "mods.toml must document the loader-tolerant optional ranges"
        );
        assertTrue(
                metadata.contains("capability-gated"),
                "mods.toml must document runtime capability gating"
        );
    }
}
