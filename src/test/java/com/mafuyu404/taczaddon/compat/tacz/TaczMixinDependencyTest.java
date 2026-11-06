package com.mafuyu404.taczaddon.compat.tacz;

import com.mafuyu404.taczaddon.compat.tacz.contract.TaczFeature;

import com.mafuyu404.taczaddon.compat.tacz.contract.ClassContract;
import com.mafuyu404.taczaddon.compat.tacz.contract.FeatureContract;
import com.mafuyu404.taczaddon.compat.tacz.contract.FieldContract;
import com.mafuyu404.taczaddon.compat.tacz.contract.MethodContract;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Version adapters must enable and disable as consistent groups.
 */
class TaczMixinDependencyTest {
    private static final Path PROJECT_ROOT =
            Path.of("").toAbsolutePath().normalize();

    private static final String REFIT = "com.mafuyu404.taczaddon.mixin.tacz.v1_1_8.";

    private static final List<String> REFIT_GROUP = List.of(
            "ClientMessageRefitGunAccess",
            "ClientMessageRefitGunMixin",
            "ClientMessageUnloadAttachmentAccess",
            "ClientMessageUnloadAttachmentMixin",
            "GunRefitScreenMixin",
            "InventoryAttachmentSlotAccess"
    );

    @Test
    void refitAccessorAndEveryConsumerShareTheCompleteContract() {
        FeatureContract refit =
                TaczContractRegistry.contractFor(
                        TaczFeature.LIBERATED_REFIT
                );
        assertNotNull(refit);
        assertTrue(
                TaczBinaryProbe.inspect(refit).passed(),
                "the exact TaCZ jar must satisfy the complete refit contract"
        );

        for (String mixin : REFIT_GROUP) {
            TaczMixinBinding binding =
                    TaczContractRegistry.bindingForMixin(
                            REFIT + mixin
                    );
            assertNotNull(binding, mixin + " has no binding");
            assertSame(
                    refit,
                    binding.contract(),
                    mixin + " must consume the complete refit contract so the accessor and its users stay paired"
            );
            assertEquals(
                    TaczFeature.LIBERATED_REFIT,
                    binding.feature()
            );
        }
    }

    @Test
    void renamedSlotFieldDisablesTheWholeContract() {
        /*
         * Same TaCZ version number, renamed accessor field: the shared
         * contract must fail, which is exactly what disables the accessor and
         * the screen that consumes it.
         */
        FeatureContract renamed = new FeatureContract(
                TaczFeature.LIBERATED_REFIT,
                TaczContractRegistry.PROFILE_ID,
                new ClassContract(
                        "com.tacz.guns.client.gui.components.refit.InventoryAttachmentSlot"
                ).withMethods(new MethodContract(
                        "<init>",
                        "(IIILnet/minecraft/world/entity/player/Inventory;Lnet/minecraft/client/gui/components/Button$OnPress;)V"
                )).withFields(FieldContract.of(
                        "renamedInventory",
                        "Lnet/minecraft/world/entity/player/Inventory;"
                ))
        );

        assertFalse(
                TaczBinaryProbe.inspect(renamed).passed(),
                "a renamed inventory field must fail the version contract"
        );

        TaczMixinBinding accessor = new TaczMixinBinding(
                REFIT + "InventoryAttachmentSlotAccess",
                TaczFeature.LIBERATED_REFIT,
                renamed,
                CompatibilityScope.VERSION_BOUND
        );
        TaczMixinBinding consumer = new TaczMixinBinding(
                REFIT + "GunRefitScreenMixin",
                TaczFeature.LIBERATED_REFIT,
                renamed,
                CompatibilityScope.VERSION_BOUND
        );

        assertFalse(
                TaczCompatibility.isMixinBindingAvailable(accessor)
        );
        assertFalse(
                TaczCompatibility.isMixinBindingAvailable(consumer),
                "the accessor consumer must never be applied alone"
        );
    }

    @Test
    void dependencyGraphFoldsFailuresAndTerminatesCycles() {
        Map<TaczFeature, TaczCompatibility.ResolvedState> states =
                TaczCompatibility.resolveGraph(
                        List.of(
                                TaczFeature.GUNSMITH_BROWSE_MEMORY,
                                TaczFeature.GUNSMITH_SCREEN_ACCESS,
                                TaczFeature.AIM_CAMERA
                        ),
                        feature -> feature
                                == TaczFeature.GUNSMITH_SCREEN_ACCESS
                                ? TaczFeatureStatus
                                .BINARY_CONTRACT_MISMATCH
                                : TaczFeatureStatus.SUPPORTED,
                        feature -> feature
                                == TaczFeature.GUNSMITH_BROWSE_MEMORY
                                ? List.of(
                                TaczFeature.GUNSMITH_SCREEN_ACCESS)
                                : List.of()
                );

        assertEquals(
                TaczFeatureStatus.BINARY_CONTRACT_MISMATCH,
                states.get(TaczFeature.GUNSMITH_SCREEN_ACCESS).status()
        );
        assertEquals(
                TaczFeatureStatus.DISABLED,
                states.get(TaczFeature.GUNSMITH_BROWSE_MEMORY).status()
        );
        assertEquals(
                TaczFeatureStatus.SUPPORTED,
                states.get(TaczFeature.AIM_CAMERA).status(),
                "an independent feature stays available"
        );

        Map<TaczFeature, TaczCompatibility.ResolvedState> cycle =
                TaczCompatibility.resolveGraph(
                        List.of(
                                TaczFeature.GUNSMITH_BROWSE_MEMORY,
                                TaczFeature.GUNSMITH_SCREEN_ACCESS
                        ),
                        feature -> TaczFeatureStatus.SUPPORTED,
                        feature -> feature
                                == TaczFeature.GUNSMITH_BROWSE_MEMORY
                                ? List.of(TaczFeature.GUNSMITH_SCREEN_ACCESS)
                                : List.of(
                                TaczFeature.GUNSMITH_BROWSE_MEMORY)
                );

        assertEquals(
                TaczFeatureStatus.DISABLED,
                cycle.get(TaczFeature.GUNSMITH_BROWSE_MEMORY).status()
        );
        assertTrue(
                cycle.get(TaczFeature.GUNSMITH_BROWSE_MEMORY)
                        .reason()
                        .contains("cycle")
        );
    }

