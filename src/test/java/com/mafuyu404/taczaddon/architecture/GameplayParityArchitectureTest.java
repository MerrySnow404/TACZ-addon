package com.mafuyu404.taczaddon.architecture;

import static org.junit.jupiter.api.Assertions.*;

import com.mafuyu404.taczaddon.testutil.SourceTree;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

import java.nio.file.*;
import java.util.*;

/** Checks actual supported TaCZ bytecode as well as the addon injection boundaries. */
class GameplayParityArchitectureTest {
    private static final String CORE_SHOOT =
            "(Ljava/util/function/Supplier;Ljava/util/function/Supplier;JFZ)Lcom/tacz/guns/api/entity/ShootResult;";

    private static ClassNode type(String name) throws Exception {
        try (var input =
                GameplayParityArchitectureTest.class
                        .getClassLoader()
                        .getResourceAsStream(name + ".class")) {
            assertNotNull(input, name);
            ClassNode node = new ClassNode();
            new ClassReader(input).accept(node, 0);
            return node;
        }
    }

    private static MethodNode method(ClassNode type, String name, String descriptor) {
        return type.methods.stream()
                .filter(m -> m.name.equals(name) && m.desc.equals(descriptor))
                .findFirst()
                .orElseThrow();
    }

    private static String source(String name) throws Exception {
        return Files.readString(
                SourceTree.path("src/main/java/com/mafuyu404/taczaddon/" + name + ".java"));
    }

    private static int count(String text, String needle) {
        return text.split(java.util.regex.Pattern.quote(needle), -1).length - 1;
    }

    @Test
    void bothAmmoQueryPathsHaveTheRequiredNeoForgeCapabilityCall() throws Exception {
        var gun = type("com/tacz/guns/api/item/gun/AbstractGunItem");
        for (String name : List.of("canReload", "hasInventoryAmmo")) {
            var query =
                    gun.methods.stream().filter(m -> m.name.equals(name)).findFirst().orElseThrow();
            long calls =
                    Arrays.stream(query.instructions.toArray())
                            .filter(
                                    i ->
                                            i instanceof MethodInsnNode call
                                                    && call.owner.equals(
                                                            "net/minecraft/world/entity/LivingEntity")
                                                    && call.name.equals("getCapability")
                                                    && call.desc.equals(
                                                            "(Lnet/neoforged/neoforge/capabilities/EntityCapability;Ljava/lang/Object;)Ljava/lang/Object;"))
                            .count();
            assertEquals(1, calls, name);
        }
        // Loading the target also executes Mixin's required-injection validation (both calls).
        Class<?> target =
                Class.forName(
                        "com.tacz.guns.api.item.gun.AbstractGunItem",
                        false,
                        getClass().getClassLoader());
        assertTrue(
                Arrays.stream(target.getDeclaredMethods())
                        .anyMatch(m -> m.getName().contains("taczaddon$checkBackpackAmmos")));
    }

    @Test
    void requiredServerMixinsApplyToTheInstalledDependency() throws Exception {
        for (var contract :
                Map.of(
                                "com.tacz.guns.entity.shooter.LivingEntityShoot",
                                        "taczaddon$commitReloadInterruption",
                                "com.tacz.guns.inventory.GunSmithTableMenu",
                                        "taczaddon$bridgeCraft",
                                "com.tacz.guns.item.ModernKineticGunScriptAPI",
                                        "taczaddon$consumeBackpackAmmo",
                                "com.tacz.guns.entity.shooter.LivingEntityDrawGun",
                                        "modifyDrawTime")
                        .entrySet()) {
            Class<?> target = Class.forName(contract.getKey(), false, getClass().getClassLoader());
            assertTrue(
                    Arrays.stream(target.getDeclaredMethods())
                            .anyMatch(method -> method.getName().contains(contract.getValue())),
                    contract.getKey() + " required mixin did not apply");
        }
    }

