package com.mafuyu404.taczaddon.storage;

import com.mafuyu404.taczaddon.storage.ContainerItemSource;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ContainerItemSourceIdentityTest {
    @Test
    void identityGuardUsesBlockEntityAndBackendIdentity() {
        Object expectedBlockEntity = new Object();
        Object expectedBackend = new Object();

        assertTrue(ContainerItemSource.sameBackendIdentity(
                expectedBlockEntity,
                expectedBackend,
                expectedBlockEntity,
                expectedBackend
        ));
        assertFalse(ContainerItemSource.sameBackendIdentity(
                expectedBlockEntity,
                expectedBackend,
                new Object(),
                expectedBackend
        ));
        assertFalse(ContainerItemSource.sameBackendIdentity(
                expectedBlockEntity,
                expectedBackend,
                expectedBlockEntity,
                new Object()
        ));
    }

    @Test
    void unloadedPositionsAreHardRejectedWithoutChunkLoading()
            throws IOException {
        String source = Files.readString(
                Path.of(
                        "src/main/java/com/mafuyu404/taczaddon/storage/ContainerItemSource.java"
                ),
                StandardCharsets.UTF_8
        );

        assertTrue(source.contains("!level.isLoaded(pos)"));
        assertFalse(source.contains("level.getChunk(pos"));
        assertFalse(source.contains("setChunkForced"));
        assertFalse(source.contains("setForcedChunkLoadingPos"));
    }
}
