package com.mafuyu404.taczaddon.compat;

import com.mafuyu404.taczaddon.compat.api.ApiShapeProbe;
import com.mafuyu404.taczaddon.compat.camera.PerspectiveIntegrationProfile;
import com.mafuyu404.taczaddon.compat.sophisticated.SophisticatedBackpackGeneration;

import com.mafuyu404.taczaddon.testutil.SyntheticOptionalApis;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Runtime profile tests for the optional integrations.
 *
 * <p>Every scenario gets its own class loader, so the facade statics
 * (generation cache, linkage circuit breaker) behave exactly as they do at
 * runtime. Optional API generations are synthesised at the byte level, which
 * is also how production detects them: no optional class has to be loadable
 * for detection to run.
 */
class OptionalCompatibilityProfileTest {
    private static final String PACKAGE =
            "com.mafuyu404.taczaddon.compat.";
    private static final String MOD_LIST =
            "net.minecraftforge.fml.ModList";
    private static final String SSR = SyntheticOptionalApis.SSR;
    private static final String PERSPECTIVE_API =
            SyntheticOptionalApis.PERSPECTIVE_API;
    /**
     * Every optional mod root this fixture controls. A scenario's generated
     * map is the complete installed optional surface: anything in these roots
     * that the scenario did not synthesize reads as absent, including optional
     * mods that happen to be on the test runtime classpath.
     */
    private static final List<String> OPTIONAL_ROOTS = List.of(
            SSR,
            PERSPECTIVE_API,
            "com.github.leawind.thirdperson.",
            "io.github.leawind.thirdperson.",
            "net.p3pp3rf1y.sophisticatedbackpacks.",
            "net.p3pp3rf1y.sophisticatedcore.",
            "top.theillusivec4.curios.",
            "mezz.jei."
    );

    // ---------------------------------------------------------------- SSR

    @Test
    void shoulderSurfingAbsentNeverInterceptsTaCZNativePath()
            throws Exception {
        Fixture fixture = new Fixture(
                Set.of(),
                Map.of(),
                Fixture.BACKEND_FORBIDDEN
        );
        Class<?> facade = fixture.facade("ShoulderSurfing5Compat");

        assertEquals(
                "ABSENT",
                invoke(facade, "generation").toString()
        );
        assertEquals(
                "DELEGATE_TAZC_LEGACY",
                invoke(facade, "dispatch").toString()
        );
        assertNull(invoke(facade, "crosshairOverride"));
        assertTrue(mixinWouldEnterTaczLegacy(facade));
        assertFalse((Boolean) invoke(
                facade,
                "showCrosshairWhenShoulderSurfing"
        ));
        assertFalse(fixture.linkageBroken(facade));
        assertEquals(0, fixture.backendLoads());
    }

    /**
     * Regression guard for the original bug: a legacy 4.x installation used to
     * look like "SSR present", which cancelled TaCZ's own legacy crosshair
     * implementation and then failed to link the 5.x backend.
     */
    @Test
    void shoulderSurfingLegacyGenerationIsNotIntercepted()
            throws Exception {
        Fixture fixture = new Fixture(
                Set.of("shouldersurfing"),
                SyntheticOptionalApis.shoulderSurfingLegacy(),
                Fixture.BACKEND_FORBIDDEN
        );
        Class<?> facade = fixture.facade("ShoulderSurfing5Compat");

        assertEquals("LEGACY", invoke(facade, "generation").toString());
        assertFalse((Boolean) invoke(facade, "isSupported"));
        assertEquals(
                "DELEGATE_TAZC_LEGACY",
                invoke(facade, "dispatch").toString()
        );
        assertNull(invoke(facade, "crosshairOverride"));
        assertTrue(mixinWouldEnterTaczLegacy(facade));
        assertFalse((Boolean) invoke(
                facade,
                "showCrosshairWhenShoulderSurfing"
        ));
        /*
         * TaCZ's native path stays untouched and the circuit breaker was never
         * tripped, because the 5.x backend was never invoked.
         */
        assertFalse(fixture.linkageBroken(facade));
        assertEquals(0, fixture.backendLoads());
    }

