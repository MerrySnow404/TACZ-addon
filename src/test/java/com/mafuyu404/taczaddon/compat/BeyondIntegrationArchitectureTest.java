package com.mafuyu404.taczaddon.compat;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BeyondIntegrationArchitectureTest {
    private static final Path PROJECT_ROOT =
            Path.of("").toAbsolutePath().normalize();

    @Test
    void bridgeUsesOnlyPlayerMainInvWrapperIdentity()
            throws IOException {
        String beyond = read(
                "src/main/java/com/mafuyu404/taczaddon/compat/"
                        + "BeyondIntegrationCompat.java"
        );
        String mixin = read(
                "src/main/java/com/mafuyu404/taczaddon/mixin/tacz/"
                        + "v1_1_8/ModernKineticGunScriptAPIMixin.java"
        );

        assertTrue(beyond.contains(
                "PlayerMainInvWrapper"
        ));
        assertTrue(beyond.contains(
                "findAndExtractInventoryAmmo"
        ));
        // Direct BI API path (preferred): probed by class-name string only — no compile-time
        // dependency on Beyond Integration, invocation goes through the reflective isolation layer.
        assertTrue(beyond.contains(
                "tryConsumeFromAll"
        ));
        assertFalse(beyond.contains(
                "import com.solr98"
        ));
        String api = read(
                "src/main/java/com/mafuyu404/taczaddon/compat/"
                        + "BeyondAmmoApi.java"
        );
        assertTrue(api.contains("getMethod"));
        assertTrue(api.contains("tryConsumeFromAll"));
        assertFalse(api.contains("import com.solr98"));
        assertFalse(beyond.contains("getCapability"));
        assertFalse(beyond.contains("CombinedInvWrapper"));
        assertFalse(beyond.contains("PlayerInvWrapper"));
        assertFalse(beyond.contains("VirtualInventory"));

        assertTrue(mixin.contains(
                "consumeThroughTaczInventoryContract"
        ));
        assertTrue(mixin.contains("at = @At(\"RETURN\")"));
        assertFalse(mixin.contains("at = @At(\"HEAD\")"));
        assertFalse(mixin.contains("com.solr98.beyondintegration"));
        assertFalse(mixin.contains("order ="));
        assertFalse(mixin.contains("static int consumeRemaining"));
        assertFalse(mixin.contains("static int clampConsumed"));
        assertFalse(mixin.contains("IntUnaryOperator"));
    }

    @Test
    void backpackFallbackNoLongerCallsExtensibleTaczMethod()
            throws IOException {
        String service = read(
                "src/main/java/com/mafuyu404/taczaddon/common/"
                        + "BackpackAmmoService.java"
        );

        assertTrue(service.contains(
                "consumeBackpackAmmo("
        ));
        assertTrue(service.contains(
                "extractCompatibleAmmoDirectly"
        ));
        assertFalse(service.contains(
                "consumeCompatibleAmmo"
        ));
        assertFalse(service.contains(
                "findAndExtractInventoryAmmo"
        ));
    }

    @Test
    void orchestratorKeepsCompositionTestableOutsideMixin()
            throws IOException {
        String orchestrator = read(
                "src/main/java/com/mafuyu404/taczaddon/common/"
                        + "AmmoConsumptionOrchestrator.java"
        );

        assertTrue(orchestrator.contains(
                "consumeRemaining"
        ));
        assertTrue(orchestrator.contains(
                "clampConsumed"
        ));
        assertTrue(orchestrator.contains(
                "[TACZ-addon/AmmoFallback]"
        ));
        assertFalse(orchestrator.contains(
                "com.solr98.beyondintegration"
        ));
        assertFalse(orchestrator.contains(
                "net.minecraftforge.items"
        ));
    }

    private static String read(String relativePath)
            throws IOException {
        return Files.readString(
                PROJECT_ROOT.resolve(relativePath),
                StandardCharsets.UTF_8
        );
    }
}
