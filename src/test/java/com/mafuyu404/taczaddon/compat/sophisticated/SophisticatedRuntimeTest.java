package com.mafuyu404.taczaddon.compat.sophisticated;

import static org.junit.jupiter.api.Assertions.assertEquals;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.items.IItemHandler;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;

/** Capability guard state machine tests, independent of the actual Sophisticated classes. */
class SophisticatedRuntimeTest {
    @Test
    void failedMutationLatchesBackendWithoutReportingAnEmptySource() {
        var runtime = readyRuntime(new StubIntegration());
        var changes = new AtomicInteger();
        var fallbacks = new AtomicInteger();
        org.junit.jupiter.api.Assertions.assertThrows(
                NoSuchMethodError.class,
                () ->
                        runtime.mutate(
                                SophisticatedCapability.CARRIED_BACKPACK,
                                () -> {
                                    fallbacks.incrementAndGet();
                                    return false;
                                },
                                backend -> {
                                    changes.incrementAndGet();
                                    throw new NoSuchMethodError("after extraction");
                                }));
        assertEquals(1, changes.get());
        assertEquals(0, fallbacks.get());
        assertEquals(
                SophisticatedCapabilityState.BROKEN,
                runtime.stateOf(SophisticatedCapability.CARRIED_BACKPACK));
        assertEquals(
                false,
                runtime.mutate(
                        SophisticatedCapability.CARRIED_BACKPACK,
                        () -> false,
                        backend -> {
                            changes.incrementAndGet();
                            return true;
                        }));
        assertEquals(1, changes.get());
    }

    @AfterEach
    void resetPayloadContractState() {
        SophisticatedPayloadContractState.reset();
    }

    private static SophisticatedRuntime runtime(
            Supplier<Boolean> backpacks,
            Supplier<Boolean> core,
            SophisticatedRuntime.IntegrationFactory factory) {
        return new SophisticatedRuntime(backpacks, core, factory);
    }

    private static SophisticatedRuntime readyRuntime(StubIntegration stub) {
        return runtime(() -> true, () -> true, () -> stub);
    }

    @Test
    void readyCallReturnsResultAndStaysReady() {
        StubIntegration stub = new StubIntegration();
        SophisticatedRuntime runtime = readyRuntime(stub);
        AtomicInteger calls = new AtomicInteger();

        assertEquals(
                "result",
                runtime.call(
                        SophisticatedCapability.CARRIED_BACKPACK,
                        () -> "fallback",
                        integration -> {
                            calls.incrementAndGet();
                            return "result";
                        }));
        assertEquals(
                SophisticatedCapabilityState.READY,
                runtime.stateOf(SophisticatedCapability.CARRIED_BACKPACK));

        assertEquals(
                "result",
                runtime.call(
                        SophisticatedCapability.CARRIED_BACKPACK,
                        () -> "fallback",
                        integration -> {
                            calls.incrementAndGet();
                            return "result";
                        }));
        assertEquals(2, calls.get());
        assertEquals(
                SophisticatedCapabilityState.READY,
                runtime.stateOf(SophisticatedCapability.CARRIED_BACKPACK));
    }

    @Test
    void noSuchMethodErrorMarksBrokenAndReturnsFallback() {
        StubIntegration stub = new StubIntegration();
        SophisticatedRuntime runtime = readyRuntime(stub);
        AtomicInteger calls = new AtomicInteger();

        assertEquals(
                "fallback",
                runtime.call(
                        SophisticatedCapability.CARRIED_BACKPACK,
                        () -> "fallback",
                        integration -> {
                            calls.incrementAndGet();
                            throw new NoSuchMethodError(
                                    "runOnBackpacks(Player, "
                                            + "BackpackInventorySlotConsumer)"
                                            + "V");
                        }));
        assertEquals(1, calls.get());
        assertEquals(
                SophisticatedCapabilityState.BROKEN,
                runtime.stateOf(SophisticatedCapability.CARRIED_BACKPACK));
    }

    @Test
    void noClassDefFoundErrorMarksBrokenAndReturnsFallback() {
        SophisticatedRuntime runtime = readyRuntime(new StubIntegration());

        assertEquals(
                "fallback",
                runtime.call(
                        SophisticatedCapability.CARRIED_BACKPACK,
                        () -> "fallback",
                        integration -> {
                            throw new NoClassDefFoundError(
                                    "net/p3pp3rf1y/sophisticatedbackpacks/"
                                            + "backpack/wrapper/"
                                            + "BackpackWrapper");
                        }));
        assertEquals(
                SophisticatedCapabilityState.BROKEN,
                runtime.stateOf(SophisticatedCapability.CARRIED_BACKPACK));
    }

