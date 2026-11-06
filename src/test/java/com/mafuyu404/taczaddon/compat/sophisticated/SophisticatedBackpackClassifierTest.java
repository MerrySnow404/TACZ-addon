package com.mafuyu404.taczaddon.compat.sophisticated;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unknown or renamed Sophisticated wrapper generations must never be mistaken
 * for ordinary backpacks, because the ordinary path requests contents through
 * the BackpackStorage contents-UUID protocol.
 *
 * <p>The fixtures are generated at the byte level so the classifier is tested
 * against shapes that do not exist in the development baseline; the linked
 * member types themselves come from the pinned Sophisticated Core baseline and
 * only ever appear as names.
 */
class SophisticatedBackpackClassifierTest {
    private static final String CORE = "net.p3pp3rf1y.sophisticatedcore.";
    private static final String LINKED = CORE + "linkedstorage.";
    private static final String BACKPACKS =
            "net.p3pp3rf1y.sophisticatedbackpacks.";

    private static final String KNOWN_LINKED_WRAPPER =
            BACKPACKS + "backpack.wrapper.LinkedStorageBackpackWrapper";

    @Test
    void ordinaryWrapperShapeStaysOrdinary() throws Exception {
        Object wrapper = instanceOf(
                synthesize(
                        "fixture.OrdinaryWrapperFixture",
                        List.of(),
                        List.of(new FieldSpec(
                                "handler",
                                descriptor(CORE + "inventory.InventoryHandler")
                        ))
                )
        );
        assertEquals(
                SophisticatedBackpackClassifier.Kind.ORDINARY,
                SophisticatedBackpackClassifier.classify(wrapper)
        );
    }

    @Test
    void knownGenerationWrapperClassIsLinked() throws Exception {
        Object wrapper = instanceOf(
                synthesize(KNOWN_LINKED_WRAPPER, List.of(), List.of())
        );
        assertEquals(
                SophisticatedBackpackClassifier.Kind.LINKED,
                SophisticatedBackpackClassifier.classify(wrapper)
        );
    }

    /**
     * A future generation may rename the concrete wrapper class. Any linked
     * storage type in the wrapper class's own shape still fails closed.
     */
    @Test
    void renamedLinkedGenerationIsStillLinked() throws Exception {
        Object wrapper = instanceOf(
                synthesize(
                        "fixture.RenamedLinkedWrapperFixture",
                        List.of(),
                        List.of(new FieldSpec(
                                "endpoint",
                                descriptor(LINKED
                                        + "LinkedStorageEndpointData")
                        ))
                )
        );
        assertEquals(
                SophisticatedBackpackClassifier.Kind.LINKED,
                SophisticatedBackpackClassifier.classify(wrapper)
        );
    }

    /**
     * Directly implemented linked-storage interfaces are part of the concrete
     * wrapper class's own shape, which is how a linked host subclass is
     * detected even when it extends an ordinary wrapper.
     */
    @Test
    void linkedInterfaceOnTheConcreteClassIsLinked() throws Exception {
        Object wrapper = instanceOf(
                synthesize(
                        "fixture.LinkedHostWrapperFixture",
                        List.of(LINKED
                                + "ILinkedStorageEndpointProvider"),
                        List.of()
                )
        );
        assertEquals(
                SophisticatedBackpackClassifier.Kind.LINKED,
                SophisticatedBackpackClassifier.classify(wrapper)
        );
    }

    @Test
    void absentWrapperFailsClosed() {
        assertEquals(
                SophisticatedBackpackClassifier.Kind.UNCLASSIFIED,
                SophisticatedBackpackClassifier.classify(null)
        );
    }

    // ------------------------------------------------- mutation policy

    @Test
    void ordinaryBackpackRemainsMutable() {
        SophisticatedBackpackClassifier.Access access = access(
                kind(
                        "fixture.OrdinaryMutationPolicyFixture",
                        List.of(),
                        List.of(new FieldSpec(
                                "handler",
                                descriptor(CORE + "inventory.InventoryHandler")
                        ))
                ),
                notLinked(),
                SophisticatedBackpackGeneration.ORDINARY_ONLY
        );
        assertEquals(
                SophisticatedBackpackClassifier.Access.ORDINARY,
                access
        );
        assertTrue(access.mutationAllowed());
        assertTrue(access.ordinaryContents());
    }