    @Test
    void shoulderSurfingApiV5GenerationSelectsTheAddonBackend()
            throws Exception {
        Fixture fixture = new Fixture(
                Set.of("shouldersurfing"),
                SyntheticOptionalApis.shoulderSurfingApiV5(),
                Fixture.BACKEND_OK
        );
        Class<?> facade = fixture.facade("ShoulderSurfing5Compat");

        assertEquals("API_V5", invoke(facade, "generation").toString());
        assertTrue((Boolean) invoke(facade, "isSupported"));
        assertEquals(
                "USE_ADDON_V5",
                invoke(facade, "dispatch").toString()
        );
        assertEquals(Boolean.TRUE, invoke(facade, "crosshairOverride"));
        assertFalse(mixinWouldEnterTaczLegacy(facade));
        assertFalse(fixture.linkageBroken(facade));
    }

    @Test
    void shoulderSurfingBrokenBackendNeverFallsBackToTaCZLegacy()
            throws Exception {
        Fixture fixture = new Fixture(
                Set.of("shouldersurfing"),
                SyntheticOptionalApis.shoulderSurfingApiV5(),
                Fixture.BACKEND_FAILS
        );
        Class<?> facade = fixture.facade("ShoulderSurfing5Compat");

        assertEquals(
                "USE_ADDON_V5",
                invoke(facade, "dispatch").toString()
        );
        assertFalse((Boolean) invoke(facade, "isShoulderSurfing"));
        assertTrue(fixture.linkageBroken(facade));
        /*
         * A verified 5.x installation whose backend broke must stay an addon
         * failure: the addon keeps answering (false) and never loads TaCZ's
         * legacy backend, whose classes do not exist in that generation.
         */
        assertEquals(
                "BLOCK_UNSUPPORTED",
                invoke(facade, "dispatch").toString()
        );
        assertEquals(Boolean.FALSE, invoke(facade, "crosshairOverride"));
        assertFalse(
                mixinWouldEnterTaczLegacy(facade),
                "a broken 5.x backend must not fall back to TaCZ legacy"
        );
    }

    /**
     * Regression: TaCZ's own legacy backend links obsolete SSR classes
     * directly. An unknown generation must therefore be answered by the addon
     * instead of being handed back to TaCZ.
     */
    @Test
    void unknownShoulderSurfingAbiDoesNotFallBackToTaczLegacyBackend()
            throws Exception {
        Map<String, byte[]> partial = Map.of(
                /*
                 * New-API perspective without the service interface: partly
                 * new, partly legacy, therefore unknown.
                 */
                SyntheticOptionalApis.SSR_NEW_PERSPECTIVE,
                SyntheticOptionalApis.apiClass(
                        SyntheticOptionalApis.SSR_NEW_PERSPECTIVE,
                        List.of(),
                        List.of(SyntheticOptionalApis.field(
                                "FIRST_PERSON",
                                SyntheticOptionalApis.descriptor(
                                        SyntheticOptionalApis
                                                .SSR_NEW_PERSPECTIVE
                                )
                        ))
                )
        );
        Fixture fixture = new Fixture(
                Set.of("shouldersurfing"),
                partial,
                Fixture.BACKEND_FORBIDDEN
        );
        Class<?> facade = fixture.facade("ShoulderSurfing5Compat");

        assertEquals("UNKNOWN", invoke(facade, "generation").toString());
        assertEquals(
                "BLOCK_UNSUPPORTED",
                invoke(facade, "dispatch").toString()
        );
        assertFalse((Boolean) invoke(facade, "usesAddonBackend"));
        /*
         * The Mixin consumes this policy, so an unknown generation is answered
         * false by the addon and TaCZ's legacy backend - which would fail to
         * link - is never entered.
         */
        assertEquals(Boolean.FALSE, invoke(facade, "crosshairOverride"));
        assertFalse(
                mixinWouldEnterTaczLegacy(facade),
                "unknown SSR ABI must not fall back to TaCZ's legacy backend"
        );
        assertFalse(fixture.linkageBroken(facade));
        assertEquals(0, fixture.backendLoads());
    }

