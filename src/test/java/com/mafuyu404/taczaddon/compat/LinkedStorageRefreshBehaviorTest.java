package com.mafuyu404.taczaddon.compat;

import com.tacz.guns.api.item.IGun;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

/** Executes production method bytecode with only network and game-world boundaries substituted. */
public class LinkedStorageRefreshBehaviorTest {
    private static final String SELF = Type.getInternalName(LinkedStorageRefreshBehaviorTest.class);
    public static int timer;
    private static int requests;
    private static boolean gunHeld;
    private static Optional<Long> revision;
    private static UUID queriedGroup;
    private static Object packet;

    public static Optional<Long> getRevision(UUID group) { queriedGroup = group; return revision; }
    public record Request(UUID groupId, long knownRevision) { }
    public static void request(UUID group, long knownRevision) { packet = new Request(group, knownRevision); }
    public static ItemStack mainHand(Player player) { return null; }
    public static IGun gun(ItemStack stack) {
        return gunHeld ? (IGun) Proxy.newProxyInstance(IGun.class.getClassLoader(), new Class<?>[]{IGun.class},
                (proxy, method, args) -> null) : null;
    }
    public static void refresh(Player player) { requests++; }

    @Test void refreshUsesInstalledRevisionAndMissingRevisionRequestsFullSnapshot() throws Exception {
        Method method = replay("com/mafuyu404/taczaddon/compat/sophisticated/SophisticatedLinkedStorageCompat326",
                "refreshSnapshot", node -> {
                    for (var insn : node.instructions.toArray()) {
                        if (insn instanceof MethodInsnNode call && call.name.equals("getRevision")) {
                            assertEquals("(Ljava/util/UUID;)Ljava/util/Optional;", call.desc);
                            call.owner = SELF;
                        } else if (insn instanceof MethodInsnNode call && call.name.equals("request")) {
                            call.owner = SELF;
                        }
                    }
                }, UUID.class);
        UUID group = UUID.randomUUID();
        for (long value : new long[]{-1, 0, 37, Long.MAX_VALUE}) {
            revision = value < 0 ? Optional.empty() : Optional.of(value);
            packet = null; queriedGroup = null;
            method.invoke(method.getDeclaringClass().getConstructor().newInstance(), group);
            assertEquals(group, queriedGroup);
            assertNotNull(packet);
            assertEquals(group, packet.getClass().getMethod("groupId").invoke(packet));
            assertEquals(value, packet.getClass().getMethod("knownRevision").invoke(packet));
        }
        packet = null;
        method.invoke(method.getDeclaringClass().getConstructor().newInstance(), new Object[]{null});
        assertNull(packet);
    }

    @Test void onlyGunHeldTicksProbeEveryFortyTicksAndNoGunResetsTimer() throws Exception {
        com.mafuyu404.taczaddon.testutil.MinecraftTestBootstrap.prepare();
        Method method = replay("com/mafuyu404/taczaddon/client/ammo/BackpackHudState",
                "taczaddon$tickLinkedBackpackRefresh", node -> {
                    for (var insn : node.instructions.toArray()) {
                        if (insn instanceof FieldInsnNode field && field.name.equals("taczaddon$linkedTicksUntilSync")) {
                            field.owner = SELF; field.name = "timer";
                        } else if (insn instanceof MethodInsnNode call && call.name.equals("getMainHandItem")) {
                            call.setOpcode(Opcodes.INVOKESTATIC); call.owner = SELF; call.name = "mainHand";
                            call.desc = "(Lnet/minecraft/world/entity/player/Player;)Lnet/minecraft/world/item/ItemStack;";
                        } else if (insn instanceof MethodInsnNode call && call.name.equals("getIGunOrNull")) {
                            call.owner = SELF; call.name = "gun"; call.itf = false;
                        } else if (insn instanceof MethodInsnNode call && call.name.equals("refreshLinkedBackpackSnapshots")) {
                            call.owner = SELF; call.name = "refresh";
                        }
                    }
                }, Player.class);
        requests = 0; timer = 19; gunHeld = false;
        for (int tick = 0; tick < 100; tick++) method.invoke(null, new Object[]{null});
        assertEquals(0, requests); assertEquals(0, timer);
        gunHeld = true;
        method.invoke(null, new Object[]{null});
        assertEquals(1, requests); assertEquals(40, timer);
        for (int tick = 0; tick < 39; tick++) method.invoke(null, new Object[]{null});
        assertEquals(1, requests);
        method.invoke(null, new Object[]{null});
        assertEquals(2, requests);
        gunHeld = false;
        method.invoke(null, new Object[]{null});
        assertEquals(0, timer); assertEquals(2, requests);
        gunHeld = true;
        method.invoke(null, new Object[]{null});
        assertEquals(3, requests);
    }

    private static Method replay(String owner, String methodName, Consumer<MethodNode> substitute,
                                 Class<?> parameter) throws Exception {
        ClassNode original = new ClassNode();
        try (var input = LinkedStorageRefreshBehaviorTest.class.getClassLoader().getResourceAsStream(owner + ".class")) {
            assertNotNull(input);
            new ClassReader(input).accept(original, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        }
        MethodNode body = original.methods.stream().filter(m -> m.name.equals(methodName)).findFirst().orElseThrow();
        substitute.accept(body);
        body.access = Opcodes.ACC_PUBLIC | (body.access & Opcodes.ACC_STATIC);
        body.visibleAnnotations = null; body.invisibleAnnotations = null;
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
        String name = "com/mafuyu404/taczaddon/testutil/Replay";
        writer.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, name, null, "java/lang/Object", null);
        var constructor = writer.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        constructor.visitCode(); constructor.visitVarInsn(Opcodes.ALOAD, 0);
        constructor.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
        constructor.visitInsn(Opcodes.RETURN); constructor.visitMaxs(0, 0); constructor.visitEnd();
        body.accept(writer); writer.visitEnd();
        byte[] bytes = writer.toByteArray();
        Class<?> fixture = new ClassLoader(LinkedStorageRefreshBehaviorTest.class.getClassLoader()) {
            Class<?> define() { return defineClass(name.replace('/', '.'), bytes, 0, bytes.length); }
        }.define();
        return fixture.getMethod(methodName, parameter);
    }
}