    /**
     * A known healthy 3.26 linked backpack (host wrapper) must still take part
     * in ammo mutation: its inventory handler proxies to the canonical linked
     * contents, which is exactly what Sophisticated itself mutates.
     */
    @Test
    void knownLinkedBackpackRemainsMutable() {
        SophisticatedBackpackClassifier.Access access = access(
                kind(
                        "fixture.LinkedMutationPolicyFixture",
                        List.of(),
                        List.of(new FieldSpec(
                                "endpoint",
                                descriptor(LINKED
                                        + "LinkedStorageEndpointData")
                        ))
                ),
                notLinked(),
                SophisticatedBackpackGeneration.LINKED_STORAGE
        );
        assertEquals(
                SophisticatedBackpackClassifier.Access.KNOWN_LINKED,
                access
        );
        assertTrue(
                access.mutationAllowed(),
                "known linked backpacks must stay consumable"
        );
        assertTrue(access.linkedContents());
    }

    @Test
    void knownLinkedEndpointRemainsMutable() {
        SophisticatedBackpackClassifier.Access access = access(
                SophisticatedBackpackClassifier.Kind.LINKED,
                EndpointResolution.linked(
                        java.util.UUID.randomUUID()
                ),
                SophisticatedBackpackGeneration.LINKED_STORAGE
        );
        assertEquals(
                SophisticatedBackpackClassifier.Access.KNOWN_LINKED,
                access
        );
        assertTrue(access.mutationAllowed());
    }

    @Test
    void malformedLinkedEndpointCannotBeMutated() {
        SophisticatedBackpackClassifier.Access access = access(
                SophisticatedBackpackClassifier.Kind.LINKED,
                EndpointResolution
                        .malformedLinked(),
                SophisticatedBackpackGeneration.LINKED_STORAGE
        );
        assertEquals(
                SophisticatedBackpackClassifier.Access.MALFORMED_LINKED,
                access
        );
        assertFalse(access.mutationAllowed());
        assertFalse(access.readable());
    }

    @Test
    void unknownLinkedGenerationCannotBeMutated() {
        SophisticatedBackpackClassifier.Access access = access(
                SophisticatedBackpackClassifier.Kind.LINKED,
                EndpointResolution
                        .unavailableBridge(),
                SophisticatedBackpackGeneration.UNKNOWN
        );
        assertEquals(
                SophisticatedBackpackClassifier.Access.UNSUPPORTED_GENERATION,
                access
        );
        assertFalse(access.mutationAllowed());
    }

    @Test
    void brokenLinkedBackendCannotBeMutated() {
        SophisticatedBackpackClassifier.Access access = access(
                SophisticatedBackpackClassifier.Kind.LINKED,
                EndpointResolution
                        .unavailableBridge(),
                SophisticatedBackpackGeneration.LINKED_STORAGE
        );
        assertEquals(
                SophisticatedBackpackClassifier.Access.LINKAGE_BROKEN,
                access
        );
        assertFalse(access.mutationAllowed());
        assertFalse(access.readable());
    }

    @Test
    void unclassifiedWrapperCannotBeMutated() {
        SophisticatedBackpackClassifier.Access access = access(
                SophisticatedBackpackClassifier.Kind.UNCLASSIFIED,
                notLinked(),
                SophisticatedBackpackGeneration.LINKED_STORAGE
        );
        assertEquals(
                SophisticatedBackpackClassifier.Access.UNKNOWN,
                access
        );
        assertFalse(access.mutationAllowed());
    }

