package com.mafuyu404.taczaddon.client;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AimCameraOwnershipArchitectureTest {
    private static final Path PROJECT_ROOT =
            Path.of("").toAbsolutePath().normalize();

    @Test
    void compositeOwnershipRemovesMutuallyExclusiveBackendModel()
            throws IOException {
        String context = read(
                "src/main/java/com/mafuyu404/taczaddon/client/camera/AimCameraContext.java"
        );
        String controller = read(
                "src/main/java/com/mafuyu404/taczaddon/client/camera/AimCameraController.java"
        );

        assertTrue(context.contains("boolean shoulderSurfingActive"));
        assertTrue(context.contains("@Nullable String perspectiveApiId"));
        assertTrue(controller.contains(
                "ShoulderSurfing5Compat.isShoulderSurfing()"
        ));
        assertTrue(controller.contains(
                "PerspectiveApiCompat.currentNonVanillaPerspectiveId()"
        ));
    }

    @Test
    void staleFallbackAndInternalApiReferencesAreAbsent()
            throws IOException {
        String allJava;
        try (Stream<Path> paths = Files.walk(
                PROJECT_ROOT.resolve("src")
        )) {
            allJava = paths
                    .filter(path -> path.toString().endsWith(".java"))
                    .map(this::readPath)
                    .collect(Collectors.joining());
        }

        assertFalse(allJava.contains(
                "PERSPECTIVE_" + "VANILLA_FALLBACK"
        ));
        assertFalse(allJava.contains(
                "io.github.leawind.thirdperson." + "internal"
        ));
        assertFalse(allJava.contains(
                "com.github.exopandora.shouldersurfing." + "client."
        ));
    }

    private String read(String relativePath) throws IOException {
        return readPath(PROJECT_ROOT.resolve(relativePath));
    }

    private String readPath(Path path) {
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new RuntimeException(exception);
        }
    }
}
