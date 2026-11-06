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
    private static Boolean supported;

    private BeyondAmmoCacheContract() {}

    public static synchronized boolean isSupported() {
        if (supported == null) {
            supported = supports(ApiShapeProbe.sourceFor(BeyondAmmoCacheContract.class));
        }
        return supported;
    }

    static boolean supports(ApiShapeProbe.ClassBytes source) {
        ApiShapeProbe.Shape shape = ApiShapeProbe.inspect(source, CACHE);
        if (!shape.hasMethod("hasData", RESOURCE + "Z")
                || !shape.hasMethod("getCount", RESOURCE + "I")
                || !shape.hasMethod("requestQuick", RESOURCE + "V")
                || !shape.hasMethod("applyPending", "()V")
                || !ApiShapeProbe.hasMethod(source,
                "com.solr98.beyondintegration.feature.ammo.tacz.TaczAmmoExtractor",
                "getAmmoIdClient", "(Lnet/minecraft/world/item/ItemStack;)"
                        + "Lnet/minecraft/resources/ResourceLocation;")) {
            return false;
        }
        ClassNode node = new ClassNode();
        new ClassReader(source.read(CACHE)).accept(node, ClassReader.SKIP_DEBUG);
        return node.methods.stream().filter(method -> method.name.equals("applyPending")
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
    }
}
