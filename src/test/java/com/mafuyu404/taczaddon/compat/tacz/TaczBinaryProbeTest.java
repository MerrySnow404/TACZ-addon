package com.mafuyu404.taczaddon.compat.tacz;

import com.mafuyu404.taczaddon.compat.tacz.contract.TaczFeature;

import com.mafuyu404.taczaddon.compat.tacz.contract.FeatureContract;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TaczBinaryProbeTest {
    private static final List<TaczFeature> FRAGILE_FEATURES = List.of(
            TaczFeature.BACKPACK_AMMO_QUERY,
            TaczFeature.BACKPACK_INVENTORY_FEED_QUERY,
            TaczFeature.BACKPACK_AMMO_CONSUME,
            TaczFeature.LIBERATED_REFIT,
            TaczFeature.GUNSMITH_SESSION,
            TaczFeature.GUNSMITH_SCREEN_ACCESS,
            TaczFeature.GUNSMITH_EXTERNAL_SOURCE_VIEW,
            TaczFeature.GUNSMITH_CRAFT_BRIDGE,
            TaczFeature.GUNSMITH_BROWSE_MEMORY,
            TaczFeature.GUNSMITH_PROPERTY_FILTER,
            TaczFeature.GUNSMITH_PAGE_INFO,
            TaczFeature.GUNSMITH_INGREDIENT_INTERACTION,
            TaczFeature.FAST_SWAP,
            TaczFeature.SHOOT_WHILE_RELOADING,
            TaczFeature.SLIDE_SHOOT,
            TaczFeature.HUD_AMMO,
            TaczFeature.TOOLTIP_EXTENSION,
            TaczFeature.CLIENT_ANIMATION,
            TaczFeature.BETTER_MELEE,
            TaczFeature.AIM_CAMERA,
            TaczFeature.CRAWL_DISABLE,
            TaczFeature.TACZ_SSR5_CROSSHAIR
    );

    @Test
    void everyVersionAdapterContractPassesAgainstResolvedTaCZ()
            throws Exception {
        for (TaczFeature feature : FRAGILE_FEATURES) {
            FeatureContract contract =
                    TaczContractRegistry.contractFor(feature, TaczCompatibility.profile());
            assertTrue(
                    contract != null
                            && !contract.classes().isEmpty(),
                    feature + " must have a binary contract"
            );
            TaczBinaryProbe.ProbeResult result =
                    TaczBinaryProbe.inspect(contract);
            assertTrue(
                    result.passed(),
                    feature + " contract failed: " + result.detail()
            );
        }
    }

    @Test
    void ingredientInteractionProbeAcceptsCorrectFixture() {
        TaczBinaryProbe.ProbeResult result =
                inspectFixture(FixtureKind.CORRECT);
        assertTrue(result.passed(), result.detail());
    }

    @Test
    void ingredientInteractionProbeRejectsWrongRenderIngredientDescriptor() {
        TaczBinaryProbe.ProbeResult result =
                inspectFixture(FixtureKind.WRONG_DESCRIPTOR);
        assertFalse(result.passed());
        assertTrue(result.detail().contains("missing method"));
    }

    @Test
    void ingredientInteractionProbeRejectsMissingRenderFakeItemInvocation() {
        TaczBinaryProbe.ProbeResult result =
                inspectFixture(FixtureKind.MISSING_INVOCATION);
        assertFalse(result.passed());
        assertTrue(result.detail().contains("bad invocation count"));
    }

    @Test
    void ingredientInteractionProbeRejectsMultipleRenderFakeItemInvocations() {
        TaczBinaryProbe.ProbeResult result =
                inspectFixture(FixtureKind.DOUBLE_INVOCATION);
        assertFalse(result.passed());
        assertTrue(result.detail().contains("bad invocation count"));
    }

    @Test
    void ingredientInteractionProbeRejectsMissingRenderIngredient() {
        TaczBinaryProbe.ProbeResult result =
                inspectFixture(FixtureKind.MISSING_RENDER_INGREDIENT);
        assertFalse(result.passed());
        assertTrue(result.detail().contains("missing method"));
    }

    @Test
    void ingredientInteractionProbeRejectsMissingRender() {
        TaczBinaryProbe.ProbeResult result =
                inspectFixture(FixtureKind.MISSING_RENDER);
        assertFalse(result.passed());
        assertTrue(result.detail().contains("missing method"));
    }

    @Test
    void ingredientInteractionProbeRejectsWrongRenderDescriptor() {
        TaczBinaryProbe.ProbeResult result =
                inspectFixture(FixtureKind.WRONG_RENDER_DESCRIPTOR);
        assertFalse(result.passed());
        assertTrue(result.detail().contains("missing method"));
    }

    private static TaczBinaryProbe.ProbeResult inspectFixture(
            FixtureKind kind
    ) {
        return TaczBinaryProbe.inspect(
                TaczContractRegistry.contractFor(
                        TaczFeature.GUNSMITH_INGREDIENT_INTERACTION
                ),
                className -> fixtureBytes(kind)
        );
    }

    private static byte[] fixtureBytes(FixtureKind kind) {
        ClassWriter writer = new ClassWriter(0);
        writer.visit(
                Opcodes.V17,
                Opcodes.ACC_PUBLIC | Opcodes.ACC_SUPER,
                "com/example/GunSmithTableScreen",
                null,
                "java/lang/Object",
                null
        );

        if (kind != FixtureKind.MISSING_RENDER) {
            writer.visitMethod(
                    Opcodes.ACC_PUBLIC,
                    "render",
                    kind == FixtureKind.WRONG_RENDER_DESCRIPTOR
                            ? "(Lnet/minecraft/client/gui/GuiGraphics;II)V"
                            : "(Lnet/minecraft/client/gui/GuiGraphics;IIF)V",
                    null,
                    null
            ).visitEnd();
        }

        if (kind != FixtureKind.MISSING_RENDER_INGREDIENT) {
            MethodVisitor ingredient = writer.visitMethod(
                    Opcodes.ACC_PRIVATE,
                    "renderIngredient",
                    kind == FixtureKind.WRONG_DESCRIPTOR
                            ? "(Lnet/minecraft/world/item/ItemStack;)V"
                            : "(Lnet/minecraft/client/gui/GuiGraphics;)V",
                    null,
                    null
            );
            ingredient.visitCode();
            if (kind == FixtureKind.CORRECT
                    || kind == FixtureKind.DOUBLE_INVOCATION) {
                emitRenderFakeItemCall(ingredient);
            }
            if (kind == FixtureKind.DOUBLE_INVOCATION) {
                emitRenderFakeItemCall(ingredient);
            }
            ingredient.visitInsn(Opcodes.RETURN);
            ingredient.visitMaxs(0, 0);
            ingredient.visitEnd();
        }
        writer.visitEnd();
        return writer.toByteArray();
    }

    private static void emitRenderFakeItemCall(MethodVisitor visitor) {
        visitor.visitVarInsn(Opcodes.ALOAD, 1);
        visitor.visitVarInsn(Opcodes.ALOAD, 2);
        visitor.visitVarInsn(Opcodes.ILOAD, 3);
        visitor.visitVarInsn(Opcodes.ILOAD, 4);
        visitor.visitMethodInsn(
                Opcodes.INVOKEVIRTUAL,
                "net/minecraft/client/gui/GuiGraphics",
                "renderFakeItem",
                "(Lnet/minecraft/world/item/ItemStack;II)V",
                false
        );
    }

    private enum FixtureKind {
        CORRECT,
        WRONG_DESCRIPTOR,
        MISSING_INVOCATION,
        DOUBLE_INVOCATION,
        MISSING_RENDER_INGREDIENT,
        MISSING_RENDER,
        WRONG_RENDER_DESCRIPTOR
    }
}
