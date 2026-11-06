package com.mafuyu404.taczaddon.mixin;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SyntheticLambdaMixinCompatibilityTest {
    private static final Path PROJECT_ROOT =
            Path.of("").toAbsolutePath().normalize();

    @Test
    void noMixinSourceUsesASyntheticLambdaSelector()
            throws IOException {
        Path mixinDirectory = PROJECT_ROOT.resolve(
                "src/main/java/com/mafuyu404/taczaddon/mixin"
        );

        List<Path> sources = new ArrayList<>();
        try (Stream<Path> files = Files.walk(mixinDirectory)) {
            files.filter(path -> path.toString().endsWith(".java"))
                    .forEach(sources::add);
        }

        for (Path source : sources) {
            assertFalse(
                    read(source).contains("lambda$"),
                    () -> source.getFileName()
                            + " contains a synthetic lambda selector"
            );
        }
    }

    @Test
    void replacedBindingsUseStableNamedInjectionPoints()
            throws IOException {
        String unload = read(
                "src/main/java/com/mafuyu404/taczaddon/mixin/tacz/v1_1_8/ClientMessageUnloadAttachmentMixin.java"
        );
        String animation = read(
                "src/main/java/com/mafuyu404/taczaddon/mixin/tacz/v1_1_8/GunAnimationStateContextMixin.java"
        );
        String kinetic = read(
                "src/main/java/com/mafuyu404/taczaddon/mixin/tacz/v1_1_8/ModernKineticGunScriptAPIMixin.java"
        );
        String helper = read(
                "src/main/java/com/mafuyu404/taczaddon/ammo/BackpackAmmoService.java"
        );

        assertTrue(unload.contains(
                "enqueueWork(Ljava/lang/Runnable;)"
        ));
        assertTrue(unload.contains("LiberatedRefitService.enqueueNativeUnload"));
        assertFalse(unload.contains("VirtualInventory"));

        assertTrue(animation.contains("method = \"hasAmmoToConsume()Z\""));
        assertTrue(animation.contains("processCameraEntity("));
        assertTrue(animation.contains("index = 0"));
        assertTrue(animation.contains("require = 1"));
        assertFalse(animation.contains("VirtualInventory"));

        assertTrue(kinetic.contains(
                "method = \"consumeAmmoFromPlayer(I)I\""
        ));
        assertTrue(kinetic.contains("at = @At(\"RETURN\")"));
        assertFalse(kinetic.contains("at = @At(\"HEAD\")"));
        assertTrue(kinetic.contains("taczaddon$consumeBackpackAmmo"));
        assertTrue(kinetic.contains(
                "AmmoConsumptionOrchestrator"
        ));
        assertFalse(kinetic.contains(
                "static int consumeRemaining"
        ));
        assertFalse(kinetic.contains(
                "static int clampConsumed"
        ));
        assertFalse(kinetic.contains("getDescriptionId()"));
        assertFalse(kinetic.contains("VirtualInventory"));

        assertTrue(helper.contains("hasCompatibleAmmo"));
        assertTrue(helper.contains("consumeBackpackAmmo("));
        assertTrue(helper.contains("extractCompatibleAmmoDirectly"));
        assertFalse(helper.contains("consumeCompatibleAmmo"));
        assertFalse(helper.contains("findAndExtractInventoryAmmo"));
        assertTrue(helper.contains("isAmmoOfGun"));
        assertTrue(helper.contains("isAmmoBoxOfGun"));
    }

    private static String read(String relativePath) {
        return read(PROJECT_ROOT.resolve(relativePath));
    }

    private static String read(Path path) {
        try {
            return Files.readString(
                    path,
                    StandardCharsets.UTF_8
            );
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }
}
