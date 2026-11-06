package com.mafuyu404.taczaddon.compat.tacz;

import com.mafuyu404.taczaddon.compat.tacz.contract.FeatureContract;
import com.mafuyu404.taczaddon.compat.tacz.contract.TaczFeature;

import java.util.List;

public record TaczMixinBinding(
        String mixinClassName,
        TaczFeature feature,
        FeatureContract contract,
        CompatibilityScope scope,
        List<TaczFeature> dependencies,
        TaczRuntimeSide runtimeSide
) {
    public TaczMixinBinding {
        dependencies = dependencies == null
                ? List.of()
                : List.copyOf(dependencies);
        runtimeSide = runtimeSide == null
                ? TaczRuntimeSide.COMMON
                : runtimeSide;
    }

    public TaczMixinBinding(
            String mixinClassName,
            TaczFeature feature,
            FeatureContract contract,
            CompatibilityScope scope
    ) {
        this(
                mixinClassName,
                feature,
                contract,
                scope,
                List.of(),
                TaczRuntimeSide.COMMON
        );
    }

    public TaczMixinBinding(
            String mixinClassName,
            TaczFeature feature,
            FeatureContract contract,
            CompatibilityScope scope,
            List<TaczFeature> dependencies
    ) {
        this(
                mixinClassName,
                feature,
                contract,
                scope,
                dependencies,
                TaczRuntimeSide.COMMON
        );
    }
}