    /**
     * Mirrors the injection: a {@code null} override means TaCZ's own
     * compatibility implementation runs; anything else is the addon's answer.
     */
    private static boolean mixinWouldEnterTaczLegacy(Class<?> facade)
            throws Exception {
        return invoke(facade, "crosshairOverride") == null;
    }

    // ---------------------------------------------------------- Perspective

    @Test
    void leawindTwoIsCoexistOnly() throws Exception {
        Fixture fixture = new Fixture(
                Set.of("leawind_third_person"),
                SyntheticOptionalApis.leawindTwoOnly(),
                Fixture.BACKEND_FORBIDDEN
        );
        Class<?> facade = fixture.facade("PerspectiveApiCompat");

        assertEquals(
                "LEAWIND_2_UNSUPPORTED",
                invoke(facade, "profile").toString()
        );
        assertFalse((Boolean) invoke(facade, "isSupported"));
        assertNull(invoke(facade, "currentNonVanillaPerspectiveId"));
        assertNull(invoke(facade, "requestFirstPerson"));
        assertFalse(fixture.linkageBroken(facade));
        assertEquals(0, fixture.backendLoads());
    }

    /**
     * Regression: Perspective API can be installed independently of Leawind.
     * Leawind 2.x must still be classified as the unsupported 2.x generation.
     */
    @Test
    void leawindTwoWithPerspectiveApiIsNotMisclassifiedAsLeawindThree()
            throws Exception {
        Fixture fixture = new Fixture(
                Set.of("leawind_third_person", "perspective_api"),
                SyntheticOptionalApis.leawindTwoWithPerspectiveApi(),
                Fixture.BACKEND_FORBIDDEN
        );
        Class<?> facade = fixture.facade("PerspectiveApiCompat");

        assertEquals(
                "LEAWIND_2_UNSUPPORTED",
                invoke(facade, "profile").toString()
        );
        assertFalse((Boolean) invoke(facade, "isSupported"));
        assertNull(invoke(facade, "currentNonVanillaPerspectiveId"));
        assertFalse(fixture.linkageBroken(facade));
        assertEquals(0, fixture.backendLoads());
    }

    @Test
    void leawindThreeWithoutUsablePerspectiveApiIsSafe() throws Exception {
        Fixture fixture = new Fixture(
                Set.of("leawind_third_person", "perspective_api"),
                SyntheticOptionalApis.leawindThreeWithIncompatiblePerspectiveApi(),
                Fixture.BACKEND_FORBIDDEN
        );
        Class<?> facade = fixture.facade("PerspectiveApiCompat");

        assertEquals(
                "LEAWIND_3_MISSING_PERSPECTIVE_API",
                invoke(facade, "profile").toString()
        );
        assertFalse((Boolean) invoke(facade, "isSupported"));
        assertNull(invoke(facade, "currentNonVanillaPerspectiveId"));
        assertNull(invoke(facade, "requestFirstPerson"));
        assertFalse(fixture.linkageBroken(facade));
        assertEquals(0, fixture.backendLoads());
    }

    @Test
    void unknownLeawindGenerationIsUnsupported() throws Exception {
        Fixture fixture = new Fixture(
                Set.of("leawind_third_person", "perspective_api"),
                SyntheticOptionalApis.perspectiveApiGeneration(),
                Fixture.BACKEND_FORBIDDEN
        );
        Class<?> facade = fixture.facade("PerspectiveApiCompat");

        assertEquals("UNKNOWN", invoke(facade, "profile").toString());
        assertFalse((Boolean) invoke(facade, "isSupported"));
        assertNull(invoke(facade, "currentNonVanillaPerspectiveId"));
        assertFalse(fixture.linkageBroken(facade));
    }

    @Test
    void verifiedPerspectiveApiGenerationIsUsable() throws Exception {
        Fixture fixture = new Fixture(
                Set.of("leawind_third_person", "perspective_api"),
                SyntheticOptionalApis.leawindThreeWithVerifiedPerspectiveApi(),
                Fixture.BACKEND_OK
        );
        Class<?> facade = fixture.facade("PerspectiveApiCompat");

        assertEquals(
                "LEAWIND_3_PERSPECTIVE_API_SUPPORTED",
                invoke(facade, "profile").toString()
        );
        assertTrue((Boolean) invoke(facade, "isSupported"));
        assertEquals(
                "leawind.third_person",
                invoke(facade, "currentNonVanillaPerspectiveId")
        );
    }

