package com.mafuyu404.taczaddon.config;

import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import com.mojang.logging.LogUtils;

import java.io.IOException;
import java.nio.file.*;
import java.util.List;

/** Runs before FML opens either config; a backup also makes interrupted migration retryable. */
public final class ConfigMigration {
    private ConfigMigration() {}

    private record Entry(
            String group,
            String oldKey,
            String newGroup,
            String newKey,
            Object fallback,
            int min,
            int max,
            boolean client) {}

    private static final List<Entry> ENTRIES =
            List.of(
                    new Entry(
                            "Melee Setting",
                            "meleeGunIds",
                            "Melee Setting",
                            "MeleeWeaponList",
                            List.of("tacz:type_82", "tacz:type_83"),
                            0,
                            0,
                            true),
                    new Entry(
                            "GunSmithTable Setting",
                            "enableCraftToast",
                            "GunSmithTable Presentation",
                            "enableCraftToast",
                            true,
                            0,
                            0,
                            true),
                    new Entry(
                            "Attachment Setting",
                            "maxDisplayedCompatibleGuns",
                            "Attachment Setting",
                            "allowGunDisplayCount",
                            16,
                            0,
                            1024,
                            true),
                    new Entry(
                            "Attachment Setting",
                            "enableAttachmentDetail",
                            "Attachment Setting",
                            "enableAttachmentDetail",
                            true,
                            0,
                            0,
                            true),
                    new Entry(
                            "Other Setting",
                            "enableBetterAimCamera",
                            "Other Setting",
                            "enableBetterAimCamera",
                            true,
                            0,
                            0,
                            true),
                    new Entry(
                            "Other Setting",
                            "enableShowItemRelation",
                            "Other Setting",
                            "enableShowItemRelation",
                            true,
                            0,
                            0,
                            true),
                    new Entry(
                            "Other Setting",
                            "enableShowItemRelationInSophisticatedStorage",
                            "Other Setting",
                            "enableShowItemRelationInSophisticatedStorage",
                            true,
                            0,
                            0,
                            true),
                    new Entry(
                            "Gun Setting",
                            "enableShootWhileReloading",
                            "Gameplay",
                            "enableShootWhileReloading",
                            true,
                            0,
                            0,
                            false),
                    new Entry(
                            "Other Setting",
                            "enableFastSwapGun",
                            "Gameplay",
                            "enableFastSwapGun",
                            true,
                            0,
                            0,
                            false),
                    new Entry(
                            "GunSmithTable Setting",
                            "enableContainerReader",
                            "GunSmithTable",
                            "enableContainerReader",
                            true,
                            0,
                            0,
                            false),
                    new Entry(
                            "GunSmithTable Setting",
                            "containerScanRadius",
                            "GunSmithTable",
                            "containerScanRadius",
                            3,
                            1,
                            16,
                            false),
                    new Entry(
                            "GunSmithTable Setting",
                            "massCraftCount",
                            "GunSmithTable",
                            "batchCraftMax",
                            64,
                            1,
                            64,
                            false));

    public static void migrate(Path directory) {
        Path commonPath = directory.resolve("taczaddon-common.toml");
        Path clientPath = directory.resolve("taczaddon-client.toml");
        Path backup = directory.resolve("taczaddon-common.toml.pre-split.bak");
        Path marker = directory.resolve("taczaddon-config-migration-v1.done");
        if (Files.exists(marker) || !Files.exists(commonPath)) return;
        try (var common = CommentedFileConfig.of(commonPath);
                var client = CommentedFileConfig.of(clientPath)) {
            common.load();
            if (!Files.exists(backup) && !common.contains(List.of("GunSmithTable Setting"))) return;
            if (Files.exists(clientPath)) client.load();
            if (!Files.exists(backup)) Files.copy(commonPath, backup);
            // Specify TOML explicitly: the immutable backup ends in .bak.
            try (var old =
                    CommentedFileConfig.builder(
                                    backup, com.electronwill.nightconfig.toml.TomlFormat.instance())
                            .build()) {
                old.load();
                for (Entry entry : ENTRIES) {
                    var target = entry.client ? client : common;
                    var newPath = List.of(entry.newGroup, entry.newKey);
                    if (target.contains(newPath)) continue;
                    Object value = old.get(List.of(entry.group, entry.oldKey));
                    if (!valid(entry, value)) {
                        if (value != null)
                            LogUtils.getLogger()
                                    .warn(
                                            "Invalid old config {}.{}; using default",
                                            entry.group,
                                            entry.oldKey);
                        value = entry.fallback;
                    }
                    target.set(newPath, value);
                }
            }
            for (String group :
                    List.of(
                            "Melee Setting",
                            "Gun Setting",
                            "GunSmithTable Setting",
                            "Attachment Setting",
                            "Other Setting")) common.remove(List.of(group));
            // Client first. If interrupted, its already-written keys win on the next attempt.
            saveAtomically(clientPath, client);
            saveAtomically(commonPath, common);
            Files.writeString(
                    marker,
                    "Migrated client/common configuration; original retained in "
                            + backup.getFileName());
        } catch (IOException | RuntimeException failure) {
            throw new IllegalStateException(
                    "Cannot migrate TACZ-addon config; original backup is retained", failure);
        }
    }

    private static boolean valid(Entry entry, Object value) {
        if (entry.fallback instanceof Boolean) return value instanceof Boolean;
        if (entry.fallback instanceof Integer)
            return (value instanceof Integer || value instanceof Long)
                    && ((Number) value).longValue() >= entry.min
                    && ((Number) value).longValue() <= entry.max;
        return value instanceof List<?> list
                && list.stream()
                        .allMatch(
                                v ->
                                        v instanceof String id
                                                && id.matches("(?:[a-z0-9_.-]+:)?[a-z0-9_./-]+"));
    }

    private static void saveAtomically(Path destination, CommentedFileConfig config)
            throws IOException {
        Path temporary = destination.resolveSibling(destination.getFileName() + ".migration.tmp");
        try (var output =
                CommentedFileConfig.builder(
                                temporary, com.electronwill.nightconfig.toml.TomlFormat.instance())
                        .sync()
                        .build()) {
            output.putAll(config);
            output.save();
        }
        try {
            Files.move(
                    temporary,
                    destination,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
