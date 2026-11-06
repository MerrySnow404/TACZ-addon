package com.mafuyu404.taczaddon.init.crafting;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NearbyContainerArchitectureTest {
    private static final Path PROJECT_ROOT =
            Path.of("").toAbsolutePath().normalize();

    @Test
    void genericResolverOwnsTheOnlyNearbyIteration()
            throws IOException {
        String resolver = read(
                "src/main/java/com/mafuyu404/taczaddon/storage/NearbyInventorySourceResolver.java"
        );
        String gunsmith = read(
                "src/main/java/com/mafuyu404/taczaddon/gunsmith/GunSmithCraftingSources.java"
        );

        assertTrue(resolver.contains("level.isLoaded(pos)"));
        assertTrue(resolver.contains(
                "new ContainerItemSource(level, pos)"
        ));
        assertFalse(resolver.contains("level.getChunk(pos"));
        assertFalse(resolver.contains("setChunkForced"));
        assertFalse(resolver.contains("setForcedChunkLoadingPos"));

        assertTrue(gunsmith.contains(
                "NearbyInventorySourceResolver.resolve"
        ));
        assertFalse(gunsmith.contains("BlockPos.betweenClosed"));
        assertFalse(gunsmith.contains("new ContainerItemSource"));
        assertFalse(gunsmith.contains("resolveNearbyContainers"));
    }

    @Test
    void standardTableSupportNeverUsesBlockIdWhitelists()
            throws IOException {
        String sessionManager = read(
                "src/main/java/com/mafuyu404/taczaddon/gunsmith/GunSmithCraftingSessionManager.java"
        );
        String gunsmith = read(
                "src/main/java/com/mafuyu404/taczaddon/gunsmith/GunSmithCraftingSources.java"
        );
        String serverEvent = read(
                "src/main/java/com/mafuyu404/taczaddon/event/ServerEvent.java"
        );

        for (String forbidden : new String[] {
                "gun_smith_table_a",
                "gun_smith_table_b",
                "gun_smith_table_c",
                "tacz:gun_smith_table"
        }) {
            assertFalse(sessionManager.contains(forbidden));
            assertFalse(gunsmith.contains(forbidden));
            assertFalse(serverEvent.contains(forbidden));
        }

        assertTrue(serverEvent.contains(
                "AbstractGunSmithTableBlock"
        ));
        assertTrue(serverEvent.contains(
                "tableBlock.getRootPos"
        ));

        assertTrue(Files.isRegularFile(PROJECT_ROOT.resolve(
                "src/main/java/com/mafuyu404/taczaddon/storage/api/WorkbenchAnchorProvider.java"
        )));
        assertTrue(Files.isRegularFile(PROJECT_ROOT.resolve(
                "src/main/java/com/mafuyu404/taczaddon/storage/WorkbenchAnchorRegistry.java"
        )));

        String provider = read(
                "src/main/java/com/mafuyu404/taczaddon/gunsmith/TaczWorkbenchAnchorProvider.java"
        );
        assertTrue(provider.contains("GunSmithTableMenu"));
        assertTrue(provider.contains("session.getTablePos()"));
    }

    @Test
    void configAndDependencyMetadataUseSharedSemantics()
            throws IOException {
        String config = read(
                "src/main/java/com/mafuyu404/taczaddon/config/CommonConfig.java"
        );
        assertTrue(config.contains("enableNearbyContainerSources"));
        assertTrue(config.contains("getNearbyContainerScanRadius"));
        assertTrue(config.contains("enableContainerReader"));
        assertTrue(config.contains("containerScanRadius"));

        String mods = read(
                "src/main/resources/META-INF/mods.toml"
        );
        assertFalse(mods.contains("modId=\"perspective_api\""));
        assertTrue(mods.contains("modId=\"leawind_third_person\""));
        assertTrue(mods.contains("mandatory=false"));
        assertTrue(mods.contains("versionRange=\"[0,)\""));

        String gradle = read("build.gradle");
        assertTrue(gradle.contains(
                "implementation fg.deobf(\"curse.maven:leawind-third-person-930880:8602826\")"
        ));
        assertTrue(gradle.contains(
                "compileOnly fg.deobf(\"maven.modrinth:LIqveQm1:1.5.0-beta+forge-1.20.1\")"
        ));
        assertFalse(gradle.contains(
                "implementation fg.deobf(\"maven.modrinth:LIqveQm1"
        ));
    }

    private static String read(String relativePath)
            throws IOException {
        return Files.readString(
                PROJECT_ROOT.resolve(relativePath),
                StandardCharsets.UTF_8
        );
    }
}