    @Test
    void perspectiveApiLinkageFailureDisablesOnlyThatIntegration()
            throws Exception {
        Fixture fixture = new Fixture(
                Set.of("leawind_third_person", "perspective_api"),
                SyntheticOptionalApis.leawindThreeWithVerifiedPerspectiveApi(),
                Fixture.BACKEND_FAILS
        );
        Class<?> facade = fixture.facade("PerspectiveApiCompat");

        assertNull(invoke(facade, "currentNonVanillaPerspectiveId"));
        assertTrue(fixture.linkageBroken(facade));
        /*
         * The verified generation is unchanged; only the backend is latched
         * off for this session.
         */
        assertEquals(
                "LEAWIND_3_PERSPECTIVE_API_SUPPORTED",
                invoke(facade, "profile").toString()
        );
        for (String sibling : List.of(
                "ShoulderSurfing5Compat",
                "SophisticatedBackpacksCompat",
                "SophisticatedStorageClientCompat",
                "CuriosCompat",
                "BeyondIntegrationCompat"
        )) {
            assertFalse(
                    fixture.linkageBroken(fixture.facade(sibling)),
                    sibling + " must stay independent"
            );
        }
    }

    /**
     * The pinned development baseline (Perspective API 1.5.0-beta) must match
     * the contract the detection uses, so the contract is derived from a real
     * artifact and not from an assumption.
     */
    @Test
    void developmentPerspectiveApiMatchesTheVerifiedContract() {
        assertEquals(
                PerspectiveIntegrationProfile
                        .LEAWIND_3_PERSPECTIVE_API_SUPPORTED,
                PerspectiveIntegrationProfile.detect(
                        true,
                        true,
                        ApiShapeProbe.loaderSource(
                                OptionalCompatibilityProfileTest.class
                                        .getClassLoader()
                        )
                ),
                "dev baseline Perspective API must satisfy the verified contract and Leawind 3.x is the dev baseline"
        );
    }

    /**
     * Shoulder Surfing is compile-only on the development classpath, so the
     * real 4.1.5 legacy jar, the pinned 5.0.10 v5 jar, and the current 5.1.1
     * v5 jar are verified separately by {@code UpstreamAbiFixtureTest}.
     *
     * <p>The Sophisticated development baseline must report the verified
     * linked-storage generation.
     */
    @Test
    void developmentSophisticatedBaselineIsLinkedStorageGeneration() {
        ApiShapeProbe.ClassBytes source = ApiShapeProbe.loaderSource(
                OptionalCompatibilityProfileTest.class.getClassLoader()
        );
        assertEquals(
                SophisticatedBackpackGeneration.LINKED_STORAGE,
                SophisticatedBackpackGeneration.detect(source),
                "dev baseline Sophisticated Core must be the verified 3.26 linked-storage generation"
        );
    }

    // ------------------------------------------------------------- helpers

    private static Object invoke(
            Class<?> facade,
            String methodName
    ) throws Exception {
        Method method = facade.getDeclaredMethod(methodName);
        method.setAccessible(true);
        return method.invoke(null);
    }

    /**
     * Class loader that replays the real facade classes with a stubbed Forge
     * ModList and synthesised optional API generations.
     */
    private static final class Fixture extends ClassLoader {
        /** Optional backend may be loaded and answers normally. */
        static final int BACKEND_OK = 0;
        /** Optional backend may be loaded and fails with a LinkageError. */
        static final int BACKEND_FAILS = 1;
        /** Scenario must never load an optional backend at all. */
        static final int BACKEND_FORBIDDEN = 2;

        private final Set<String> installed;
        private final Map<String, byte[]> generated;
        private final boolean backendFails;
        private final boolean backendForbidden;
        private int backendLoads;

        Fixture(
                Set<String> installed,
                Map<String, byte[]> generated,
                int mode
        ) {
            super(OptionalCompatibilityProfileTest.class.getClassLoader());
            this.installed = installed;
            this.generated = new LinkedHashMap<>(generated);
            this.backendFails = mode == BACKEND_FAILS;
            this.backendForbidden = mode == BACKEND_FORBIDDEN;
        }

