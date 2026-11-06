package com.mafuyu404.taczaddon.compat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import com.mafuyu404.taczaddon.testutil.SyntheticOptionalApis;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/** Replays the real facades with installed-mod and failing API fixtures, without a game. */
public class OptionalCompatEntrypointTest {
    private static final String PACKAGE = "com.mafuyu404.taczaddon.compat.";
    private static final String PLAYER = "net.minecraft.world.entity.player.Player";
    private static final String SERVER_PLAYER = "net.minecraft.server.level.ServerPlayer";
    private static final String STACK = "net.minecraft.world.item.ItemStack";
    private static final AtomicInteger CALLS = new AtomicInteger();

    public static void failApi() {
        CALLS.incrementAndGet();
        throw new NoSuchMethodError("optional API fixture");
    }

    static Stream<Arguments> entrypoints() {
        return Stream.of(
                new String[] {"ShoulderSurfing5Compat", "isShoulderSurfing", "isFreeLooking",
                        "isFirstPersonActive", "forceFirstPerson", "enableShoulderSurfing", "showCrosshairWhenShoulderSurfing"},
                new String[] {"PerspectiveApiCompat", "currentNonVanillaPerspectiveId", "isFirstPersonActive", "requestFirstPerson"},
                new String[] {"SophisticatedBackpacksCompat", "visitInventoryBackpacks", "mutateInventoryBackpacks",
                        "syncAllBackpack", "refreshLinkedBackpackSnapshots"},
                new String[] {"SophisticatedStorageClientCompat", "isStorageScreen", "renderItemRelations"},
                new String[] {"CuriosCompat", "visitHandlers"},
                new String[] {"JeiCompat", "showRecipes"}
        ).flatMap(names -> Stream.of(names).skip(1).map(method -> Arguments.of(names[0], method)));
    }

    @ParameterizedTest(name = "{0}.{1}")
    @MethodSource("entrypoints")
    void everyApiEntryTripsAndAllSiblingEntriesStopRetrying(String simpleName, String methodName) throws Exception {
        CALLS.set(0);
        ClassLoader loader = fixtureLoader();
        Class<?> facade = Class.forName(com.mafuyu404.taczaddon.testutil.CompatibilityClassNames.compatClassName(simpleName), true, loader);
        if (simpleName.equals("JeiCompat")) facade.getMethod("init").invoke(null);
        Method entry = Stream.of(facade.getDeclaredMethods()).filter(m -> m.getName().equals(methodName)).findFirst().orElseThrow();
        if (propagatesLinkageToCaller(simpleName, methodName)) {
            assertPropagatedLinkage(entry);
        } else {
            assertNeutral(entry);
        }
        assertEquals(1, CALLS.get(), "The real entry must reach the failing API");
        var latch = facade.getDeclaredField("linkageBroken");
        latch.setAccessible(true);
        assertTrue(latch.getBoolean(null));
        for (Arguments args : entrypoints().toList()) {
            if (!args.get()[0].equals(simpleName)) continue;
            String sibling = (String) args.get()[1];
            Method method = Stream.of(facade.getDeclaredMethods()).filter(m -> m.getName().equals(sibling)).findFirst().orElseThrow();
            assertNeutral(method);
        }
        assertEquals(1, CALLS.get(), "No entry may retry a latched backend");
        for (String other : List.of("CuriosCompat", "JeiCompat", "ShoulderSurfing5Compat", "PerspectiveApiCompat",
                "SophisticatedBackpacksCompat", "SophisticatedStorageClientCompat", "BeyondIntegrationCompat")) {
            if (other.equals(simpleName)) continue;
            var otherLatch = Class.forName(com.mafuyu404.taczaddon.testutil.CompatibilityClassNames.compatClassName(other), true, loader).getDeclaredField("linkageBroken");
            otherLatch.setAccessible(true);
            assertFalse(otherLatch.getBoolean(null), other + " must remain independent");
        }
    }

    /**
     * The backpack mutation pass is the single deliberate exception to the
     * "optional facades never escape" rule.
     *
     * <p>A mutation pass may already have changed slots before the failing
     * call. Reporting "consumed nothing" by returning false would let the
     * caller ask the next ammo source for the same rounds, so the real facade
     * latches the linkage failure and rethrows it to the current operation.
     * Every sibling entry must still be neutral after the latch.
     */
    private static boolean propagatesLinkageToCaller(
            String simpleName,
            String methodName
    ) {
        return simpleName.equals("SophisticatedBackpacksCompat")
                && methodName.equals("mutateInventoryBackpacks");
    }

    private static void assertNeutral(Method method) throws Exception {
        Object[] args = argumentsFor(method);
        Object result = assertDoesNotThrow(() -> method.invoke(null, args));
        if (method.getReturnType() == boolean.class) assertEquals(false, result);
        else assertNull(result);
    }

    private static void assertPropagatedLinkage(Method method)
            throws Exception {
        Object[] args = argumentsFor(method);
        var thrown = assertThrows(
                java.lang.reflect.InvocationTargetException.class,
                () -> method.invoke(null, args)
        );
        assertInstanceOf(LinkageError.class, thrown.getCause());
    }

