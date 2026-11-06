package com.mafuyu404.taczaddon.compat.beyond;

import com.mafuyu404.taczaddon.compat.api.ApiShapeProbe;

import com.mafuyu404.taczaddon.bootstrap.TaczAddonMixinPlugin;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.tree.ClassNode;

import static org.junit.jupiter.api.Assertions.*;

class BeyondAmmoCacheContractTest {
    private final ApiShapeProbe.ClassBytes upstream = ApiShapeProbe.sourceFor(getClass());

    @Test
    void pinnedBeyondJarHasRequiredCacheAndClientQueryContract() {
        assertTrue(BeyondAmmoCacheContract.supports(upstream));
        assertTrue(new TaczAddonMixinPlugin().shouldApplyMixin(BeyondAmmoCacheContract.CACHE,
                "com.mafuyu404.taczaddon.mixin.beyond.TaczAmmoCacheMixin"));
    }

    @Test
    void absentIntegrationDoesNotEnablePatch() {
        assertFalse(BeyondAmmoCacheContract.supports(name -> null));
    }

    @Test
    void changedCacheImplementationIsNotPatched() {
        ClassNode node = new ClassNode();
        new ClassReader(upstream.read(BeyondAmmoCacheContract.CACHE)).accept(node, 0);
        node.methods.removeIf(method -> method.name.equals("applyPending"));
        ClassWriter writer = new ClassWriter(0);
        node.accept(writer);
        assertFalse(BeyondAmmoCacheContract.supports(name ->
                name.equals(BeyondAmmoCacheContract.CACHE) ? writer.toByteArray() : upstream.read(name)));
    }
}