        int backendLoads() {
            return this.backendLoads;
        }

        Class<?> facade(String simpleName)
                throws ClassNotFoundException {
            return Class.forName(
                    com.mafuyu404.taczaddon.testutil.CompatibilityClassNames.compatClassName(simpleName),
                    true,
                    this
            );
        }

        boolean linkageBroken(Class<?> facade) throws Exception {
            Field latch = facade.getDeclaredField("linkageBroken");
            latch.setAccessible(true);
            return latch.getBoolean(null);
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve)
                throws ClassNotFoundException {
            if (name.equals(MOD_LIST)) {
                return define(name, modListStub(), resolve);
            }
            if (this.generated.containsKey(name)) {
                return define(name, this.generated.get(name), resolve);
            }
            if (isOptionalRoot(name)) {
                throw new ClassNotFoundException(name);
            }
            if (name.startsWith(PACKAGE)) {
                return loadAddonClass(name, resolve);
            }
            return super.loadClass(name, resolve);
        }

        /**
         * The production probe reads optional classes as bytes, so synthesised
         * generations must be visible to resource lookups too. Optional
         * packages that are not part of the scenario read as absent, exactly
         * like {@link #loadClass(String, boolean)}.
         */
        @Override
        public InputStream getResourceAsStream(String name) {
            if (name != null && name.endsWith(".class")) {
                String binaryName = name
                        .substring(
                                0,
                                name.length() - ".class".length()
                        )
                        .replace('/', '.');
                byte[] bytes = this.generated.get(binaryName);
                if (bytes != null) {
                    return new ByteArrayInputStream(bytes);
                }
                if (isOptionalRoot(binaryName)) {
                    return null;
                }
            }
            return super.getResourceAsStream(name);
        }

        private static boolean isOptionalRoot(String binaryName) {
            return binaryName != null
                    && OPTIONAL_ROOTS.stream()
                    .anyMatch(binaryName::startsWith);
        }

        private Class<?> loadAddonClass(
                String name,
                boolean resolve
        ) throws ClassNotFoundException {
            synchronized (getClassLoadingLock(name)) {
                Class<?> loaded = findLoadedClass(name);
                if (loaded != null) {
                    if (resolve) {
                        resolveClass(loaded);
                    }
                    return loaded;
                }
                if (name.endsWith("Inner")) {
                    this.backendLoads++;
                    if (this.backendForbidden) {
                        fail("backend " + name
                                + " must not be loaded in this scenario");
                    }
                    return define(
                            name,
                            backendStub(name),
                            resolve
                    );
                }
                try (InputStream input = getParent()
                        .getResourceAsStream(
                                name.replace('.', '/') + ".class"
                        )) {
                    if (input == null) {
                        throw new ClassNotFoundException(name);
                    }
                    return define(name, input.readAllBytes(), resolve);
                } catch (IOException error) {
                    throw new ClassNotFoundException(name, error);
                }
            }
        }

        private Class<?> define(
                String name,
                byte[] bytes,
                boolean resolve
        ) {
            synchronized (getClassLoadingLock(name)) {
                Class<?> loaded = findLoadedClass(name);
                if (loaded == null) {
                    loaded = defineClass(name, bytes, 0, bytes.length);
                }
                if (resolve) {
                    resolveClass(loaded);
                }
                return loaded;
            }
        }