    private static Object[] argumentsFor(Method method) throws Exception {
        Object[] args = new Object[method.getParameterCount()];
        for (int i = 0; i < args.length; i++) {
            Class<?> type = method.getParameterTypes()[i];
            if (type == boolean.class) args[i] = true;
            else if (List.of(PLAYER, SERVER_PLAYER, STACK).contains(type.getName())) args[i] = type.getConstructor().newInstance();
        }
        return args;
    }

    private ClassLoader fixtureLoader() {
        Map<String, byte[]> generatedAnchors =
                SyntheticOptionalApis.shoulderSurfingApiV5();
        return new ClassLoader(getClass().getClassLoader()) {
            @Override
            protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                if (name.equals(OptionalCompatEntrypointTest.class.getName())) return getParent().loadClass(name);
                if (generatedAnchors.containsKey(name)) {
                    return define(
                            name,
                            generatedAnchors.get(name),
                            resolve
                    );
                }
                boolean stub = List.of(PLAYER, SERVER_PLAYER, STACK, "net.minecraftforge.fml.ModList").contains(name);
                if (!stub && !name.startsWith(PACKAGE)) return super.loadClass(name, resolve);
                synchronized (getClassLoadingLock(name)) {
                    Class<?> loaded = findLoadedClass(name);
                    if (loaded == null) {
                        try {
                            byte[] bytes = stub ? stub(name) : backendOrFacade(name);
                            loaded = defineClass(name, bytes, 0, bytes.length);
                        } catch (IOException error) {
                            throw new ClassNotFoundException(name, error);
                        }
                    }
                    if (resolve) resolveClass(loaded);
                    return loaded;
                }
            }

            /**
             * The generation probe reads optional classes as bytes, so the
             * synthesised 5.x anchors have to be visible as resources too.
             */
            @Override
            public java.io.InputStream getResourceAsStream(String name) {
                if (name != null && name.endsWith(".class")) {
                    String binaryName = name
                            .substring(0, name.length() - ".class".length())
                            .replace('/', '.');
                    byte[] bytes = generatedAnchors.get(binaryName);
                    if (bytes != null) {
                        return new ByteArrayInputStream(bytes);
                    }
                }
                return super.getResourceAsStream(name);
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
        };
    }

    private static byte[] backendOrFacade(String name) throws IOException {
        try (var input = OptionalCompatEntrypointTest.class.getClassLoader().getResourceAsStream(name.replace('.', '/') + ".class")) {
            if (input == null) throw new IOException(name);
            ClassNode node = new ClassNode();
            new ClassReader(input).accept(node, 0);
            if (name.endsWith("Inner") || name.equals("com.mafuyu404.taczaddon.compat.jei.JeiPlugin")) {
                node.interfaces.clear();
                node.fields.clear();
                node.methods.removeIf(m -> (m.access & Opcodes.ACC_STATIC) == 0 || m.name.equals("<clinit>"));
                for (MethodNode method : node.methods) {
                    method.instructions.clear();
                    method.tryCatchBlocks.clear();
                    method.localVariables = null;
                    method.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC,
                            Type.getInternalName(OptionalCompatEntrypointTest.class), "failApi", "()V", false));
                    method.instructions.add(new InsnNode(Opcodes.ACONST_NULL));
                    method.instructions.add(new InsnNode(Opcodes.ATHROW));
                }
            }
            ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
            node.accept(writer);
            return writer.toByteArray();
        }
    }

    private static byte[] stub(String name) {
        String internal = name.replace('.', '/');
        String parent = name.equals(SERVER_PLAYER) ? PLAYER.replace('.', '/') : "java/lang/Object";
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, internal, null, parent, null);
        MethodVisitor constructor = writer.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        constructor.visitCode();
        constructor.visitVarInsn(Opcodes.ALOAD, 0);
        constructor.visitMethodInsn(Opcodes.INVOKESPECIAL, parent, "<init>", "()V", false);
        constructor.visitInsn(Opcodes.RETURN);
        constructor.visitMaxs(0, 0);
        constructor.visitEnd();
        if (name.endsWith("ModList")) {
            MethodVisitor get = writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "get", "()L" + internal + ";", null, null);
            get.visitCode();
            get.visitTypeInsn(Opcodes.NEW, internal);
            get.visitInsn(Opcodes.DUP);
            get.visitMethodInsn(Opcodes.INVOKESPECIAL, internal, "<init>", "()V", false);
            get.visitInsn(Opcodes.ARETURN);
            get.visitMaxs(0, 0);
            get.visitEnd();
        }
        if (name.equals(STACK) || name.endsWith("ModList")) {
            MethodVisitor flag = writer.visitMethod(Opcodes.ACC_PUBLIC, name.equals(STACK) ? "isEmpty" : "isLoaded",
                    name.equals(STACK) ? "()Z" : "(Ljava/lang/String;)Z", null, null);
            flag.visitCode();
            flag.visitInsn(name.equals(STACK) ? Opcodes.ICONST_0 : Opcodes.ICONST_1);
            flag.visitInsn(Opcodes.IRETURN);
            flag.visitMaxs(0, 0);
            flag.visitEnd();
        }
        writer.visitEnd();
        return writer.toByteArray();
    }
}
