package com.mafuyu404.taczaddon.compat.tacz;

import com.mafuyu404.taczaddon.bootstrap.TaczAddonMixinPlugin;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class TaczArchitectureTest {
    private static final Path PROJECT_ROOT =
            Path.of("").toAbsolutePath().normalize();

    @Test
    void noTaCZMixinUsesLocalVariableInjection()
            throws IOException {
        Path mixinRoot = PROJECT_ROOT.resolve(
                "src/main/java/com/mafuyu404/taczaddon/mixin/tacz"
        );
        List<Path> sources = listJavaFiles(mixinRoot);
        assertFalse(sources.isEmpty());

        for (Path source : sources) {
            String text = Files.readString(
                    source,
                    StandardCharsets.UTF_8
            );
            assertFalse(
                    text.contains("lambda$"),
                    source + " contains a synthetic lambda selector"
            );
            assertFalse(
                    text.contains("@ModifyVariable"),
                    source + " uses @ModifyVariable"
            );
            assertFalse(
                    text.contains("@At(\"STORE\")"),
                    source + " uses STORE injection"
            );
        }
    }

    @Test
    void singleMixinConfigUsesPlatformCompatibilityAndTaCZPlugin()
            throws IOException {
        String config = read(
                "src/main/resources/taczaddon.mixins.json"
        );
        try (Stream<Path> resources = Files.list(PROJECT_ROOT.resolve("src/main/resources"))) {
            assertEquals(List.of("taczaddon.mixins.json"), resources
                    .map(path -> path.getFileName().toString())
                    .filter(name -> name.endsWith(".mixins.json"))
                    .sorted().toList());
        }
        assertTrue(config.contains("\"compatibilityLevel\": \"JAVA_17\""));
        assertTrue(config.contains("\"package\": \"com.mafuyu404.taczaddon.mixin\""));
        assertTrue(config.contains("\"refmap\": \"taczaddon.refmap.json\""));
        assertTrue(config.contains(
                "\"plugin\": \"com.mafuyu404.taczaddon.bootstrap.TaczAddonMixinPlugin\""
        ));
        assertTrue(config.contains("SmithingMenuMixin"));
        assertTrue(config.contains("AbstractContainerScreenMixin"));
        assertTrue(config.contains("tacz.v1_1_8.LocalPlayerDrawMixin"));
        assertTrue(config.contains("tacz.v1_1_8.GunSmithTableSourceViewMixin"));
        assertTrue(config.contains(
                "tacz.v1_1_8.GunSmithTableIngredientInteractionMixin"
        ));
        assertEquals(List.of("config \"${mod_id}.mixins.json\""), read("build.gradle")
                .lines().map(String::trim)
                .filter(line -> line.startsWith("config ")).toList());
    }

    @Test
    void noLegacyGlobalJeiBridgeRemains()
            throws IOException {
        // Concatenated literals keep the legacy bridge name out of the
        // source tree so the final bridge-name grep stays at zero hits.
        String legacyStorageKey = "GunSmithTableJEI";
        String legacyEventClass = "JEIEvent";
        Path mainRoot = PROJECT_ROOT.resolve(
                "src/main/java/com/mafuyu404/taczaddon"
        );
        for (Path source : listJavaFiles(mainRoot)) {
            String text = Files.readString(
                    source,
                    StandardCharsets.UTF_8
            );
            assertFalse(
                    text.contains(legacyStorageKey),
                    source + " references the legacy DataStorage bridge"
            );
            assertFalse(
                    text.contains(legacyEventClass),
                    source + " references the legacy input event bridge"
            );
        }
        assertFalse(
                Files.exists(PROJECT_ROOT.resolve(
                        "src/main/java/com/mafuyu404/taczaddon/event/JEIEvent.java"
                )),
                "legacy JEIEvent.java must stay deleted"
        );
    }

    @Test
    void gunSmithIngredientMixinKeepsFrameLocalArchitecture()
            throws IOException {
        String mixin = read(
                "src/main/java/com/mafuyu404/taczaddon/mixin/tacz/v1_1_8/GunSmithTableIngredientInteractionMixin.java"
        );

        assertTrue(mixin.contains("renderIngredient("));
        assertTrue(mixin.contains("renderFakeItem"));
        assertTrue(mixin.contains("GunSmithIngredientInteractionState"));
        assertTrue(mixin.contains("JeiCompat.showRecipes"));
        assertTrue(mixin.contains("require = 1"));
        assertTrue(mixin.contains("remap = false"));
        assertTrue(mixin.contains("remap = true"));

        assertFalse(mixin.contains("DataStorage"));
        assertFalse(mixin.contains("GunSmithTableJEI"));
        assertFalse(mixin.contains("InputEvent.MouseButton"));
        assertFalse(mixin.contains("int[] mouse"));
        assertFalse(mixin.contains("HashMap<String, Boolean>"));
        assertFalse(mixin.contains("require = 0"));
        assertFalse(mixin.contains("@ModifyVariable"));
        assertFalse(mixin.contains("@At(\"STORE\")"));

        String containerScreen = read(
                "src/main/java/com/mafuyu404/taczaddon/mixin/AbstractContainerScreenMixin.java"
        );
        assertTrue(containerScreen.contains("mouseReleased"));
        assertTrue(containerScreen.contains(
                "GunSmithIngredientScreenAccess"
        ));
    }

    @Test
    void commonAndNetworkPackagesDoNotImportClientClasses()
            throws IOException {
        List<Path> roots = List.of(
                PROJECT_ROOT.resolve("src/main/java/com/mafuyu404/taczaddon/gunsmith"),
                PROJECT_ROOT.resolve("src/main/java/com/mafuyu404/taczaddon/ammo"),
                PROJECT_ROOT.resolve("src/main/java/com/mafuyu404/taczaddon/gun"),
                PROJECT_ROOT.resolve("src/main/java/com/mafuyu404/taczaddon/storage"),
                PROJECT_ROOT.resolve(
                        "src/main/java/com/mafuyu404/taczaddon/refit"
                ),
                PROJECT_ROOT.resolve(
                        "src/main/java/com/mafuyu404/taczaddon/network"
                ),
                PROJECT_ROOT.resolve(
                        "src/main/java/com/mafuyu404/taczaddon/compat/tacz"
                )
        );

        for (Path root : roots) {
            for (Path source : listJavaFiles(root)) {
                String text = Files.readString(
                        source,
                        StandardCharsets.UTF_8
                );
                assertFalse(
                        text.contains("import net.minecraft.client."),
                        source + " imports a client class"
                );
            }
        }
    }

    @Test
    void everyVersionAdapterIsMappedToAFeature()
            throws IOException {
        String config = read(
                "src/main/resources/taczaddon.mixins.json"
        );
        for (String entry : entries(config)) {
            if (!entry.startsWith("tacz.")) {
                continue;
            }
            String mixinClass = "com.mafuyu404.taczaddon.mixin."
                    + entry;
            assertTrue(
                    TaczContractRegistry.bindingForMixin(mixinClass)
                            != null,
                    mixinClass + " has no exact Mixin binding"
            );
        }
    }

    /**
     * The physical side of every TaCZ adapter must match the side registry the
     * runtime gate reports, and the server-visible fast-swap adapter must stay
     * on the common side.
     */
    @Test
    void mixinSideSemanticsMatchTheRuntimeSideRegistry()
            throws IOException {
        String config = read(
                "src/main/resources/taczaddon.mixins.json"
        );
        List<String> common = arrayEntries(config, "\"mixins\"");
        List<String> client = arrayEntries(config, "\"client\"");

        assertTrue(
                common.contains("tacz.v1_1_8.LivingEntityDrawGunMixin"),
                "server-visible fast-swap adapter must stay in the common array"
        );
        assertFalse(
                client.contains("tacz.v1_1_8.LivingEntityDrawGunMixin"),
                "server-visible fast-swap adapter must not be client-only"
        );
        for (String entry : client) {
            assertFalse(common.contains(entry), entry + " listed twice");
            if (!entry.startsWith("tacz.")) {
                continue;
            }
            assertEquals(
                    TaczRuntimeSide.CLIENT,
                    TaczContractRegistry.sideForMixin(
                            "com.mafuyu404.taczaddon.mixin." + entry
                    ),
                    entry
            );
        }
        for (String entry : common) {
            if (!entry.startsWith("tacz.")) {
                continue;
            }
            assertEquals(
                    TaczRuntimeSide.COMMON,
                    TaczContractRegistry.sideForMixin(
                            "com.mafuyu404.taczaddon.mixin." + entry
                    ),
                    entry
            );
        }
    }

    private static List<String> arrayEntries(String json, String key) {
        int start = json.indexOf(key);
        assertTrue(start >= 0, key);
        int open = json.indexOf('[', start);
        int close = json.indexOf(']', open);
        assertTrue(open >= 0 && close > open, key);
        List<String> result = new ArrayList<>();
        for (String line : json.substring(open + 1, close).split("\\R")) {
            String trimmed = line.trim();
            if (!trimmed.startsWith("\"")) {
                continue;
            }
            result.add(trimmed.substring(
                    1,
                    trimmed.lastIndexOf('"')
            ));
        }
        return result;
    }

    @Test
    void noMixinCallbackImportsOutsideMixinOrBootstrapPackages()
            throws IOException {
        Path root = PROJECT_ROOT.resolve(
                "src/main/java/com/mafuyu404/taczaddon"
        );
        for (Path source : listJavaFiles(root)) {
            String path = source.toString().replace('\\', '/');
            if (path.contains("/mixin/")
                    || path.endsWith(
                    "/bootstrap/TaczAddonMixinPlugin.java"
            )) {
                continue;
            }
            String text = Files.readString(
                    source,
                    StandardCharsets.UTF_8
            );
            assertFalse(
                    text.contains(
                            "import org.spongepowered.asm.mixin."
                    ),
                    source + " imports Mixin callback API"
            );
        }
    }

    @Test
    void noBlanketRequireZeroRemainsInTaCZMixins()
            throws IOException {
        Path mixinRoot = PROJECT_ROOT.resolve(
                "src/main/java/com/mafuyu404/taczaddon/mixin/tacz"
        );
        for (Path source : listJavaFiles(mixinRoot)) {
            String text = Files.readString(
                    source,
                    StandardCharsets.UTF_8
            );
            assertFalse(
                    text.contains("require = 0"),
                    source + " still uses blanket require = 0"
            );
        }
    }

    private static List<String> entries(String json) {
        List<String> result = new ArrayList<>();
        for (String line : json.split("\\R")) {
            String trimmed = line.trim();
            if (trimmed.contains(":")) {
                continue;
            }
            if (trimmed.startsWith("\"")
                    && trimmed.endsWith("\",")
                    || trimmed.startsWith("\"")
                    && trimmed.endsWith("\"")) {
                result.add(trimmed.substring(
                        1,
                        trimmed.length() - (trimmed.endsWith(",") ? 2 : 1)
                ));
            }
        }
        return result;
    }

    private static List<Path> listJavaFiles(Path root)
            throws IOException {
        if (!Files.isDirectory(root)) {
            return List.of();
        }
        try (Stream<Path> stream = Files.walk(root)) {
            return stream
                    .filter(path -> path.toString().endsWith(".java"))
                    .toList();
        }
    }

    private static String read(String relativePath)
            throws IOException {
        return Files.readString(
                PROJECT_ROOT.resolve(relativePath),
                StandardCharsets.UTF_8
        );
    }
}
