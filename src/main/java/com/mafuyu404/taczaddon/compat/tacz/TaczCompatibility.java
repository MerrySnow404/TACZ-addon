package com.mafuyu404.taczaddon.compat.tacz;

import com.mafuyu404.taczaddon.compat.tacz.contract.FeatureContract;
import com.mafuyu404.taczaddon.compat.tacz.contract.TaczFeature;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * Version adapters have three independent states.
 *
 * <ol>
 *     <li><b>binary</b>: does the exact TaCZ profile satisfy the whole feature
 *     contract;</li>
 *     <li><b>enabled</b>: is the feature also allowed on this run side and are
 *     all transitive dependencies satisfied;</li>
 *     <li><b>applied</b>: did the Mixin framework actually apply the adapter
 *     to its target. Application is recorded for diagnostics only, never fed
 *     back into the enable decision: target classes load lazily, so a missing
 *     {@code postApply} must never be read as a missing dependency.</li>
 * </ol>
 */
public final class TaczCompatibility {
    private static final Logger LOGGER =
            LogUtils.getLogger();

    private static volatile boolean initialized;
    private static volatile String implementationVersion = "unknown";
    private static volatile TaczCompatibilityProfile profile =
            TaczCompatibilityProfile.UNKNOWN;
    private static volatile boolean taczPresent;
    private static volatile long appliedMixinCount;

    private static final Map<TaczFeature, ResolvedState> FEATURE_STATES =
            Collections.synchronizedMap(
                    new EnumMap<>(TaczFeature.class)
            );

    private static final Set<String> APPLIED_MIXINS =
            ConcurrentHashMap.newKeySet();

    private TaczCompatibility() {
    }

    public static TaczCompatibilityProfile profile() {
        ensureInitialized();
        return profile;
    }

    /** Effective status: binary support folded with the dependency graph. */
    public static boolean isAvailable(TaczFeature feature) {
        return status(feature) == TaczFeatureStatus.SUPPORTED;
    }

    public static TaczFeatureStatus status(TaczFeature feature) {
        ensureInitialized();
        ResolvedState state = FEATURE_STATES.get(feature);
        return state == null
                ? TaczFeatureStatus.DISABLED
                : state.status();
    }

    /** Binary support before side and dependency folding. */
    public static TaczFeatureStatus binaryStatus(TaczFeature feature) {
        ensureInitialized();
        return computeBinaryState(feature).status();
    }

    public static String reason(TaczFeature feature) {
        ensureInitialized();
        ResolvedState state = FEATURE_STATES.get(feature);
        return state == null ? "feature state unavailable" : state.reason();
    }

    public static boolean sideAllows(TaczMixinBinding binding) {
        return binding.runtimeSide().allows(TaczRuntimeSide.currentDist());
    }

    public static CompatibilitySnapshot snapshot() {
        ensureInitialized();
        EnumMap<TaczFeature, TaczFeatureStatus> copy =
                new EnumMap<>(TaczFeature.class);
        for (Map.Entry<TaczFeature, ResolvedState> entry
                : FEATURE_STATES.entrySet()) {
            copy.put(entry.getKey(), entry.getValue().status());
        }
        return new CompatibilitySnapshot(
                implementationVersion,
                profile,
                Collections.unmodifiableMap(copy)
        );
    }

    public static void recordAppliedMixin(String mixinClassName) {
        if (mixinClassName == null) {
            return;
        }
        if (APPLIED_MIXINS.add(mixinClassName)) {
            appliedMixinCount = APPLIED_MIXINS.size();
            LOGGER.debug(
                    "[TACZ-addon compatibility] applied mixin={} (diagnostic "
                            + "only)",
                    mixinClassName
            );
        }
    }

    public static Set<String> appliedMixins() {
        return Collections.unmodifiableSet(
                new TreeSet<>(APPLIED_MIXINS)
        );
    }

    public static long appliedMixinCount() {
        return appliedMixinCount;
    }

    public static void logSummary() {
        ensureInitialized();
        LOGGER.info(
                "[TACZ-addon] TaCZ detected: {} profile: {} "
                        + "(mixins applied so far: {})",
                implementationVersion,
                profile,
                appliedMixinCount
        );
        for (TaczFeature feature : TaczFeature.values()) {
            TaczFeatureStatus effective = status(feature);
            TaczFeatureStatus binary = binaryStatus(feature);
            String suffix = effective == TaczFeatureStatus.SUPPORTED
                    ? ""
                    : " (" + reason(feature) + ")";
            LOGGER.info(
                    "[TACZ-addon] {}: enabled={} binary={}{}",
                    feature,
                    effective,
                    binary,
                    suffix
            );
        }
    }

    public static boolean isMixinBindingAvailable(
            TaczMixinBinding binding
    ) {
        ensureInitialized();
        if (!taczPresent) {
            return false;
        }
        if (!sideAllows(binding)) {
            return false;
        }
        if (!isAvailable(binding.feature())) {
            return false;
        }

        for (TaczFeature dependency : binding.dependencies()) {
            if (!isAvailable(dependency)) {
                return false;
            }
        }

        if (binding.scope() == CompatibilityScope.PUBLIC_STABLE) {
            return binding.contract() == null
                    || TaczBinaryProbe.inspect(
                    binding.contract()
            ).passed();
        }

        if (profile == TaczCompatibilityProfile.UNKNOWN) {
            return false;
        }
        return binding.contract() == null
                || TaczBinaryProbe.inspect(
                binding.contract()
        ).passed();
    }

