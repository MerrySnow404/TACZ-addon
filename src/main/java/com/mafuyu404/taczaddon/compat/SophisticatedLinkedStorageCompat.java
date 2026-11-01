package com.mafuyu404.taczaddon.compat;

import com.mojang.logging.LogUtils;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.ModList;
import org.slf4j.Logger;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

final class SophisticatedLinkedStorageCompat {
    private static final Logger LOGGER = LogUtils.getLogger();

    private static final String IMPL_CLASS =
            "com.mafuyu404.taczaddon.compat.SophisticatedLinkedStorageCompat326";

    private static final AtomicBoolean WARNING_LOGGED =
            new AtomicBoolean();

    /*
     * This is intentionally mutable.
     *
     * If a 3.26 linked-storage API links successfully during bootstrap but a
     * later method/linkage fails, the linked bridge is permanently disabled
     * for this session instead of throwing on every HUD/update pass.
     *
     * Ordinary Sophisticated backpack support remains independent.
     */
    private static volatile Bridge BRIDGE = loadBridge();

    /**
     * Structural generation, resolved once from optional class bytes. Loader
     * presence is reported separately by {@link #isInstalled()}.
     */
    private static final SophisticatedBackpackGeneration GENERATION =
            SophisticatedBackpackGeneration.detect(
                    ApiShapeProbe.sourceFor(
                            SophisticatedLinkedStorageCompat.class
                    )
            );

    private SophisticatedLinkedStorageCompat() {
    }

    /**
     * Loader-level presence only. Never used as a "safe to call" gate.
     */
    static boolean isInstalled() {
        ModList modList = ModList.get();
        return modList != null
                && modList.isLoaded(
                SophisticatedBackpackGeneration.CORE_MOD_ID
        );
    }

    static SophisticatedBackpackGeneration generation() {
        return GENERATION;
    }

    static EndpointResolution resolve(ItemStack stack) {
        try {
            return BRIDGE.resolve(stack);
        } catch (LinkageError error) {
            disableBrokenBridge(error);
            return EndpointResolution.unavailableBridge();
        }
    }

    /**
     * Full bootstrap request.
     *
     * 3.26 sends knownRevision=-1 so a new world/session always receives the
     * complete linked-storage snapshot.
     */
    static void requestSnapshot(UUID groupId) {
        try {
            BRIDGE.requestSnapshot(groupId);
        } catch (LinkageError error) {
            disableBrokenBridge(error);
        }
    }

    /**
     * Low-cost coherence probe.
     *
     * The 3.26 implementation sends the currently installed client revision.
     * The Sophisticated server only returns a snapshot when its revision is
     * different, so this can safely run periodically.
     */
    static void refreshSnapshot(UUID groupId) {
        try {
            BRIDGE.refreshSnapshot(groupId);
        } catch (LinkageError error) {
            disableBrokenBridge(error);
        }
    }

    static void sendSnapshot(
            ServerPlayer player,
            UUID groupId
    ) {
        try {
            BRIDGE.sendSnapshot(
                    player,
                    groupId
            );
        } catch (LinkageError error) {
            disableBrokenBridge(error);
        }
    }

    static boolean supported() {
        Bridge bridge = BRIDGE;

        return bridge != NoopBridge.INSTANCE
                && bridge != BrokenBridge.INSTANCE;
    }

    static void requestLocatedSnapshot(UUID groupId, String handlerName, String identifier, int slot, boolean refresh) {
        try {
            BRIDGE.requestLocatedSnapshot(groupId, handlerName, identifier, slot, refresh);
        } catch (LinkageError error) {
            disableBrokenBridge(error);
        }
    }

    private static Bridge loadBridge() {
        /*
         * Detect the generation before touching any 3.26 class. A generation
         * that is not the verified one must never load the linked backend.
         */
        SophisticatedBackpackGeneration generation =
                SophisticatedBackpackGeneration.detect(
                        ApiShapeProbe.sourceFor(
                                SophisticatedLinkedStorageCompat.class
                        )
                );
        if (!generation.linkedStorageSupported()) {
            if (generation
                    == SophisticatedBackpackGeneration.UNKNOWN) {
                logBroken(new IllegalStateException(
                        "unknown Sophisticated linked-storage generation"
                ));
                return BrokenBridge.INSTANCE;
            }
            /*
             * Expected on the verified Sophisticated 3.24.x ordinary-only
             * generation. This is NOT an error and must not disable ordinary
             * backpack compatibility.
             */
            return NoopBridge.INSTANCE;
        }

        ClassLoader loader =
                SophisticatedLinkedStorageCompat.class
                        .getClassLoader();

        try {
            Class.forName(
                    "net.p3pp3rf1y.sophisticatedcore.linkedstorage."
                            + "LinkedStorageStackLifecycle",
                    false,
                    loader
            );
        } catch (ClassNotFoundException absent) {
            logBroken(absent);
            return BrokenBridge.INSTANCE;
        } catch (LinkageError error) {
            logBroken(error);
            return BrokenBridge.INSTANCE;
        }

        try {
            Class<?> implementation =
                    Class.forName(
                            IMPL_CLASS,
                            true,
                            loader
                    );

            return (Bridge) implementation
                    .getDeclaredConstructor()
                    .newInstance();
        } catch (ReflectiveOperationException
                 | LinkageError error) {
            logBroken(error);
            return BrokenBridge.INSTANCE;
        }
    }

