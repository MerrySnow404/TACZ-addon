package com.mafuyu404.taczaddon.compat.tacz;

import com.mafuyu404.taczaddon.compat.tacz.contract.TaczFeature;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;

import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

class GunSmithFilterContractTest {
    private static final String SCREEN = "com.tacz.guns.client.gui.GunSmithTableScreen";
    @Test void classificationRejectsMissingAndAdditionalListAddCalls() {
        for (boolean duplicate : new boolean[]{false, true}) {
            var result = probe(TaczFeature.GUNSMITH_PROPERTY_FILTER, node -> {
                var method = node.methods.stream().filter(m -> m.name.equals("classifyRecipes")).findFirst().orElseThrow();
                for (var insn : method.instructions.toArray()) if (insn instanceof MethodInsnNode call
                        && call.owner.equals("java/util/List") && call.name.equals("add")) {
                    if (duplicate) method.instructions.insert(insn, new MethodInsnNode(call.getOpcode(), call.owner, call.name, call.desc, call.itf));
                    else method.instructions.remove(insn);
                    break;
                }
            });
            assertFalse(result.passed()); assertTrue(result.detail().contains("bad invocation count"));
        }
    }
    @Test void pageInfoRejectsChangedDrawStringMultiplicity() {
        var result = probe(TaczFeature.GUNSMITH_PAGE_INFO, node -> {
            var render = node.methods.stream().filter(m -> m.name.equals("render")).findFirst().orElseThrow();
            for (var insn : render.instructions.toArray()) if (insn instanceof MethodInsnNode call
                    && call.name.equals("drawString") && call.desc.contains("chat/Component")) {
                render.instructions.remove(insn); break;
            }
        });
        assertFalse(result.passed()); assertTrue(result.detail().contains("bad invocation count"));
    }
    @Test void allRequiredFilterMembersFailClosedWhenMissing() {
        for (String name : new String[]{"init", "classifyRecipes", "updateIngredientCount", "updateSelectedRecipeAfterFiltering",
                "mouseScrolled", "getSelectedRecipe", "selectedRecipeList", "indexPage", "typePage", "recipes", "recipeKeys"}) {
            var result = probe(TaczFeature.GUNSMITH_PROPERTY_FILTER, node -> {
                node.methods.removeIf(m -> m.name.equals(name)); node.fields.removeIf(f -> f.name.equals(name));
            });
            assertFalse(result.passed(), name);
        }
    }
    private static TaczBinaryProbe.ProbeResult probe(TaczFeature feature, Consumer<ClassNode> mutation) {
        return TaczBinaryProbe.inspect(TaczContractRegistry.contractFor(feature), name -> {
            try (var input = GunSmithFilterContractTest.class.getClassLoader().getResourceAsStream(name.replace('.', '/') + ".class")) {
                assertNotNull(input); byte[] original = input.readAllBytes();
                if (!name.equals(SCREEN)) return original;
                ClassNode node = new ClassNode(); new ClassReader(original).accept(node, 0); mutation.accept(node);
                ClassWriter writer = new ClassWriter(0); node.accept(writer); return writer.toByteArray();
            } catch (java.io.IOException error) { throw new AssertionError(error); }
        });
    }
}