    @Test
    void serverCommitPointFollowsAllNativeRejections() throws Exception {
        var type = type("com/tacz/guns/entity/shooter/LivingEntityShoot");
        var shot = method(type, "shoot", CORE_SHOOT);
        int commit = -1, reloadChecks = 0, eventCheck = -1, rejections = 0;
        for (int i = 0; i < shot.instructions.size(); i++) {
            var insn = shot.instructions.get(i);
            if (insn instanceof TypeInsnNode allocation
                    && allocation.getOpcode() == Opcodes.NEW
                    && allocation.desc.equals(
                            "com/tacz/guns/network/message/event/ServerMessageGunShoot"))
                commit = i;
            if (insn instanceof MethodInsnNode call
                    && call.owner.equals("com/tacz/guns/api/entity/ReloadState$StateType")
                    && call.name.equals("isReloading")) reloadChecks++;
            if (insn instanceof MethodInsnNode call
                    && call.owner.equals("com/tacz/guns/api/event/common/GunShootEvent")
                    && call.name.equals("isCanceled")) eventCheck = i;
            if (insn instanceof FieldInsnNode field
                    && field.owner.equals("com/tacz/guns/api/entity/ShootResult")
                    && !field.name.equals("SUCCESS")) {
                assertEquals(
                        -1,
                        commit,
                        "Native failure follows the selected commit point: " + field.name);
                rejections++;
            }
        }
        assertEquals(1, reloadChecks);
        assertTrue(rejections >= 10);
        assertTrue(eventCheck >= 0 && commit > eventCheck);
        var wrapper =
                method(
                        type,
                        "shoot",
                        "(Ljava/util/function/Supplier;Ljava/util/function/Supplier;J)Lcom/tacz/guns/api/entity/ShootResult;");
        assertTrue(
                Arrays.stream(wrapper.instructions.toArray())
                        .anyMatch(
                                i ->
                                        i instanceof MethodInsnNode call
                                                && call.name.equals("shoot")
                                                && call.desc.equals(CORE_SHOOT)));
        String addon = source("mixin/LivingEntityShootMixin");
        assertTrue(addon.contains(CORE_SHOOT));
        assertEquals(1, count(addon, "operator.cancelReload()"));
        assertTrue(
                addon.indexOf("taczaddon$commitReloadInterruption")
                        < addon.indexOf("operator.cancelReload()"));
        String head =
                addon.substring(addon.indexOf("taczaddon$beforeShot"), addon.indexOf("@Redirect"));
        assertFalse(head.contains("cancelReload"));
        assertFalse(head.contains("reloadTimestamp ="));
        assertTrue(addon.contains("@At(\"RETURN\")"));
        assertFalse(addon.contains("require = 0"));
    }

    @Test
    void clientHooksMatchNativeLockAndReloadGates() throws Exception {
        var type = type("com/tacz/guns/client/gameplay/LocalPlayerShoot");
        var shoot = method(type, "shoot", "()Lcom/tacz/guns/api/entity/ShootResult;");
        long locks =
                Arrays.stream(shoot.instructions.toArray())
                        .filter(
                                i ->
                                        i instanceof FieldInsnNode field
                                                && field.name.equals("clientStateLock")
                                                && field.getOpcode() == Opcodes.GETFIELD)
                        .count();
        assertEquals(1, locks);
        long commits =
                Arrays.stream(shoot.instructions.toArray())
                        .filter(
                                i ->
                                        i instanceof MethodInsnNode call
                                                && call.name.equals("lockState")
                                                && call.desc.equals(
                                                        "(Ljava/util/function/Predicate;)V"))
                        .count();
        assertEquals(1, commits);
        var preCheck =
                type.methods.stream()
                        .filter(m -> m.name.equals("preCheck"))
                        .findFirst()
                        .orElseThrow();
        assertEquals(
                1,
                Arrays.stream(preCheck.instructions.toArray())
                        .filter(
                                i ->
                                        i instanceof MethodInsnNode call
                                                && call.owner.equals(
                                                        "com/tacz/guns/api/entity/ReloadState$StateType")
                                                && call.name.equals("isReloading"))
                        .count());
        String service = source("combat/client/ClientReloadInterruption");
        assertFalse(service.contains("sendToServer"));
        assertFalse(service.contains("operator.cancelReload()"));
        assertFalse(service.contains("hasInventoryAmmo("));
        assertTrue(service.contains("ClientSyncedConfig.enableShootWhileReloading()"));
        assertTrue(service.contains("GunAnimationConstant.INPUT_CANCEL_RELOAD"));
        assertTrue(service.contains("normalized.startsWith(\"reload\")"));
        assertTrue(service.contains("normalized.contains(\"_reload\")"));
        String mixin = source("mixin/LocalPlayerShootMixin");
        assertTrue(mixin.contains("MeleeInputHandler.onShoot(cir)"));
        assertTrue(mixin.contains("slideShoot"));
        assertTrue(mixin.contains("@At(\"RETURN\")"));
        assertFalse(mixin.contains("require = 0"));
    }