    private static void disableBrokenBridge(
            Throwable throwable
    ) {
        BRIDGE = BrokenBridge.INSTANCE;
        logBroken(throwable);
    }

    private static void logBroken(
            Throwable throwable
    ) {
        if (WARNING_LOGGED.compareAndSet(
                false,
                true
        )) {
            LOGGER.warn(
                    "[TACZ-addon] Sophisticated Backpacks linked-storage "
                            + "compatibility is unavailable for this session; "
                            + "ordinary backpack compatibility remains enabled",
                    throwable
            );
        }
    }

    interface Bridge {
        default void requestLocatedSnapshot(UUID groupId, String handlerName, String identifier, int slot, boolean refresh) {
            if (refresh) refreshSnapshot(groupId);
            else requestSnapshot(groupId);
        }
        EndpointResolution resolve(
                ItemStack stack
        );

        /**
         * Force a complete bootstrap snapshot.
         */
        void requestSnapshot(
                UUID groupId
        );

        /**
         * Ask whether the server revision changed.
         *
         * Old/no-op bridges can simply delegate to requestSnapshot because
         * they never actually send anything.
         */
        default void refreshSnapshot(
                UUID groupId
        ) {
            requestSnapshot(groupId);
        }

        void sendSnapshot(
                ServerPlayer player,
                UUID groupId
        );
    }

    enum ResolutionKind {
        NOT_LINKED,
        LINKED,
        MALFORMED_LINKED,
        BRIDGE_UNAVAILABLE
    }

    record EndpointResolution(
            ResolutionKind kind,
            UUID groupId
    ) {
        static EndpointResolution notLinked() {
            return new EndpointResolution(
                    ResolutionKind.NOT_LINKED,
                    null
            );
        }

        static EndpointResolution linked(
                UUID groupId
        ) {
            return new EndpointResolution(
                    ResolutionKind.LINKED,
                    groupId
            );
        }

        static EndpointResolution malformedLinked() {
            return new EndpointResolution(
                    ResolutionKind.MALFORMED_LINKED,
                    null
            );
        }

        static EndpointResolution unavailableBridge() {
            return new EndpointResolution(
                    ResolutionKind.BRIDGE_UNAVAILABLE,
                    null
            );
        }

        boolean linked() {
            return kind == ResolutionKind.LINKED
                    || kind == ResolutionKind.MALFORMED_LINKED;
        }

        boolean bridgeUnavailable() {
            return kind
                    == ResolutionKind.BRIDGE_UNAVAILABLE;
        }

        Optional<UUID> groupIdOptional() {
            return Optional.ofNullable(
                    groupId
            );
        }
    }

    /**
     * Linked storage does not exist on this Sophisticated generation.
     *
     * Ordinary backpacks are fully supported.
     */
    private enum NoopBridge
            implements Bridge {
        INSTANCE;

        @Override
        public EndpointResolution resolve(
                ItemStack stack
        ) {
            return EndpointResolution.notLinked();
        }

        @Override
        public void requestSnapshot(
                UUID groupId
        ) {
        }

        @Override
        public void sendSnapshot(
                ServerPlayer player,
                UUID groupId
        ) {
        }
    }

    /**
     * Linked API was present but failed its runtime ABI contract.
     *
     * This is intentionally distinct from NoopBridge: callers need to know
     * that linked classification is unavailable rather than conclude that
     * every stack is definitely ordinary.
     */
    private enum BrokenBridge
            implements Bridge {
        INSTANCE;

        @Override
        public EndpointResolution resolve(
                ItemStack stack
        ) {
            return EndpointResolution
                    .unavailableBridge();
        }

        @Override
        public void requestSnapshot(
                UUID groupId
        ) {
        }

        @Override
        public void sendSnapshot(
                ServerPlayer player,
                UUID groupId
        ) {
        }
    }
}