        private byte[] modListStub() {
            ClassWriter writer = new ClassWriter(
                    ClassWriter.COMPUTE_FRAMES
                            | ClassWriter.COMPUTE_MAXS
            );
            String internal = MOD_LIST.replace('.', '/');
            writer.visit(
                    Opcodes.V17,
                    Opcodes.ACC_PUBLIC,
                    internal,
                    null,
                    "java/lang/Object",
                    null
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

            MethodVisitor get = writer.visitMethod(
                    Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                    "get",
                    "()L" + internal + ";",
                    null,
                    null
            );
            get.visitCode();
            get.visitTypeInsn(Opcodes.NEW, internal);
            get.visitInsn(Opcodes.DUP);
            get.visitMethodInsn(
                    Opcodes.INVOKESPECIAL,
                    internal,
                    "<init>",
                    "()V",
                    false
            );
            get.visitInsn(Opcodes.ARETURN);
            get.visitMaxs(0, 0);
            get.visitEnd();

            MethodVisitor isLoaded = writer.visitMethod(
                    Opcodes.ACC_PUBLIC,
                    "isLoaded",
                    "(Ljava/lang/String;)Z",
                    null,
                    null
            );
            isLoaded.visitCode();
            for (String modId
                    : this.installed.stream().sorted().toList()) {
                Label next = new Label();
                isLoaded.visitVarInsn(Opcodes.ALOAD, 1);
                isLoaded.visitLdcInsn(modId);
                isLoaded.visitMethodInsn(
                        Opcodes.INVOKEVIRTUAL,
                        "java/lang/String",
                        "equals",
                        "(Ljava/lang/Object;)Z",
                        false
                );
                isLoaded.visitJumpInsn(Opcodes.IFEQ, next);
                isLoaded.visitInsn(Opcodes.ICONST_1);
                isLoaded.visitInsn(Opcodes.IRETURN);
                isLoaded.visitLabel(next);
            }
            isLoaded.visitInsn(Opcodes.ICONST_0);
            isLoaded.visitInsn(Opcodes.IRETURN);
            isLoaded.visitMaxs(0, 0);
            isLoaded.visitEnd();

            writer.visitEnd();
            return writer.toByteArray();
        }

        /**
         * Minimal stand-in for an optional backend that answers the entry
         * points the facades use, or fails each of them with a
         * {@link NoSuchMethodError} when the scenario wants a linkage break.
         */
        private byte[] backendStub(String name) {
            ClassWriter writer = new ClassWriter(
                    ClassWriter.COMPUTE_MAXS
            );
            String internal = name.replace('.', '/');
            writer.visit(
                    Opcodes.V17,
                    Opcodes.ACC_PUBLIC,
                    internal,
                    null,
                    "java/lang/Object",
                    null
            );
            if (name.endsWith("PerspectiveApiCompatInner")) {
                emit(
                        writer,
                        "currentPerspectiveId",
                        "()Ljava/lang/String;",
                        "leawind.third_person",
                        false
                );
                emit(writer, "isFirstPersonActive", "()Z", null, false);
                emit(
                        writer,
                        "requestFirstPerson",
                        "()Lcom/mafuyu404/taczaddon/compat/camera/PerspectiveApiHandle;",
                        null,
                        false
                );
            } else {
                emit(writer, "isShoulderSurfing", "()Z", null, true);
                emit(writer, "isFreeLooking", "()Z", null, false);
            }
            writer.visitEnd();
            return writer.toByteArray();
        }

        private void emit(
                ClassWriter writer,
                String methodName,
                String descriptor,
                String stringValue,
                boolean booleanValue
        ) {
            MethodVisitor method = writer.visitMethod(
                    Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                    methodName,
                    descriptor,
                    null,
                    null
            );
            method.visitCode();
            if (this.backendFails) {
                method.visitTypeInsn(
                        Opcodes.NEW,
                        "java/lang/NoSuchMethodError"
                );
                method.visitInsn(Opcodes.DUP);
                method.visitLdcInsn("optional API fixture");
                method.visitMethodInsn(
                        Opcodes.INVOKESPECIAL,
                        "java/lang/NoSuchMethodError",
                        "<init>",
                        "(Ljava/lang/String;)V",
                        false
                );
                method.visitInsn(Opcodes.ATHROW);
            } else if (stringValue != null) {
                method.visitLdcInsn(stringValue);
                method.visitInsn(Opcodes.ARETURN);
            } else if (descriptor.endsWith(")Z")) {
                method.visitInsn(
                        booleanValue ? Opcodes.ICONST_1 : Opcodes.ICONST_0
                );
                method.visitInsn(Opcodes.IRETURN);
            } else {
                method.visitInsn(Opcodes.ACONST_NULL);
                method.visitInsn(Opcodes.ARETURN);
            }
            method.visitMaxs(0, 0);
            method.visitEnd();
        }
    }
}