    private static void ensureInitialized() {
        if (initialized) {
            return;
        }
        synchronized (TaczCompatibility.class) {
            if (initialized) {
                return;
            }

            TaczVersionReader.VersionResolution resolution =
                    TaczVersionReader.resolve();
            implementationVersion =
                    resolution.implementationVersion();
            taczPresent = resolution.present();
            profile = resolution.profile();

            Map<TaczFeature, ResolvedState> resolved = resolveGraph(
                    List.of(TaczFeature.values()),
                    feature -> computeBinaryState(feature).status(),
                    TaczContractRegistry::dependenciesOf
            );
            FEATURE_STATES.clear();
            FEATURE_STATES.putAll(resolved);
            initialized = true;
        }
    }

    /**
     * Resolves binary status into effective status with memoisation and cycle
     * protection.
     *
     * <p>A dependent feature never reports itself as supported while a
     * transitive dependency is disabled, and a dependency cycle terminates as
     * a disabled feature instead of recursing forever.
     */
    static Map<TaczFeature, ResolvedState> resolveGraph(
            Iterable<TaczFeature> features,
            Function<TaczFeature, TaczFeatureStatus> binaryStatus,
            Function<TaczFeature, List<TaczFeature>> dependencies
    ) {
        Map<TaczFeature, ResolvedState> resolved =
                new EnumMap<>(TaczFeature.class);
        for (TaczFeature feature : features) {
            resolveFeature(
                    feature,
                    resolved,
                    binaryStatus,
                    dependencies,
                    new ArrayDeque<>()
            );
        }
        return resolved;
    }

    private static ResolvedState resolveFeature(
            TaczFeature feature,
            Map<TaczFeature, ResolvedState> resolved,
            Function<TaczFeature, TaczFeatureStatus> binaryStatus,
            Function<TaczFeature, List<TaczFeature>> dependencies,
            Deque<TaczFeature> resolving
    ) {
        ResolvedState memoized = resolved.get(feature);
        if (memoized != null) {
            return memoized;
        }

        if (resolving.contains(feature)) {
            ResolvedState cycle = new ResolvedState(
                    TaczFeatureStatus.DISABLED,
                    "dependency cycle: " + describeCycle(
                            resolving,
                            feature
                    )
            );
            resolved.put(feature, cycle);
            return cycle;
        }

        TaczFeatureStatus binary = binaryStatus.apply(feature);
        if (binary != TaczFeatureStatus.SUPPORTED) {
            ResolvedState state =
                    new ResolvedState(binary, "binary state");
            resolved.put(feature, state);
            return state;
        }

        resolving.addLast(feature);
        try {
            List<TaczFeature> declared = dependencies.apply(feature);
            if (declared != null) {
                for (TaczFeature dependency : new ArrayList<>(declared)) {
                    ResolvedState dependencyState = resolveFeature(
                            dependency,
                            resolved,
                            binaryStatus,
                            dependencies,
                            resolving
                    );
                    if (dependencyState.status()
                            != TaczFeatureStatus.SUPPORTED) {
                        ResolvedState disabled = new ResolvedState(
                                TaczFeatureStatus.DISABLED,
                                "requires " + dependency + ": "
                                        + dependencyState.status()
                                        + " (" + dependencyState.reason()
                                        + ")"
                        );
                        resolved.put(feature, disabled);
                        return disabled;
                    }
                }
            }
        } finally {
            resolving.removeLastOccurrence(feature);
        }

        ResolvedState state = new ResolvedState(
                TaczFeatureStatus.SUPPORTED,
                "enabled"
        );
        resolved.put(feature, state);
        return state;
    }

    private static String describeCycle(
            Deque<TaczFeature> resolving,
            TaczFeature repeated
    ) {
        StringBuilder path = new StringBuilder();
        boolean started = false;
        for (TaczFeature feature : resolving) {
            if (started) {
                path.append(" -> ");
            }
            path.append(feature);
            started = true;
        }
        return path.append(" -> ").append(repeated).toString();
    }

    private static ResolvedState computeBinaryState(
            TaczFeature feature
    ) {
        if (!taczPresent) {
            return new ResolvedState(
                    TaczFeatureStatus.NOT_PRESENT,
                    "TaCZ is not present"
            );
        }

        CompatibilityScope scope =
                TaczContractRegistry.scopeFor(feature);
        if (profile == TaczCompatibilityProfile.UNKNOWN
                && scope == CompatibilityScope.VERSION_BOUND) {
            return new ResolvedState(
                    TaczFeatureStatus.UNSUPPORTED_VERSION,
                    "version-bound feature requires recognized TaCZ profile"
            );
        }

        FeatureContract contract =
                TaczContractRegistry.contractFor(feature, profile);
        if (contract == null || contract.classes().isEmpty()) {
            return new ResolvedState(
                    TaczFeatureStatus.SUPPORTED,
                    "explicit scope without binary assumptions"
            );
        }

        TaczBinaryProbe.ProbeResult result =
                TaczBinaryProbe.inspect(contract);
        return result.passed()
                ? new ResolvedState(
                TaczFeatureStatus.SUPPORTED,
                "contract satisfied"
        )
                : new ResolvedState(
                TaczFeatureStatus.BINARY_CONTRACT_MISMATCH,
                result.detail()
        );
    }

    public record CompatibilitySnapshot(
            String implementationVersion,
            TaczCompatibilityProfile profile,
            Map<TaczFeature, TaczFeatureStatus> features
    ) {
    }

    record ResolvedState(
            TaczFeatureStatus status,
            String reason
    ) {
    }
}
