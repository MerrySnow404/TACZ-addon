package com.mafuyu404.taczaddon.compat;

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
    }

    @Test
    void incrementalCacheImplementationStillSatisfiesTheGate() {
        // Beyond Integration 0.8 replaced the single-network putAll snapshot with an incremental
        // structure; the gate must stay satisfied by the public query API alone.
        ClassNode node = new ClassNode();
        new ClassReader(upstream.read(BeyondAmmoCacheContract.CACHE)).accept(node, 0);
        node.methods.stream()
                .filter(method -> method.name.equals("applyPending"))
                .forEach(method -> method.instructions.clear());
        ClassWriter writer = new ClassWriter(0);
        node.accept(writer);
        assertTrue(BeyondAmmoCacheContract.supports(name ->
                name.equals(BeyondAmmoCacheContract.CACHE) ? writer.toByteArray() : upstream.read(name)));
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