    private static List<MethodInsnNode> calls(String name) throws Exception {
        return type("com/mafuyu404/taczaddon/" + name).methods.stream()
                .flatMap(m -> Arrays.stream(m.instructions.toArray()))
                .filter(MethodInsnNode.class::isInstance)
                .map(MethodInsnNode.class::cast)
                .toList();
    }

    @Test
    void payloadsEnforceRateLimitsAndDelegateCrafting() throws Exception {
        for (String packet :
                List.of(
                        "gunsmith/network/GunSmithCraftRequestPacket",
                        "refit/network/RefitSourceRefreshRequestPacket",
                        "refit/network/RefitExternalAttachmentInstallPacket",
                        "combat/network/SwitchGunPacket",
                        "ammo/network/AmmoBoxCollectPacket")) {
            assertTrue(
                    calls(packet).stream()
                            .anyMatch(
                                    c ->
                                            c.owner.endsWith("/ServerboundPacketGuard")
                                                    && c.name.equals("isRateLimited")),
                    packet);
        }
        var request = calls("gunsmith/network/GunSmithCraftRequestPacket");
        assertTrue(
                request.stream()
                        .anyMatch(
                                c ->
                                        c.owner.endsWith("/GunSmithCraftService")
                                                && c.name.equals("craft")));
        assertFalse(request.stream().anyMatch(c -> c.owner.endsWith("/CraftingTransaction")));
    }

    @Test
    void nearbyResolutionChecksLoadedStateBeforeLookingUpBlocks() throws Exception {
        var resolver = calls("inventory/NearbyInventorySourceResolver");
        int loaded = -1, lookup = -1;
        for (int i = 0; i < resolver.size(); i++) {
            String name = resolver.get(i).name;
            if (name.equals("isLoaded") && loaded < 0) loaded = i;
            if (name.equals("getBlockEntity") && lookup < 0) lookup = i;
            assertNotEquals("getChunk", name);
        }
        assertTrue(loaded >= 0 && lookup > loaded);
        assertTrue(resolver.stream().anyMatch(c -> c.owner.endsWith("/InventoryIntegrations")));
    }

    @Test
    void externalRefitPreservesCommitAndCompensationOrdering() throws Exception {
        var service = type("com/mafuyu404/taczaddon/refit/AttachmentRefitService");
        var install =
                service.methods.stream()
                        .filter(m -> m.name.equals("installExternal"))
                        .findFirst()
                        .orElseThrow();
        var calls =
                Arrays.stream(install.instructions.toArray())
                        .filter(MethodInsnNode.class::isInstance)
                        .map(MethodInsnNode.class::cast)
                        .map(c -> c.name)
                        .toList();
        assertTrue(calls.indexOf("simulateOne") < calls.indexOf("extractOne"));
        assertTrue(calls.indexOf("commitInsert") < calls.indexOf("addFreshEntity"));
        assertTrue(calls.contains("rollback"));
        assertTrue(calls.contains("handleRollbackOutcome"));
        assertTrue(calls.indexOf("close") < calls.indexOf("postChange"));
        assertFalse(calls.contains("drop"));
    }
}
