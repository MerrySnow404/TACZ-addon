package com.mafuyu404.taczaddon.architecture;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/** Checks compiled references without loading Minecraft or optional mod classes. */
class DependencyArchitectureTest {
    private static final String ROOT = "com/mafuyu404/taczaddon/";
    private static final Pattern DESCRIPTOR = Pattern.compile("L([^;<]+)");

    @Test
    void featurePackagesHaveNoDependencyCycles() throws Exception {
        Map<String, Set<String>> classes = dependencies();
        Map<String, Set<String>> packages = new TreeMap<>();
        Map<String, String> evidence = new HashMap<>();
        classes.forEach((from, targets) -> {
            String source = packageOf(from);
            packages.computeIfAbsent(source, ignored -> new TreeSet<>());
            for (String to : targets) {
                if (!classes.containsKey(to) || source.equals(packageOf(to))) continue;
                String target = packageOf(to);
                packages.get(source).add(target);
                evidence.putIfAbsent(source + " -> " + target, from + " -> " + to);
            }
        });
        Set<String> complete = new HashSet<>();
        for (String name : packages.keySet()) visit(name, packages, complete, new ArrayList<>(), evidence);
    }

    @Test
    void transportBusinessAndMixinBoundariesRemainOneWay() throws Exception {
        for (var entry : dependencies().entrySet()) {
            String from = entry.getKey().substring(ROOT.length());
            for (String target : entry.getValue()) {
                String to = target.substring(ROOT.length());
                String edge = from + " -> " + to;
                if (from.startsWith("network/") && from.endsWith("Packet")) {
                    assertFalse(to.startsWith("client/") || to.startsWith("network/server/")
                            || to.equals("network/ClientPacketDispatch") || to.startsWith("bootstrap/"), edge);
                }
                if (List.of("gunsmith/", "refit/", "ammo/", "gun/", "storage/", "rule/")
                        .stream().anyMatch(from::startsWith)) {
                    assertFalse(to.startsWith("network/") || to.startsWith("client/"), edge);
                }
                if (!from.startsWith("mixin/")) assertFalse(to.startsWith("mixin/"), edge);
                if (from.startsWith("network/")) assertFalse(to.startsWith("client/"), edge);
            }
        }
    }

    private static void visit(String name, Map<String, Set<String>> graph, Set<String> complete,
                              List<String> path, Map<String, String> evidence) {
        int cycleStart = path.indexOf(name);
        if (cycleStart >= 0) {
            List<String> cycle = new ArrayList<>(path.subList(cycleStart, path.size()));
            cycle.add(name);
            List<String> edges = new ArrayList<>();
            for (int i = 1; i < cycle.size(); i++) {
                edges.add(evidence.get(cycle.get(i - 1) + " -> " + cycle.get(i)));
            }
            fail("Package dependency cycle: " + String.join(" -> ", cycle) + "\n" + String.join("\n", edges));
        }
        if (complete.contains(name)) return;
        path.add(name);
        for (String target : graph.getOrDefault(name, Set.of())) visit(target, graph, complete, path, evidence);
        path.remove(path.size() - 1);
        complete.add(name);
    }

    private static String packageOf(String name) { return name.substring(0, name.lastIndexOf('/')); }

    private static Map<String, Set<String>> dependencies() throws Exception {
        Path root = Path.of("src/main/java");
        Map<String, Set<String>> result = new TreeMap<>();
        try (var files = Files.walk(root)) {
            for (Path source : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                String name = root.relativize(source).toString().replace('\\', '/').replace(".java", "");
                readClass(name, result);
            }
        }
        assertTrue(result.size() > 180, "Must inspect the production source set, not an empty or obsolete directory");
        return result;
    }

    private static void readClass(String name, Map<String, Set<String>> result) throws Exception {
        if (result.containsKey(name)) return;
        ClassNode node = new ClassNode();
        try (var input = DependencyArchitectureTest.class.getClassLoader().getResourceAsStream(name + ".class")) {
            assertNotNull(input, name);
            new ClassReader(input).accept(node, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        }
        Set<String> refs = new TreeSet<>();
        result.put(name, refs);
        add(refs, node.superName);
        node.interfaces.forEach(n -> add(refs, n));
        descriptor(refs, node.signature);
        for (FieldNode field : node.fields) { descriptor(refs, field.desc); descriptor(refs, field.signature); }
        for (MethodNode method : node.methods) {
            descriptor(refs, method.desc);
            descriptor(refs, method.signature);
            method.exceptions.forEach(n -> add(refs, n));
            for (TryCatchBlockNode block : method.tryCatchBlocks) add(refs, block.type);
            for (AbstractInsnNode instruction : method.instructions) {
                if (instruction instanceof MethodInsnNode call) { add(refs, call.owner); descriptor(refs, call.desc); }
                else if (instruction instanceof FieldInsnNode field) { add(refs, field.owner); descriptor(refs, field.desc); }
                else if (instruction instanceof TypeInsnNode type) add(refs, type.desc);
                else if (instruction instanceof MultiANewArrayInsnNode array) descriptor(refs, array.desc);
                else if (instruction instanceof LdcInsnNode constant) constant(refs, constant.cst);
                else if (instruction instanceof InvokeDynamicInsnNode dynamic) {
                    descriptor(refs, dynamic.desc);
                    constant(refs, dynamic.bsm);
                    for (Object argument : dynamic.bsmArgs) constant(refs, argument);
                }
            }
        }
        // Nested/anonymous classes contain real dependencies too, including lambda implementations.
        for (InnerClassNode inner : node.innerClasses) {
            if (inner.name.startsWith(name + "$")) readClass(inner.name, result);
        }
    }

    private static void constant(Set<String> refs, Object value) {
        if (value instanceof Type type) descriptor(refs, type.getDescriptor());
        else if (value instanceof Handle handle) { add(refs, handle.getOwner()); descriptor(refs, handle.getDesc()); }
    }

    private static void descriptor(Set<String> refs, String descriptor) {
        if (descriptor == null) return;
        var matcher = DESCRIPTOR.matcher(descriptor);
        while (matcher.find()) add(refs, matcher.group(1));
    }

    private static void add(Set<String> refs, String name) {
        if (name != null && name.startsWith(ROOT)) refs.add(name);
    }
}
