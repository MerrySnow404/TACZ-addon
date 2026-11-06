package com.mafuyu404.taczaddon.compat.curios;

import com.mafuyu404.taczaddon.ammo.api.ConsumptionOutcome;

import com.mafuyu404.taczaddon.compat.sophisticated.SophisticatedBackpacksCompat;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class CuriosCompatibilityTest {
    @Test void linkageFailureIsLatchedAndDoesNotDisableOtherSources() throws Exception {
        var latch = CuriosCompat.class.getDeclaredField("linkageBroken");
        latch.setAccessible(true);
        try {
            assertFalse(CuriosCompat.runGuarded(() -> { throw new NoSuchMethodError("fixture"); }));
            assertTrue(latch.getBoolean(null));
            assertFalse(CuriosCompat.runGuarded(() -> { fail("broken integration retried"); return true; }));
            var ordinaryLatch = SophisticatedBackpacksCompat.class.getDeclaredField("linkageBroken");
            ordinaryLatch.setAccessible(true);
            assertFalse(ordinaryLatch.getBoolean(null));
            assertEquals(7, com.mafuyu404.taczaddon.ammo.AmmoConsumptionOrchestrator.consumeRemaining(
                    7, 2,
                    remaining -> ConsumptionOutcome.confirmed(0),
                    remaining -> ConsumptionOutcome.confirmed(remaining)
            ).consumed());
        } finally { latch.setBoolean(null, false); }
    }

    @Test void optionalBoundaryAndQueryPathsDoNotDuplicateNativeInventory() throws Exception {
        Path root = Path.of("src/main/java/com/mafuyu404/taczaddon");
        try (var files = Files.walk(root)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                if (!file.getFileName().toString().equals("CuriosCompatInner.java"))
                    assertFalse(Files.readString(file).contains("import top.theillusivec4.curios"), file.toString());
            }
        }
        String inner = Files.readString(root.resolve("compat/curios/CuriosCompatInner.java"));
        assertTrue(inner.contains("getCuriosInventory(player).resolve()"));
        assertTrue(inner.contains("getCurios()"));
        assertTrue(inner.contains("getStacks()"));
        assertFalse(inner.contains("getInventory()"));
        assertFalse(inner.contains("getCosmeticStacks()"));
        String query = Files.readString(root.resolve("ammo/BackpackAmmoService.java"));
        assertEquals(2, query.split("CuriosCompat.visitHandlers", -1).length - 1);
        for (String mixin : new String[] {"AbstractGunItemMixin", "ModernKineticGunScriptAPIHasAmmoMixin", "GunAnimationStateContextMixin"})
            assertTrue(Files.readString(root.resolve("mixin/tacz/v1_1_8/" + mixin + ".java")).contains("BackpackAmmoService."));
        String hud = Files.readString(root.resolve("client/ammo/BackpackHudState.java"));
        assertEquals(1, hud.split("combined.addAll\\(player.getInventory\\(\\).items\\)", -1).length - 1);
        assertTrue(hud.contains("CuriosCompat.visitHandlers"));
        String metadata = Files.readString(Path.of("src/main/resources/META-INF/mods.toml"));
        String curios = metadata.substring(metadata.indexOf("modId=\"curios\""));
        assertTrue(curios.contains("mandatory=false"));
        assertTrue(curios.contains("versionRange=\"[0,)\""));
    }
}