    @Test
    void compatibilityExceptionMarksBrokenAndReturnsFallback() {
        SophisticatedRuntime runtime = readyRuntime(new StubIntegration());

        assertEquals(
                "fallback",
                runtime.call(
                        SophisticatedCapability.CARRIED_BACKPACK,
                        () -> "fallback",
                        integration -> {
                            throw new SophisticatedCompatibilityException("contract probe failed");
                        }));
        assertEquals(
                SophisticatedCapabilityState.BROKEN,
                runtime.stateOf(SophisticatedCapability.CARRIED_BACKPACK));
    }

    @Test
    void brokenCapabilityIsNeverInvokedAgain() {
        SophisticatedRuntime runtime = readyRuntime(new StubIntegration());
        AtomicInteger calls = new AtomicInteger();

        runtime.call(
                SophisticatedCapability.CARRIED_BACKPACK,
                () -> "fallback",
                integration -> {
                    calls.incrementAndGet();
                    throw new NoSuchMethodError("runOnBackpacks(...)V");
                });
        assertEquals(1, calls.get());

        for (int i = 0; i < 3; i++) {
            assertEquals(
                    "fallback",
                    runtime.call(
                            SophisticatedCapability.CARRIED_BACKPACK,
                            () -> "fallback",
                            integration -> {
                                calls.incrementAndGet();
                                return "should-not-happen";
                            }));
        }
        assertEquals(1, calls.get());
        assertEquals(
                SophisticatedCapabilityState.BROKEN,
                runtime.stateOf(SophisticatedCapability.CARRIED_BACKPACK));
    }

    @Test
    void absentBackpacksReturnFallbackWithoutIntegration() {
        AtomicInteger factoryCalls = new AtomicInteger();
        SophisticatedRuntime runtime =
                runtime(
                        () -> false,
                        () -> false,
                        () -> {
                            factoryCalls.incrementAndGet();
                            return new StubIntegration();
                        });
        AtomicInteger operationCalls = new AtomicInteger();

        assertEquals(
                "fallback",
                runtime.call(
                        SophisticatedCapability.CARRIED_BACKPACK,
                        () -> "fallback",
                        integration -> {
                            operationCalls.incrementAndGet();
                            return "result";
                        }));
        assertEquals(0, factoryCalls.get());
        assertEquals(0, operationCalls.get());
        assertEquals(
                SophisticatedCapabilityState.ABSENT,
                runtime.stateOf(SophisticatedCapability.CARRIED_BACKPACK));
        assertEquals(
                SophisticatedCapabilityState.ABSENT,
                runtime.stateOf(SophisticatedCapability.CLIENT_SYNC));
    }

    @Test
    void backpacksWithoutCoreMarksAllCapabilitiesBroken() {
        AtomicInteger factoryCalls = new AtomicInteger();
        SophisticatedRuntime runtime =
                runtime(
                        () -> true,
                        () -> false,
                        () -> {
                            factoryCalls.incrementAndGet();
                            return new StubIntegration();
                        });

        assertEquals(
                "fallback",
                runtime.call(
                        SophisticatedCapability.CARRIED_BACKPACK,
                        () -> "fallback",
                        integration -> "result"));
        assertEquals(0, factoryCalls.get());

        for (SophisticatedCapability capability : SophisticatedCapability.values()) {
            assertEquals(SophisticatedCapabilityState.BROKEN, runtime.stateOf(capability));
        }
    }

    @Test
    void integrationLoadFailureMarksAllBrokenAndIsNotRetried() {
        AtomicInteger factoryCalls = new AtomicInteger();
        SophisticatedRuntime runtime =
                runtime(
                        () -> true,
                        () -> true,
                        () -> {
                            factoryCalls.incrementAndGet();
                            throw new SophisticatedCompatibilityException(
                                    "implementation class could not be loaded");
                        });

        assertEquals(
                "fallback",
                runtime.call(
                        SophisticatedCapability.CARRIED_BACKPACK,
                        () -> "fallback",
                        integration -> "result"));
        assertEquals(
                "fallback",
                runtime.call(
                        SophisticatedCapability.BLOCK_BACKPACK,
                        () -> "fallback",
                        integration -> "result"));
        assertEquals(1, factoryCalls.get());

        for (SophisticatedCapability capability : SophisticatedCapability.values()) {
            assertEquals(SophisticatedCapabilityState.BROKEN, runtime.stateOf(capability));
        }
    }

