package com.mafuyu404.taczaddon.compat.tacz;

import com.mafuyu404.taczaddon.compat.api.ApiShapeProbe;
import com.mafuyu404.taczaddon.testutil.CompatibilityFixtures;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.jar.JarFile;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** Checks direct API calls as well as the separately tested Mixin contracts. */
class TaczApiLinkageTest {
    @TestFactory
    Stream<DynamicTest> addonReferencesResolveInEverySupportedRelease() {
        return Stream.of("hotfix", "hotfix2", "1.2.0").map(generation ->
                DynamicTest.dynamicTest(generation, () -> verify(generation)));
    }

    private void verify(String generation) throws Exception {
        Set<String> failures = new TreeSet<>();
        try (var jar = new JarFile(CompatibilityFixtures.jar("tacz", generation, "tacz").toFile())) {
            Map<String, byte[]> cache = new HashMap<>();
            ApiShapeProbe.ClassBytes source = name -> cache.computeIfAbsent(name, key -> {
                String resource = key.replace('.', '/') + ".class";
                var entry = jar.getJarEntry(resource);
                try (var input = entry != null ? jar.getInputStream(entry)
                        : key.startsWith("com.tacz.") ? null
                        : getClass().getClassLoader().getResourceAsStream(resource)) {
                    return input == null ? null : input.readAllBytes();
                } catch (IOException e) { throw new UncheckedIOException(e); }
            });
            Path classes = Path.of(TaczContractRegistry.class.getProtectionDomain()
                    .getCodeSource().getLocation().toURI());
            try (var paths = Files.walk(classes.resolve("com/mafuyu404/taczaddon"))) {
                for (Path path : paths.filter(p -> p.toString().endsWith(".class")).toList()) {
                    if (!generation.equals("1.2.0") && path.getFileName().toString().equals("TaczGunClock$V120.class")) {
                        continue; // Deliberately lazy 1.2.0-only clock bridge.
                    }
                    ClassNode node = new ClassNode();
                    new ClassReader(Files.readAllBytes(path)).accept(node, ClassReader.SKIP_DEBUG);
                    for (MethodNode method : node.methods) {
                        for (var insn : method.instructions) {
                            if (insn instanceof MethodInsnNode call) {
                                check(source, node.name, call.owner, call.name, call.desc, true, failures);
                            } else if (insn instanceof FieldInsnNode field) {
                                check(source, node.name, field.owner, field.name, field.desc, false, failures);
                            } else if (insn instanceof InvokeDynamicInsnNode dynamic) {
                                for (Object arg : dynamic.bsmArgs) {
                                    if (arg instanceof Handle handle) {
                                        check(source, node.name, handle.getOwner(), handle.getName(),
                                                handle.getDesc(), handle.getTag() >= Opcodes.H_INVOKEVIRTUAL, failures);
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        assertTrue(failures.isEmpty(), generation + ":\n" + String.join("\n", failures));
    }

    private void check(ApiShapeProbe.ClassBytes source, String caller, String owner, String name,
                       String descriptor, boolean method, Set<String> failures) {
        if (!owner.startsWith("com/tacz/")) return;
        var member = method ? ApiShapeProbe.method(name, descriptor)
                : ApiShapeProbe.field(name, descriptor);
        if (!ApiShapeProbe.satisfies(source, owner.replace('/', '.'), List.of(member))) {
            failures.add(caller + " -> " + owner + "." + name + descriptor);
        }
    }
}
