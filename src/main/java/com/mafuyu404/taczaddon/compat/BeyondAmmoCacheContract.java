package com.mafuyu404.taczaddon.compat;

/** Byte-only gate; optional client classes must not be linked by the Mixin plugin. */
public final class BeyondAmmoCacheContract {
    public static final String CACHE = "com.solr98.beyondintegration.client.TaczAmmoCache";
    private static final String RESOURCE = "(Lnet/minecraft/resources/ResourceLocation;)";
    private static Boolean supported;

    private BeyondAmmoCacheContract() {}

    public static synchronized boolean isSupported() {
        if (supported == null) {
            supported = supports(ApiShapeProbe.sourceFor(BeyondAmmoCacheContract.class));
        }
        return supported;
    }

    /**
     * Client-side capability gate: the cache must expose the per-ammo query API our guard calls,
     * and Beyond Integration must expose the client ammo-id resolver.
     *
     * <p>The cache internals are intentionally not pinned: Beyond Integration 0.8 replaced the
     * single-network {@code putAll} snapshot with an incremental per-ammo/per-network structure,
     * so an implementation-shape assertion here would only disable the guard on newer builds.
     */
    static boolean supports(ApiShapeProbe.ClassBytes source) {
        ApiShapeProbe.Shape shape = ApiShapeProbe.inspect(source, CACHE);
        return shape.hasMethod("hasData", RESOURCE + "Z")
                && shape.hasMethod("getCount", RESOURCE + "I")
                && shape.hasMethod("requestQuick", RESOURCE + "V")
                && shape.hasMethod("applyPending", "()V")
                && ApiShapeProbe.hasMethod(source,
                "com.solr98.beyondintegration.feature.ammo.tacz.TaczAmmoExtractor",
                "getAmmoIdClient", "(Lnet/minecraft/world/item/ItemStack;)"
                        + "Lnet/minecraft/resources/ResourceLocation;");
    }
}