    @Test
    void failureInOneCapabilityDoesNotAffectAnother() {
        SophisticatedRuntime runtime = readyRuntime(new StubIntegration());

        assertEquals(
                "fallback",
                runtime.call(
                        SophisticatedCapability.CARRIED_BACKPACK,
                        () -> "fallback",
                        integration -> {
                            throw new NoSuchFieldError("ModCoreDataComponents.STORAGE_UUID");
                        }));

        assertEquals(
                "block-result",
                runtime.call(
                        SophisticatedCapability.BLOCK_BACKPACK,
                        () -> "fallback",
                        integration -> "block-result"));
        assertEquals(
                SophisticatedCapabilityState.BROKEN,
                runtime.stateOf(SophisticatedCapability.CARRIED_BACKPACK));
        assertEquals(
                SophisticatedCapabilityState.READY,
                runtime.stateOf(SophisticatedCapability.BLOCK_BACKPACK));
    }

    @Test
    void clientSyncWorksWithInstalledPayloadHook() {
        SophisticatedPayloadContractState.reportPreflight(true);
        SophisticatedPayloadContractState.reportApplied(true);

        SophisticatedRuntime runtime = readyRuntime(new StubIntegration());

        assertEquals(
                "sync-result",
                runtime.call(
                        SophisticatedCapability.CLIENT_SYNC,
                        () -> "fallback",
                        integration -> "sync-result"));

        assertEquals(
                SophisticatedCapabilityState.READY,
                runtime.stateOf(SophisticatedCapability.CLIENT_SYNC));
    }

    @Test
    void payloadPreflightAloneDoesNotInstallOptionalHook() {
        SophisticatedPayloadContractState.reportPreflight(true);

        assertEquals(
                SophisticatedPayloadContractState.State.ELIGIBLE,
                SophisticatedPayloadContractState.state());
        assertEquals(false, SophisticatedPayloadContractState.isUsable());
    }

    @Test
    void clientSyncDoesNotRequirePayloadMixinHook() {
        SophisticatedPayloadContractState.reset();
        assertNativeClientSyncReady();
    }

    @Test
    void ineligiblePayloadHookDoesNotBlockNativeClientSync() {
        SophisticatedPayloadContractState.reset();
        SophisticatedPayloadContractState.reportPreflight(false);
        assertNativeClientSyncReady();
    }

    @Test
    void failedPayloadHookDoesNotBlockNativeClientSync() {
        SophisticatedPayloadContractState.reportPreflight(true);
        SophisticatedPayloadContractState.reportApplied(false);
        assertNativeClientSyncReady();
    }

    private static void assertNativeClientSyncReady() {
        SophisticatedRuntime runtime = readyRuntime(new StubIntegration());
        assertEquals(
                "sync-result",
                runtime.call(
                        SophisticatedCapability.CLIENT_SYNC,
                        () -> "fallback",
                        integration -> "sync-result"));
        assertEquals(
                SophisticatedCapabilityState.READY,
                runtime.stateOf(SophisticatedCapability.CLIENT_SYNC));
    }

    @Test
    void clientSyncProbeLinkageFailureLatchesOnlyClientSync() {
        AtomicInteger probes = new AtomicInteger();
        AtomicInteger calls = new AtomicInteger();
        SophisticatedRuntime runtime =
                readyRuntime(
                        new StubIntegration() {
                            @Override
                            public boolean probeClientSync() {
                                probes.incrementAndGet();
                                throw new NoSuchMethodError(
                                        "RequestLinkedStorageContentsPayload(UUID,long)");
                            }
                        });
        for (int i = 0; i < 3; i++) {
            assertEquals(
                    "fallback",
                    runtime.call(
                            SophisticatedCapability.CLIENT_SYNC,
                            () -> "fallback",
                            integration -> {
                                calls.incrementAndGet();
                                return "sent";
                            }));
        }
        assertEquals(1, probes.get());
        assertEquals(0, calls.get());
        assertEquals(
                SophisticatedCapabilityState.BROKEN,
                runtime.stateOf(SophisticatedCapability.CLIENT_SYNC));
        assertEquals(
                "carried",
                runtime.call(
                        SophisticatedCapability.CARRIED_BACKPACK,
                        () -> "fallback",
                        integration -> "carried"));
        assertEquals(
                "block",
                runtime.call(
                        SophisticatedCapability.BLOCK_BACKPACK,
                        () -> "fallback",
                        integration -> "block"));
    }

    @Test
    void nativeRequestLinkageFailureIsNotRetried() {
        SophisticatedRuntime runtime = readyRuntime(new StubIntegration());
        AtomicInteger calls = new AtomicInteger();
        for (int i = 0; i < 3; i++) {
            assertEquals(
                    "fallback",
                    runtime.call(
                            SophisticatedCapability.CLIENT_SYNC,
                            () -> "fallback",
                            integration -> {
                                calls.incrementAndGet();
                                throw new NoClassDefFoundError(
                                        "RequestLinkedStorageContentsPayload");
                            }));
        }
        assertEquals(1, calls.get());
        assertEquals(
                SophisticatedCapabilityState.BROKEN,
                runtime.stateOf(SophisticatedCapability.CLIENT_SYNC));
    }