    @Test
    void productionDependencyGraphMatchesTheDeclaredBindings() {
        assertTrue(
                TaczContractRegistry.dependenciesOf(
                        TaczFeature.BACKPACK_INVENTORY_FEED_QUERY
                ).contains(TaczFeature.BACKPACK_AMMO_CONSUME)
        );
        assertTrue(
                TaczContractRegistry.dependenciesOf(
                        TaczFeature.GUNSMITH_BROWSE_MEMORY
                ).contains(TaczFeature.GUNSMITH_SCREEN_ACCESS)
        );
        assertTrue(
                TaczContractRegistry.dependenciesOf(
                        TaczFeature.GUNSMITH_CRAFT_BRIDGE
                ).contains(TaczFeature.GUNSMITH_SCREEN_ACCESS)
        );
        assertTrue(
                TaczContractRegistry.dependenciesOf(
                        TaczFeature.GUNSMITH_EXTERNAL_SOURCE_VIEW
                ).contains(TaczFeature.GUNSMITH_SCREEN_ACCESS)
        );

        for (TaczFeature feature : TaczFeature.values()) {
            assertEquals(
                    TaczCompatibility.binaryStatus(feature),
                    TaczCompatibility.status(feature),
                    feature + " has a dependency or contract problem on the exact TaCZ jar"
            );
        }
    }

    @Test
    void inventoryFeedQueryFallsBackWhenBackpackConsumptionIsUnavailable() {
        Map<TaczFeature, TaczCompatibility.ResolvedState> states =
                TaczCompatibility.resolveGraph(
                        List.of(TaczFeature.values()),
                        feature -> feature == TaczFeature.BACKPACK_AMMO_CONSUME
                                ? TaczFeatureStatus.BINARY_CONTRACT_MISMATCH
                                : TaczFeatureStatus.SUPPORTED,
                        TaczContractRegistry::dependenciesOf
                );

        assertEquals(
                TaczFeatureStatus.DISABLED,
                states.get(TaczFeature.BACKPACK_INVENTORY_FEED_QUERY).status()
        );
    }

    @Test
    void runtimeSideMatchesTheSharedMixinConfiguration()
            throws IOException {
        String json = Files.readString(
                PROJECT_ROOT.resolve(
                        "src/main/resources/taczaddon.mixins.json"
                ),
                StandardCharsets.UTF_8
        );

        List<String> common = section(json, "\"mixins\"");
        List<String> client = section(json, "\"client\"");

        assertTrue(common.contains("tacz.v1_1_8.LivingEntityDrawGunMixin"),
                "the server-visible draw hook belongs to the common section");
        assertTrue(client.contains(
                "tacz.v1_1_8.LocalPlayerDrawMixin"
        ));

        for (String entry : common) {
            if (!entry.startsWith("tacz.")) {
                continue;
            }
            assertEquals(
                    TaczRuntimeSide.COMMON,
                    TaczContractRegistry.sideForMixin(
                            "com.mafuyu404.taczaddon.mixin." + entry
                    ),
                    entry
            );
        }
        for (String entry : client) {
            if (!entry.startsWith("tacz.")) {
                continue;
            }
            assertEquals(
                    TaczRuntimeSide.CLIENT,
                    TaczContractRegistry.sideForMixin(
                            "com.mafuyu404.taczaddon.mixin." + entry
                    ),
                    entry
            );
        }
    }

    @Test
    void appliedMixinsAreDiagnosticsOnly() {
        long before = TaczCompatibility.appliedMixinCount();
        TaczFeatureStatus statusBefore =
                TaczCompatibility.status(TaczFeature.HUD_AMMO);

        TaczCompatibility.recordAppliedMixin("com.example.NotYetLoaded");

        assertEquals(before + 1, TaczCompatibility.appliedMixinCount());
        assertTrue(TaczCompatibility.appliedMixins().contains(
                "com.example.NotYetLoaded"
        ));
        assertEquals(
                statusBefore,
                TaczCompatibility.status(TaczFeature.HUD_AMMO),
                "recording an application must not change any enable decision"
        );
    }

    private static List<String> section(String json, String name) {
        int start = json.indexOf(name);
        int open = json.indexOf('[', start);
        int close = json.indexOf(']', open);
        String body = json.substring(open + 1, close);
        List<String> entries = new ArrayList<>();
        for (String raw : body.split(",")) {
            String entry = raw.trim();
            if (entry.startsWith("\"") && entry.endsWith("\"")) {
                entries.add(entry.substring(1, entry.length() - 1));
            }
        }
        return entries;
    }
}