    @Test
    void unclassifiedLinkedEndpointFailsClosed() {
        SophisticatedBackpackClassifier.Access access = access(
                SophisticatedBackpackClassifier.Kind.UNCLASSIFIED,
                EndpointResolution.linked(
                        java.util.UUID.randomUUID()
                ),
                SophisticatedBackpackGeneration.LINKED_STORAGE
        );

        assertEquals(
                SophisticatedBackpackClassifier.Access.UNKNOWN,
                access,
                "a verified linked endpoint does not prove that an unclassified wrapper has verified mutation semantics"
        );
        assertFalse(access.readable());
        assertFalse(access.mutationAllowed());
        assertFalse(access.ordinaryContents());
        assertFalse(access.linkedContents());
    }

    @Test
    void unclassifiedWrapperWithBrokenLinkedBridgeFailsClosed() {
        SophisticatedBackpackClassifier.Access access = access(
                SophisticatedBackpackClassifier.Kind.UNCLASSIFIED,
                EndpointResolution
                        .unavailableBridge(),
                SophisticatedBackpackGeneration.LINKED_STORAGE
        );

        assertEquals(
                SophisticatedBackpackClassifier.Access.UNKNOWN,
                access
        );
        assertFalse(access.readable());
        assertFalse(access.mutationAllowed());
    }

    /**
     * A linked-shaped wrapper on a generation without linked storage is not a
     * known state and must not be treated as an ordinary backpack.
     */
    @Test
    void ordinaryOnlyGenerationCannotTreatLinkedWrapperAsOrdinary() {
        SophisticatedBackpackClassifier.Access access = access(
                SophisticatedBackpackClassifier.Kind.LINKED,
                notLinked(),
                SophisticatedBackpackGeneration.ORDINARY_ONLY
        );
        assertEquals(
                SophisticatedBackpackClassifier.Access.UNKNOWN,
                access
        );
        assertFalse(access.ordinaryContents());
        assertFalse(access.mutationAllowed());
    }

    private static SophisticatedBackpackClassifier.Access access(
            SophisticatedBackpackClassifier.Kind kind,
            EndpointResolution endpoint,
            SophisticatedBackpackGeneration generation
    ) {
        return SophisticatedBackpackClassifier.resolveAccess(
                kind,
                endpoint,
                generation
        );
    }

    private static EndpointResolution
    notLinked() {
        return EndpointResolution
                .notLinked();
    }

    private static SophisticatedBackpackClassifier.Kind kind(
            String binaryName,
            List<String> interfaces,
            List<FieldSpec> fields
    ) {
        try {
            return SophisticatedBackpackClassifier.classify(
                    instanceOf(synthesize(binaryName, interfaces, fields))
            );
        } catch (Exception error) {
            throw new IllegalStateException(error);
        }
    }

    @Test
    void unknownLinkedGenerationIsDetectedAsUnknown() {
        /*
         * Linked-storage classes exist, but their shape does not match the
         * verified 3.26 contract: present, yet not callable with confidence.
         */
        Map<String, byte[]> partial = Map.of(
                LINKED + "LinkedStorageStackLifecycle",
                synthesize(
                        LINKED + "LinkedStorageStackLifecycle",
                        List.of(),
                        List.of()
                )
        );
        assertEquals(
                SophisticatedBackpackGeneration.UNKNOWN,
                SophisticatedBackpackGeneration.detect(partial::get)
        );
    }

    @Test
    void absentSophisticatedApiIsAbsent() {
        assertEquals(
                SophisticatedBackpackGeneration.ABSENT,
                SophisticatedBackpackGeneration.detect(name -> null)
        );
    }

    @Test
    void twoClassesWithoutDescriptorsAreUnknownNotAbsent() {
        /*
         * Generation detection must require the real ordinary ABI shape, not
         * merely the presence of two well-known class names. A partial ABI is
         * an unknown Sophisticated installation, never "absent".
         */
        Map<String, byte[]> ordinary = Map.of(
                BACKPACKS + "backpack.wrapper.IBackpackWrapper",
                synthesize(
                        BACKPACKS + "backpack.wrapper.IBackpackWrapper",
                        List.of(),
                        List.of()
                ),
                CORE + "inventory.InventoryHandler",
                synthesize(
                        CORE + "inventory.InventoryHandler",
                        List.of(),
                        List.of()
                )
        );
        assertEquals(
                SophisticatedBackpackGeneration.UNKNOWN,
                SophisticatedBackpackGeneration.detect(ordinary::get)
        );
    }