    @Test
    void failedPayloadApplyIsNotUsable() {
        SophisticatedPayloadContractState.reportPreflight(true);
        SophisticatedPayloadContractState.reportApplied(false);

        assertEquals(
                SophisticatedPayloadContractState.State.APPLY_FAILED,
                SophisticatedPayloadContractState.state());
        assertEquals(false, SophisticatedPayloadContractState.isUsable());
    }

    @Test
    void postApplyWithoutPreflightFailsClosed() {
        SophisticatedPayloadContractState.reportApplied(true);

        assertEquals(
                SophisticatedPayloadContractState.State.APPLY_FAILED,
                SophisticatedPayloadContractState.state());
        assertEquals(false, SophisticatedPayloadContractState.isUsable());
    }

    @Test
    void successfulPayloadContractIsSticky() {
        SophisticatedPayloadContractState.reportPreflight(true);
        SophisticatedPayloadContractState.reportApplied(true);

        assertEquals(
                SophisticatedPayloadContractState.State.INSTALLED,
                SophisticatedPayloadContractState.state());

        /*
         * Repeated/late Mixin queries must not accidentally downgrade a verified
         * process-lifetime installation.
         */
        SophisticatedPayloadContractState.reportPreflight(false);
        SophisticatedPayloadContractState.reportApplied(false);

        assertEquals(
                SophisticatedPayloadContractState.State.INSTALLED,
                SophisticatedPayloadContractState.state());
        assertEquals(true, SophisticatedPayloadContractState.isUsable());
    }

    @Test
    void runCapabilityGuardsVoidOperations() {
        SophisticatedPayloadContractState.reportPreflight(true);
        SophisticatedPayloadContractState.reportApplied(true);

        SophisticatedRuntime runtime = readyRuntime(new StubIntegration());

        AtomicInteger syncCalls = new AtomicInteger();

        runtime.run(
                SophisticatedCapability.CLIENT_SYNC, integration -> syncCalls.incrementAndGet());

        assertEquals(1, syncCalls.get());

        runtime.run(
                SophisticatedCapability.CARRIED_BACKPACK,
                integration -> {
                    throw new NoSuchMethodError("runOnBackpacks(...)V");
                });

        /*
         * CARRIED_BACKPACK has blown its fuse. It must not execute again.
         */
        runtime.run(
                SophisticatedCapability.CARRIED_BACKPACK,
                integration -> syncCalls.incrementAndGet());

        assertEquals(1, syncCalls.get());
    }

    private static class StubIntegration implements SophisticatedBackpacksIntegration {

        @Override
        public boolean probeCarriedBackpack() {
            return true;
        }

        @Override
        public boolean probeBlockBackpack() {
            return true;
        }

        @Override
        public boolean probeClientSync() {
            return true;
        }

        @Override
        public List<ItemStack> getItemsFromBackpackBlock(BlockPos blockPos, Player player) {
            return new ArrayList<>();
        }

        @Override
        public List<ItemStack> getItemsFromBackpackItem(ItemStack itemStack) {
            return new ArrayList<>();
        }

        @Override
        public List<ItemStack> getItemsFromInventoryBackpack(Player player) {
            return new ArrayList<>();
        }

        @Override
        public boolean visitInventoryBackpacks(Player player, Predicate<IItemHandler> visitor) {
            return false;
        }

        @Override
        public boolean mutateInventoryBackpacks(
                ServerPlayer player, Predicate<IItemHandler> visitor) {
            return false;
        }

        @Override
        public void syncAllBackpack(Player player) {}

        @Override
        public boolean refreshInventoryBackpackWrapper(Player player, UUID backpackUuid) {
            return false;
        }

        @Override
        public void modifyInventoryBackpack(
                ServerPlayer player, ItemStack backpackItem, Consumer<IItemHandler> action) {}

        @Override
        public void modifyBlockBackpack(
                ServerPlayer player, BlockPos blockPos, Consumer<IItemHandler> action) {}

        @Override
        public void forEachInventoryBackpackHandler(Player player, Consumer<IItemHandler> action) {}

        @Override
        public void forEachBlockBackpackHandler(
                Player player, BlockPos blockPos, Consumer<IItemHandler> action) {}

        @Override
        public boolean isBackpackBlock(Level level, BlockPos blockPos) {
            return false;
        }

        @Override
        public int countInventoryBackpackAmmo(Player player, ItemStack gunStack) {
            return 0;
        }

        @Override
        public List<ItemStack> getAllInventoryBackpack(Player player) {
            return new ArrayList<>();
        }
    }
}
