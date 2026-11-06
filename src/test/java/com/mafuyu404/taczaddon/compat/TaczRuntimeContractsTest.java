package com.mafuyu404.taczaddon.compat;

import static org.junit.jupiter.api.Assertions.*;

import com.mafuyu404.taczaddon.mixin.TaczAddonMixinPlugin;
import com.mafuyu404.taczaddon.testutil.SourceTree;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

import java.nio.file.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

class TaczRuntimeContractsTest {
    private static final String PREFIX = "com.mafuyu404.taczaddon.mixin.";

    @Test
    void compiledAddonReferencesResolveAgainstTheSelectedRuntime() throws Exception {
        Map<String, ClassNode> classes = new HashMap<>();
        var failures = new ArrayList<String>();
        Path root = SourceTree.path("build.gradle").getParent().resolve("build/classes/java/main");
        try (var files = Files.walk(root)) {
            for (Path file : files.filter(path -> path.toString().endsWith(".class")).toList()) {
                ClassNode node = new ClassNode();
                new ClassReader(Files.readAllBytes(file)).accept(node, 0);
                for (MethodNode method : node.methods) {
                    for (var instruction : method.instructions) {
                        if (instruction instanceof MethodInsnNode call
                                && call.owner.startsWith("com/tacz/")) {
                            if (!hasMember(classes, call.owner, call.name, call.desc, true))
                                failures.add(
                                        node.name
                                                + " -> "
                                                + call.owner
                                                + "."
                                                + call.name
                                                + call.desc);
                        } else if (instruction instanceof FieldInsnNode field
                                && field.owner.startsWith("com/tacz/")) {
                            if (!hasMember(classes, field.owner, field.name, field.desc, false))
                                failures.add(
                                        node.name
                                                + " -> "
                                                + field.owner
                                                + "."
                                                + field.name
                                                + field.desc);
                        } else if (instruction instanceof InvokeDynamicInsnNode dynamic) {
                            for (Object argument : dynamic.bsmArgs) {
                                if (argument instanceof Handle handle
                                        && handle.getOwner().startsWith("com/tacz/")) {
                                    if (!hasMember(
                                            classes,
                                            handle.getOwner(),
                                            handle.getName(),
                                            handle.getDesc(),
                                            true)) failures.add(node.name + " -> " + handle);
                                }
                            }
                        }
                    }
                }
            }
        }
        assertEquals(List.of(), failures);
    }

    private boolean hasMember(
            Map<String, ClassNode> cache,
            String owner,
            String name,
            String descriptor,
            boolean method)
            throws Exception {
        if (owner == null) return false;
        ClassNode node = cache.get(owner);
        if (node == null) {
            try (var stream = getClass().getClassLoader().getResourceAsStream(owner + ".class")) {
                if (stream == null) return false;
                node = new ClassNode();
                new ClassReader(stream).accept(node, ClassReader.SKIP_CODE);
                cache.put(owner, node);
            }
        }
        if (method
                ? node.methods.stream()
                        .anyMatch(m -> m.name.equals(name) && m.desc.equals(descriptor))
                : node.fields.stream()
                        .anyMatch(f -> f.name.equals(name) && f.desc.equals(descriptor)))
            return true;
        if (name.equals("<init>")) return false;
        if (hasMember(cache, node.superName, name, descriptor, method)) return true;
        for (String implemented : node.interfaces)
            if (hasMember(cache, implemented, name, descriptor, method)) return true;
        return false;
    }

    @Test
    void selectsExactlyOneAmmoAndAimImplementation() {
        var plugin = new TaczAddonMixinPlugin();
        boolean modern =
                getClass()
                                .getClassLoader()
                                .getResource("com/tacz/guns/client/gameplay/ClientAmmoCount.class")
                        != null;
        assertEquals(
                Boolean.getBoolean("taczaddon.test.official"),
                modern,
                "Test must load the requested TACZ runtime");
        assertEquals(modern, plugin.shouldApplyMixin("", PREFIX + "ClientAmmoCountMixin"));
        assertEquals(!modern, plugin.shouldApplyMixin("", PREFIX + "GunHudOverlayMixin"));
        assertEquals(modern, plugin.shouldApplyMixin("", PREFIX + "OfficialAimKeyMixin"));
        assertEquals(!modern, plugin.shouldApplyMixin("", PREFIX + "AimKeyMixin"));
        assertEquals(modern, TaczOemCompat.isAvailable());
        assertEquals(
                modern, plugin.shouldApplyMixin("", PREFIX + "OfficialVirtualAttachmentMixin"));
    }

    @Test
    void sharedRefitContractStillMatches() {
        var plugin = new TaczAddonMixinPlugin();
        // NeoForge's JUnit launch target uses the dedicated-server side.
        assertFalse(plugin.shouldApplyMixin("", PREFIX + "GunRefitScreenMixin"));
        assertTrue(plugin.shouldApplyMixin("", PREFIX + "ClientMessageUnloadAttachmentMixin"));
    }

    @Test
    void clientTargetDeclarationsRemainLoadable() throws Exception {
        for (String name :
                List.of(
                        "client.input.AimKey",
                        "client.input.InteractKey",
                        "client.gui.GunRefitScreen",
                        "client.gui.GunSmithTableScreen",
                        "client.gui.overlay.GunHudOverlay",
                        "client.gameplay.LocalPlayerDraw",
                        "client.gameplay.LocalPlayerShoot",
                        "client.gameplay.LocalPlayerReload",
                        "client.animation.statemachine.GunAnimationStateContext",
                        "client.renderer.item.AnimateGeoItemRenderer",
                        "client.tooltip.ClientAttachmentItemTooltip")) {
            Class.forName("com.tacz.guns." + name, false, getClass().getClassLoader())
                    .getDeclaredMethods();
        }
        if (TaczOemCompat.isAvailable()) {
            Class.forName(
                            "com.tacz.guns.client.gameplay.ClientAmmoCount",
                            false,
                            getClass().getClassLoader())
                    .getDeclaredMethods();
        }
    }
}
