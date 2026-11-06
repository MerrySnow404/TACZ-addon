package com.mafuyu404.taczaddon.compat.sophisticated;

import com.mafuyu404.taczaddon.compat.api.ApiShapeProbe;

import java.util.List;
import java.util.UUID;

/** Optional block endpoint identity, independent of per-block capability wrappers. */
public final class SophisticatedBlockStorageCompat {
    private static final boolean PRESENT = matches(
            ApiShapeProbe.sourceFor(SophisticatedBlockStorageCompat.class));

    static boolean matches(ApiShapeProbe.ClassBytes source) {
        return ApiShapeProbe.satisfies(source,
                "net.p3pp3rf1y.sophisticatedcore.linkedstorage.ILinkedStorageBlockEndpoint",
                List.of(ApiShapeProbe.method("getLinkedStorageEndpointData",
                        "()Lnet/p3pp3rf1y/sophisticatedcore/linkedstorage/LinkedStorageEndpointData;")))
                && ApiShapeProbe.satisfies(source,
                "net.p3pp3rf1y.sophisticatedcore.linkedstorage.LinkedStorageEndpointData",
                List.of(ApiShapeProbe.method("groupId", "()Ljava/util/UUID;")));
    }

    private SophisticatedBlockStorageCompat() {}

    public static UUID groupId(Object blockEntity) {
        if (!PRESENT || blockEntity == null) return null;
        // Keep optional types in a separate class so older Core or no Core can load this facade.
        return SophisticatedBlockStorageCompatInner.groupId(blockEntity);
    }
}
