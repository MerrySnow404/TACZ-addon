package com.mafuyu404.taczaddon.compat.sophisticated;

import net.neoforged.fml.ModList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Capability-aware guard for the optional Sophisticated Backpacks + Core integration.
 *
 * <p>Every public facade operation executes through {@link #call(SophisticatedCapability, Supplier,
 * Function)} or {@link #run(SophisticatedCapability, Consumer)}. The guard:
 *
 * <ul>
 *   <li>checks mod presence once and maps absence to ABSENT;
 *   <li>loads the TACZAddon-owned integration implementation by class name only after both mods are
 *       present;
 *   <li>probes each capability once, on first use;
 *   <li>marks exactly the failing capability BROKEN on a {@link LinkageError} or {@link
 *       SophisticatedCompatibilityException} and returns the neutral fallback.
 * </ul>
 *
 * <p>Ordinary {@link RuntimeException}s and {@link Exception}s are not swallowed: they represent
 * TACZAddon implementation bugs or invalid game state and must remain observable. A BROKEN
 * capability is never retried and never logs again for the rest of the session.
 *
 * <p>This class and everything reachable from the safe facade contain no compile-time reference to
 * any Sophisticated class.
 */
public final class SophisticatedRuntime {

    private static final Logger LOGGER = LoggerFactory.getLogger(SophisticatedRuntime.class);

    private static final String BACKPACKS_MOD_ID = "sophisticatedbackpacks";
    private static final String CORE_MOD_ID = "sophisticatedcore";
    private static final String INTEGRATION_IMPL_CLASS =
            "com.mafuyu404.taczaddon.compat.sophisticated."
                    + "SophisticatedBackpacksIntegrationImpl";

    private static final SophisticatedRuntime INSTANCE =
            new SophisticatedRuntime(
                    () -> isModLoaded(BACKPACKS_MOD_ID),
                    () -> isModLoaded(CORE_MOD_ID),
                    SophisticatedRuntime::loadIntegration);

    @FunctionalInterface
    public interface IntegrationFactory {
        SophisticatedBackpacksIntegration create() throws SophisticatedCompatibilityException;
    }

    private final Supplier<Boolean> backpacksPresent;
    private final Supplier<Boolean> corePresent;
    private final IntegrationFactory integrationFactory;

    private final EnumMap<SophisticatedCapability, SophisticatedCapabilityState> states =
            new EnumMap<>(SophisticatedCapability.class);
    private final Set<SophisticatedCapability> brokenLogged =
            EnumSet.noneOf(SophisticatedCapability.class);

    private SophisticatedBackpacksIntegration integration;
    private boolean initialized;
    private boolean dependencyInconsistencyLogged;
    private boolean integrationLoadFailureLogged;

    SophisticatedRuntime(
            Supplier<Boolean> backpacksPresent,
            Supplier<Boolean> corePresent,
            IntegrationFactory integrationFactory) {
        this.backpacksPresent = backpacksPresent;
        this.corePresent = corePresent;
        this.integrationFactory = integrationFactory;

        for (SophisticatedCapability capability : SophisticatedCapability.values()) {
            states.put(capability, SophisticatedCapabilityState.UNINITIALIZED);
        }
    }

    public static SophisticatedRuntime get() {
        return INSTANCE;
    }

    /**
     * Runs environment checks and loads the integration implementation. Idempotent; capability
     * probes stay lazy until first guarded use.
     */
    public void ensureInitialized() {
        synchronized (this) {
            if (initialized) {
                return;
            }
            initialized = true;

            if (!Boolean.TRUE.equals(backpacksPresent.get())) {
                setAllStates(SophisticatedCapabilityState.ABSENT);
                return;
            }

            if (!Boolean.TRUE.equals(corePresent.get())) {
                setAllStates(SophisticatedCapabilityState.BROKEN);
                logDependencyInconsistency();
                return;
            }

            try {
                integration = integrationFactory.create();
            } catch (SophisticatedCompatibilityException exception) {
                integration = null;
                setAllStates(SophisticatedCapabilityState.BROKEN);
                logIntegrationLoadFailure(exception);
                return;
            } catch (LinkageError error) {
                integration = null;
                setAllStates(SophisticatedCapabilityState.BROKEN);
                logIntegrationLoadFailure(error);
                return;
            }

            if (integration == null) {
                setAllStates(SophisticatedCapabilityState.BROKEN);
                logIntegrationLoadFailure(
                        new SophisticatedCompatibilityException(
                                "Integration factory returned null"));
            }
        }
    }

    /**
     * Guarded capability call.
     *
     * @param capability capability whose READY state gates the operation
     * @param fallback neutral result returned for ABSENT/BROKEN or after a contained linkage
     *     failure
     * @param operation adapter invocation against the loaded integration
     * @return operation result, or the fallback when the capability is not usable
     */
    public <R> R call(
            SophisticatedCapability capability,
            Supplier<R> fallback,
            Function<SophisticatedBackpacksIntegration, R> operation) {
        return call(capability, fallback, operation, false);
    }

    /** An unavailable backend may be skipped, but a started mutation must never be retried. */
    public <R> R mutate(
            SophisticatedCapability capability,
            Supplier<R> fallback,
            Function<SophisticatedBackpacksIntegration, R> operation) {
        return call(capability, fallback, operation, true);
    }

    private <R> R call(
            SophisticatedCapability capability,
            Supplier<R> fallback,
            Function<SophisticatedBackpacksIntegration, R> operation,
            boolean mutation) {
        if (capability == null) {
            throw new IllegalArgumentException("capability must not be null");
        }
        if (fallback == null) {
            throw new IllegalArgumentException("fallback must not be null");
        }
        if (operation == null) {
            throw new IllegalArgumentException("operation must not be null");
        }

        ensureInitialized();
        ensureCapabilityState(capability);

        if (stateOf(capability) != SophisticatedCapabilityState.READY) {
            return fallback.get();
        }

        try {
            return operation.apply(requireIntegration());
        } catch (SophisticatedCompatibilityException exception) {
            markBroken(capability, exception);
            if (mutation) throw exception;
            return fallback.get();
        } catch (LinkageError error) {
            markBroken(capability, error);
            if (mutation) throw error;
            return fallback.get();
        }
    }

    /**
     * Guarded void capability operation; equivalent to {@link #call(SophisticatedCapability,
     * Supplier, Function)} with a no-op fallback.
     */
    public void run(
            SophisticatedCapability capability,
            Consumer<SophisticatedBackpacksIntegration> operation) {
        if (operation == null) {
            throw new IllegalArgumentException("operation must not be null");
        }

        call(
                capability,
                () -> null,
                integration -> {
                    operation.accept(integration);
                    return null;
                });
    }

    SophisticatedCapabilityState stateOf(SophisticatedCapability capability) {
        synchronized (this) {
            return states.get(capability);
        }
    }

    private synchronized void ensureCapabilityState(SophisticatedCapability capability) {
        if (states.get(capability) != SophisticatedCapabilityState.UNINITIALIZED) {
            return;
        }

        if (integration == null) {
            markBroken(
                    capability,
                    new SophisticatedCompatibilityException(
                            "No Sophisticated integration implementation " + "is available"));
            return;
        }

        boolean usable;
        try {
            usable =
                    switch (capability) {
                        case CARRIED_BACKPACK -> integration.probeCarriedBackpack();
                        case BLOCK_BACKPACK -> integration.probeBlockBackpack();
                        case CLIENT_SYNC -> integration.probeClientSync();
                    };
        } catch (SophisticatedCompatibilityException exception) {
            markBroken(capability, exception);
            return;
        } catch (LinkageError error) {
            markBroken(capability, error);
            return;
        }

        if (usable) {
            states.put(capability, SophisticatedCapabilityState.READY);
        } else {
            states.put(capability, SophisticatedCapabilityState.BROKEN);
            logCapabilityUnavailable(capability);
        }
    }

    private synchronized void markBroken(SophisticatedCapability capability, Throwable failure) {
        if (states.get(capability) == SophisticatedCapabilityState.BROKEN) {
            return;
        }

        states.put(capability, SophisticatedCapabilityState.BROKEN);

        if (brokenLogged.add(capability)) {
            LOGGER.error(
                    "[taczaddon] Sophisticated capability '{}' disabled "
                            + "after a compatibility failure. Sophisticated "
                            + "Backpacks version: {}. Sophisticated Core "
                            + "version: {}. Failure: {}: {}",
                    capability,
                    modVersion(BACKPACKS_MOD_ID),
                    modVersion(CORE_MOD_ID),
                    failure.getClass().getName(),
                    failure.getMessage(),
                    failure);
        }
    }

    private void logCapabilityUnavailable(SophisticatedCapability capability) {
        if (!brokenLogged.add(capability)) {
            return;
        }

        if (capability == SophisticatedCapability.CLIENT_SYNC) {
            LOGGER.error(
                    "[taczaddon] Sophisticated CLIENT_SYNC capability is "
                            + "unavailable: the native normal/linked contents "
                            + "protocol contract is not usable in this "
                            + "environment, so bootstrap content requests "
                            + "are disabled. Sophisticated Backpacks "
                            + "version: {}. Sophisticated Core version: {}. "
                            + "Carried and block backpack capabilities remain "
                            + "independent.",
                    modVersion(BACKPACKS_MOD_ID),
                    modVersion(CORE_MOD_ID));
            return;
        }

        LOGGER.error(
                "[taczaddon] Sophisticated capability '{}' unavailable after "
                        + "its compatibility probe. Sophisticated Backpacks "
                        + "version: {}. Sophisticated Core version: {}.",
                capability,
                modVersion(BACKPACKS_MOD_ID),
                modVersion(CORE_MOD_ID));
    }

    private void logDependencyInconsistency() {
        if (dependencyInconsistencyLogged) {
            return;
        }
        dependencyInconsistencyLogged = true;

        LOGGER.error(
                "[taczaddon] Sophisticated Backpacks is present (version {}) "
                        + "but Sophisticated Core is not loaded. All "
                        + "Sophisticated capabilities are disabled; TACZAddon "
                        + "continues without backpack support.",
                modVersion(BACKPACKS_MOD_ID));
    }

    private void logIntegrationLoadFailure(Throwable failure) {
        if (integrationLoadFailureLogged) {
            return;
        }
        integrationLoadFailureLogged = true;

        LOGGER.error(
                "[taczaddon] Sophisticated integration implementation could "
                        + "not be loaded (Sophisticated Backpacks version: "
                        + "{}, Sophisticated Core version: {}). All "
                        + "Sophisticated capabilities are disabled. "
                        + "Failure: {}: {}",
                modVersion(BACKPACKS_MOD_ID),
                modVersion(CORE_MOD_ID),
                failure.getClass().getName(),
                failure.getMessage(),
                failure);
    }

    private synchronized void setAllStates(SophisticatedCapabilityState state) {
        for (SophisticatedCapability capability : SophisticatedCapability.values()) {
            states.put(capability, state);
        }
    }

    private synchronized SophisticatedBackpacksIntegration requireIntegration() {
        return integration;
    }

    private static SophisticatedBackpacksIntegration loadIntegration() {
        ClassLoader loader = SophisticatedRuntime.class.getClassLoader();

        try {
            Class<?> implementationClass = Class.forName(INTEGRATION_IMPL_CLASS, true, loader);

            return (SophisticatedBackpacksIntegration)
                    implementationClass.getDeclaredConstructor().newInstance();
        } catch (ReflectiveOperationException | LinkageError exception) {
            throw new SophisticatedCompatibilityException(
                    "Could not load Sophisticated integration "
                            + "implementation "
                            + INTEGRATION_IMPL_CLASS,
                    exception);
        }
    }

    private static boolean isModLoaded(String modId) {
        ModList modList = ModList.get();
        return modList != null && modList.isLoaded(modId);
    }

    private static String modVersion(String modId) {
        ModList modList = ModList.get();
        if (modList == null) {
            return "unknown";
        }

        return modList.getModContainerById(modId)
                .map(container -> container.getModInfo().getVersion().toString())
                .orElse("not loaded");
    }
}
