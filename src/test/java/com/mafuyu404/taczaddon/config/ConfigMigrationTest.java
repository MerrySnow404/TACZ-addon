package com.mafuyu404.taczaddon.config;

import static org.junit.jupiter.api.Assertions.*;

import com.electronwill.nightconfig.core.file.CommentedFileConfig;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.*;
import java.util.List;

class ConfigMigrationTest {
    @TempDir Path directory;

    @Test
    void preservesPreviouslyValidTooltipLimits() throws Exception {
        var spec =
                (net.neoforged.neoforge.common.ModConfigSpec.ValueSpec)
                        ClientConfig.SPEC
                                .getSpec()
                                .get(List.of("Attachment Setting", "allowGunDisplayCount"));
        for (int limit : new int[] {0, 257, 1024}) {
            Path configDir = Files.createDirectory(directory.resolve("limit-" + limit));
            Files.writeString(
                    configDir.resolve("taczaddon-common.toml"),
                    "[\"GunSmithTable Setting\"]\n"
                        + "[\"Attachment Setting\"]\n"
                        + "maxDisplayedCompatibleGuns = "
                            + limit
                            + "\n");
            ConfigMigration.migrate(configDir);
            try (var client = CommentedFileConfig.of(configDir.resolve("taczaddon-client.toml"))) {
                client.load();
                assertEquals(
                        limit,
                        (Integer)
                                client.get(List.of("Attachment Setting", "allowGunDisplayCount")));
                assertTrue(spec.test(limit), "Migrated value must also survive config validation");
            }
        }
    }

    private void legacy() throws Exception {
        Files.writeString(
                directory.resolve("taczaddon-common.toml"),
                """
                ["GunSmithTable Setting"]
                enableContainerReader = false
                containerScanRadius = 9
                massCraftCount = 32
                enableCraftToast = false
                ["Other Setting"]
                enableFastSwapGun = false
                enableBetterAimCamera = false
                ["Melee Setting"]
                meleeGunIds = ["test:knife"]
                """);
    }

    @Test
    void movesExistingPreferencesAndPoliciesWithoutLosingTheOriginal() throws Exception {
        legacy();
        String original = Files.readString(directory.resolve("taczaddon-common.toml"));
        ConfigMigration.migrate(directory);
        assertEquals(
                original,
                Files.readString(directory.resolve("taczaddon-common.toml.pre-split.bak")));
        try (var common = CommentedFileConfig.of(directory.resolve("taczaddon-common.toml"));
                var client = CommentedFileConfig.of(directory.resolve("taczaddon-client.toml"))) {
            common.load();
            client.load();
            assertEquals(9, (Integer) common.get("GunSmithTable.containerScanRadius"));
            assertEquals(false, common.get("Gameplay.enableFastSwapGun"));
            assertFalse(common.contains(List.of("GunSmithTable Setting")));
            assertEquals(false, client.get(List.of("Other Setting", "enableBetterAimCamera")));
            assertEquals(
                    List.of("test:knife"), client.get(List.of("Melee Setting", "MeleeWeaponList")));
        }
    }

    @Test
    void newKeysWinAndRepeatingMigrationNeverOverwritesEdits() throws Exception {
        legacy();
        Files.writeString(
                directory.resolve("taczaddon-client.toml"),
                "[\"Other Setting\"]\nenableBetterAimCamera = true\n");
        Files.writeString(
                directory.resolve("taczaddon-common.toml"),
                "\n[Gameplay]\nenableFastSwapGun = true\n",
                StandardOpenOption.APPEND);
        ConfigMigration.migrate(directory);
        String client = Files.readString(directory.resolve("taczaddon-client.toml"));
        assertTrue(client.contains("enableBetterAimCamera = true"));
        assertTrue(
                Files.readString(directory.resolve("taczaddon-common.toml"))
                        .contains("enableFastSwapGun = true"));
        ConfigMigration.migrate(directory);
        assertEquals(client, Files.readString(directory.resolve("taczaddon-client.toml")));
    }

    @Test
    void interruptedMigrationResumesFromBackupAndInvalidValuesUseDefaults() throws Exception {
        Files.writeString(
                directory.resolve("taczaddon-common.toml.pre-split.bak"),
                "[\"GunSmithTable Setting\"]\ncontainerScanRadius = 999\nmassCraftCount = 0\n");
        Files.writeString(
                directory.resolve("taczaddon-common.toml"),
                "[Gameplay]\nenableFastSwapGun = false\n");
        ConfigMigration.migrate(directory);
        try (var common = CommentedFileConfig.of(directory.resolve("taczaddon-common.toml"))) {
            common.load();
            assertEquals(3, (Integer) common.get("GunSmithTable.containerScanRadius"));
            assertEquals(64, (Integer) common.get("GunSmithTable.batchCraftMax"));
            assertEquals(false, common.get("Gameplay.enableFastSwapGun"));
        }
        assertTrue(Files.exists(directory.resolve("taczaddon-config-migration-v1.done")));
    }
}
