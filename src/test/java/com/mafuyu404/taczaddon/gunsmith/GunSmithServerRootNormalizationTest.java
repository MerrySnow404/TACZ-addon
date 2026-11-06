package com.mafuyu404.taczaddon.gunsmith;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression guard for the multi-block gunsmith root normalization.
 *
 * The invariant under test is that getRootPos() must run before the root
 * BlockEntity lookup, and that the session manager stays fail-closed for a
 * null/removed root table. This is a lightweight source-structure test on
 * purpose: exercising real TaCZ multi-block blocks would require a full
 * Minecraft level fixture, which is not worth the abstraction here.
 */
class GunSmithServerRootNormalizationTest {
    private static final Path PROJECT_ROOT =
            Path.of("").toAbsolutePath().normalize();

    @Test
    void rootNormalizationPrecedesBlockEntityLookup()
            throws IOException {
        String serverEvent = readProjectFile(
                "src/main/java/com/mafuyu404/taczaddon/event/ServerEvent.java"
        );

        int blockState = serverEvent.indexOf(
                "getBlockState(tablePos)"
        );
        int instanceofBlock = serverEvent.indexOf(
                "instanceof AbstractGunSmithTableBlock"
        );
        int rootPos = serverEvent.indexOf("getRootPos(");
        int blockEntity = serverEvent.indexOf(
                "getBlockEntity(tablePos)"
        );
        int remember = serverEvent.indexOf(
                "rememberTableInteraction("
        );

        assertTrue(blockState >= 0, "missing getBlockState(tablePos)");
        assertTrue(instanceofBlock >= 0);
        assertTrue(rootPos >= 0, "missing getRootPos(");
        assertTrue(blockEntity >= 0);
        assertTrue(remember >= 0);

        assertTrue(
                blockState < instanceofBlock,
                "block state must be read before the table block check"
        );
        assertTrue(
                instanceofBlock < rootPos,
                "getRootPos must only run after the table block check"
        );
        assertTrue(
                rootPos < blockEntity,
                "getRootPos must run before the root BlockEntity lookup"
        );
        assertTrue(
                blockEntity < remember,
                "root BlockEntity lookup must precede session remember"
        );
    }

    @Test
    void sessionManagerStaysFailClosedForNullRoot()
            throws IOException {
        String manager = readProjectFile(
                "src/main/java/com/mafuyu404/taczaddon/gunsmith/GunSmithCraftingSessionManager.java"
        );

        assertTrue(manager.contains(
                "blockId == null || table.isRemoved()"
        ));
        assertTrue(manager.contains(
                "PENDING_INTERACTIONS.remove(player.getUUID())"
        ));
        assertTrue(manager.contains("tablePos.immutable()"));
        assertTrue(manager.contains("withinTtl"));
    }

    private static String readProjectFile(String relativePath)
            throws IOException {
        return Files.readString(
                PROJECT_ROOT.resolve(relativePath),
                StandardCharsets.UTF_8
        );
    }
}
