package com.mafuyu404.taczaddon.compat.beyond;

import com.mafuyu404.taczaddon.compat.api.ApiShapeProbe;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;

/** Byte-only gate; optional client classes must not be linked by the Mixin plugin. */
public final class BeyondAmmoCacheContract {
    public static final String CACHE = "com.solr98.beyondintegration.client.TaczAmmoCache";
    private static final String RESOURCE = "(Lnet/minecraft/resources/ResourceLocation;)";
    private enum CacheLayout { UNKNOWN, LEGACY_SNAPSHOT, MIRROR }
    private static CacheLayout layout;

    private BeyondAmmoCacheContract() {}

    private static synchronized CacheLayout layout() {
        if (layout == null) {
            layout = detect(ApiShapeProbe.sourceFor(BeyondAmmoCacheContract.class));
        }
        return layout;
    }

    public static boolean isSupported() {
        return layout() != CacheLayout.UNKNOWN;
    }

    public static boolean needsSnapshotFix() {
        return layout() == CacheLayout.LEGACY_SNAPSHOT;
    }

    private static CacheLayout detect(ApiShapeProbe.ClassBytes source) {
        ApiShapeProbe.Shape shape = ApiShapeProbe.inspect(source, CACHE);
        if (!shape.hasMethod("hasData", RESOURCE + "Z")
                || !shape.hasMethod("getCount", RESOURCE + "I")
                || !shape.hasMethod("requestQuick", RESOURCE + "V")
                || !shape.hasMethod("applyPending", "()V")
                || !ApiShapeProbe.hasMethod(source,
                "com.solr98.beyondintegration.feature.ammo.tacz.TaczAmmoExtractor",
                "getAmmoIdClient", "(Lnet/minecraft/world/item/ItemStack;)"
                        + "Lnet/minecraft/resources/ResourceLocation;")) {
            return CacheLayout.UNKNOWN;
        }
        ClassNode node = new ClassNode();
        new ClassReader(source.read(CACHE)).accept(node, ClassReader.SKIP_DEBUG);
        boolean legacy = node.methods.stream().filter(method -> method.name.equals("applyPending")
                && method.desc.equals("()V")).anyMatch(method -> {
            int matches = 0;
            for (var instruction : method.instructions) {
                if (instruction instanceof MethodInsnNode call
                        && call.getOpcode() == Opcodes.INVOKEINTERFACE
                        && call.owner.equals("java/util/Map")
                        && call.name.equals("putAll")
                        && call.desc.equals("(Ljava/util/Map;)V")) {
                    matches++;
                }
            }
            return matches == 1;
        });
        if (legacy) return CacheLayout.LEGACY_SNAPSHOT;
        String mirror = "com.solr98.beyondintegration.client.TaczAmmoMirror";
        return ApiShapeProbe.hasMethod(source, mirror, "getCount", RESOURCE + "I")
                && shape.hasField("pendingPartial", "Z") ? CacheLayout.MIRROR : CacheLayout.UNKNOWN;
    }
}
