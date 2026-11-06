package com.mafuyu404.taczaddon.compat.tacz.contract;

import com.mafuyu404.taczaddon.compat.tacz.contract.TaczFeature;

import java.util.List;

public record FeatureContract(
        TaczFeature feature,
        String profileId,
        List<ClassContract> classes
) {
    public FeatureContract(
            TaczFeature feature,
            String profileId,
            ClassContract... classes
    ) {
        this(feature, profileId, List.of(classes));
    }
}
