package com.mafuyu404.taczaddon.compat.tacz;

import com.mafuyu404.taczaddon.compat.tacz.contract.FeatureContract;
import com.mafuyu404.taczaddon.compat.tacz.contract.TaczFeature;

import java.util.List;

/** Entry point for version-specific ABI declarations. */
public final class TaczContractRegistry {
    public static final String PROFILE_ID = Tacz118Contracts.PROFILE_ID;
    private TaczContractRegistry() {}
    public static FeatureContract contractFor(TaczFeature feature) { return Tacz118Contracts.contractFor(feature); }
    public static FeatureContract contractFor(TaczFeature feature, TaczCompatibilityProfile profile) {
        FeatureContract base = contractFor(feature);
        return profile == TaczCompatibilityProfile.TACZ_1_2_0 ? Tacz120Contracts.adapt(feature, base) : base;
    }
    public static CompatibilityScope scopeFor(TaczFeature feature) { return Tacz118Contracts.scopeFor(feature); }
    public static TaczMixinBinding bindingForMixin(String name) {
        TaczMixinBinding binding = Tacz120Contracts.bindingForMixin(name);
        return binding != null ? binding : Tacz118Contracts.bindingForMixin(name);
    }
    public static List<TaczFeature> dependenciesOf(TaczFeature feature) { return Tacz118Contracts.dependenciesOf(feature); }
    public static TaczRuntimeSide sideForMixin(String name) { return Tacz118Contracts.sideForMixin(name); }
    public static TaczFeature featureForMixin(String name) {
        TaczMixinBinding binding = bindingForMixin(name);
        return binding == null ? null : binding.feature();
    }
    public static boolean hasContract(TaczFeature feature) { return Tacz118Contracts.hasContract(feature); }
}