    /**
     * Linked shape present but ordinary shape invalid: the linked build depends
     * on the ordinary API, so this must not be reported as LINKED_STORAGE.
     */
    @Test
    void linkedShapeWithoutOrdinaryShapeIsUnknown() {
        Map<String, byte[]> linkedOnly = Map.of(
                LINKED + "LinkedStorageStackLifecycle",
                synthesize(
                        LINKED + "LinkedStorageStackLifecycle",
                        List.of(),
                        List.of()
                ),
                LINKED + "LinkedStorageStackData",
                synthesize(
                        LINKED + "LinkedStorageStackData",
                        List.of(),
                        List.of()
                )
        );
        assertEquals(
                SophisticatedBackpackGeneration.UNKNOWN,
                SophisticatedBackpackGeneration.detect(linkedOnly::get)
        );
    }

    /**
     * Core regression for capability isolation: a broken linked backend closes
     * linked capability only. A wrapper that is independently proven ordinary
     * must keep working.
     */
    @Test
    void ordinaryBackpackSurvivesBrokenLinkedBackend() {
        SophisticatedBackpackClassifier.Access access = access(
                SophisticatedBackpackClassifier.Kind.ORDINARY,
                EndpointResolution
                        .unavailableBridge(),
                SophisticatedBackpackGeneration.LINKED_STORAGE
        );
        assertEquals(
                SophisticatedBackpackClassifier.Access.ORDINARY,
                access,
                "a broken linked bridge must not turn ordinary backpacks off"
        );
        assertTrue(access.readable());
        assertTrue(access.mutationAllowed());
        assertTrue(access.ordinaryContents());
    }

    private record FieldSpec(String name, String descriptor) {
    }

    private static String descriptor(String binaryName) {
        return "L" + binaryName.replace('.', '/') + ";";
    }

    private static byte[] synthesize(
            String binaryName,
            List<String> interfaces,
            List<FieldSpec> fields
    ) {
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(
                Opcodes.V17,
                Opcodes.ACC_PUBLIC,
                binaryName.replace('.', '/'),
                null,
                "java/lang/Object",
                interfaces.stream()
                        .map(name -> name.replace('.', '/'))
                        .toArray(String[]::new)
        );
        MethodVisitor constructor = writer.visitMethod(
                Opcodes.ACC_PUBLIC,
                "<init>",
                "()V",
                null,
                null
        );
        constructor.visitCode();
        constructor.visitVarInsn(Opcodes.ALOAD, 0);
        constructor.visitMethodInsn(
                Opcodes.INVOKESPECIAL,
                "java/lang/Object",
                "<init>",
                "()V",
                false
        );
        constructor.visitInsn(Opcodes.RETURN);
        constructor.visitMaxs(0, 0);
        constructor.visitEnd();
        for (FieldSpec field : fields) {
            writer.visitField(
                    Opcodes.ACC_PUBLIC,
                    field.name(),
                    field.descriptor(),
                    null,
                    null
            ).visitEnd();
        }
        writer.visitEnd();
        return writer.toByteArray();
    }

    private static Object instanceOf(byte[] bytes) throws Exception {
        String binaryName = new ClassReader(bytes)
                .getClassName()
                .replace('/', '.');
        ClassLoader loader = new ClassLoader(
                SophisticatedBackpackClassifierTest.class.getClassLoader()
        ) {
            @Override
            protected Class<?> loadClass(String name, boolean resolve)
                    throws ClassNotFoundException {
                if (name.equals(binaryName)) {
                    synchronized (getClassLoadingLock(name)) {
                        Class<?> loaded = findLoadedClass(name);
                        if (loaded == null) {
                            loaded = defineClass(
                                    name,
                                    bytes,
                                    0,
                                    bytes.length
                            );
                        }
                        if (resolve) {
                            resolveClass(loaded);
                        }
                        return loaded;
                    }
                }
                return super.loadClass(name, resolve);
            }
        };
        return Class.forName(binaryName, true, loader)
                .getDeclaredConstructor()
                .newInstance();
    }
}
