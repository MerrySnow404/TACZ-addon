package com.mafuyu404.taczaddon.architecture;

import static org.junit.jupiter.api.Assertions.*;

import com.mafuyu404.taczaddon.testutil.SourceTree;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.Remapper;

import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Checks compiled dependencies, including descriptors and lambda handles, without loading classes.
 */
class FeatureBoundariesTest {
    private static final String ROOT = "com/mafuyu404/taczaddon/";

    @Test
    void featureDependenciesRespectRuntimeBoundaries() throws Exception {
        List<String> violations = new ArrayList<>();
        try (var files =
                Files.walk(
                        SourceTree.path("build.gradle")
                                .getParent()
                                .resolve("build/classes/java/main"))) {
            for (var file : files.filter(p -> p.toString().endsWith(".class")).toList()) {
                ClassReader reader = new ClassReader(Files.readAllBytes(file));
                String owner = reader.getClassName();
                if (!owner.startsWith(ROOT)) continue;
                String local = owner.substring(ROOT.length());
                assertFalse(
                        local.startsWith("init/")
                                || local.startsWith("common/")
                                || local.startsWith("client/")
                                || local.startsWith("event/"),
                        owner);
                Set<String> references = new HashSet<>();
                reader.accept(
                        new ClassRemapper(
                                new ClassWriter(0),
                                new Remapper() {
                                    @Override
                                    public String map(String name) {
                                        references.add(name);
                                        return name;
                                    }
                                }),
                        0);
                for (String dependency : references) {
                    boolean optional =
                            dependency.startsWith("net/p3pp3rf1y/")
                                    || dependency.startsWith("net/fxnt/")
                                    || dependency.startsWith("top/warmj/")
                                    || dependency.startsWith("com/solr98/")
                                    || dependency.startsWith("top/theillusivec4/curios/")
                                    || dependency.startsWith("mezz/jei/")
                                    || dependency.startsWith("com/github/exopandora/");
                    if (optional && !local.startsWith("compat/") && !local.startsWith("mixin/")) {
                        violations.add(owner + " exposes optional dependency " + dependency);
                    }
                    boolean sharedInventory =
                            local.startsWith("inventory/")
                                    && !local.startsWith("inventory/client/")
                                    && !local.startsWith("inventory/network/");
                    if (sharedInventory
                            && (dependency.startsWith(ROOT + "gunsmith/")
                                    || dependency.startsWith(ROOT + "refit/")
                                    || dependency.startsWith(ROOT + "compat/"))) {
                        violations.add(owner + " depends on feature/integration " + dependency);
                    }
                    boolean core =
                            !local.contains("/client/")
                                    && !local.contains("/network/")
                                    && !local.startsWith("compat/")
                                    && !local.startsWith("mixin/")
                                    && !local.startsWith("bootstrap/");
                    if (core
                            && (dependency.startsWith("net/minecraft/client/")
                                    || dependency.startsWith("com/tacz/guns/client/")
                                    || (dependency.startsWith(ROOT)
                                            && dependency.contains("/client/")))) {
                        violations.add(owner + " loads a client type " + dependency);
                    }
                    if (!local.startsWith("mixin/") && dependency.startsWith(ROOT + "mixin/")) {
                        violations.add(owner + " depends on a Mixin implementation " + dependency);
                    }
                }
            }
        }
        assertEquals(List.of(), violations);
    }
}
