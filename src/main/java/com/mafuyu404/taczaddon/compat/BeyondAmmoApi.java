package com.mafuyu404.taczaddon.compat;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * Isolation layer over Beyond Integration's stable API.
 *
 * <p>Uses reflection on purpose: TACZ-addon must compile without a Beyond Integration dependency
 * (and without pinning to a specific BI build or package layout), while
 * {@link BeyondIntegrationCompat#isSupported()} verifies the method shapes from raw class bytes
 * before this class is ever used. A missing BI or an older build therefore never reaches this
 * call site.
 */
final class BeyondAmmoApi {

    private static final String EXTRACTOR_CLASS =
            "com.solr98.beyondintegration.feature.ammo.tacz.TaczAmmoExtractor";
    private static final String CACHE_CLASS =
            "com.solr98.beyondintegration.client.TaczAmmoCache";

    private static volatile java.lang.reflect.Method consumeMethod;
    private static volatile java.lang.reflect.Method ammoIdMethod;
    private static volatile java.lang.reflect.Method hasDataMethod;
    private static volatile java.lang.reflect.Method requestQuickMethod;
    private static volatile java.lang.reflect.Method getCountMethod;

    private BeyondAmmoApi() {
    }

    /** Direct call: returns the exact amount consumed from the player's primary network. */
    static int tryConsumeFromAll(ServerPlayer player, ItemStack gunStack, int requested) {
        try {
            java.lang.reflect.Method method = consumeMethod;
            if (method == null) {
                Class<?> extractor = Class.forName(EXTRACTOR_CLASS);
                method = extractor.getMethod(
                        "tryConsumeFromAll", ServerPlayer.class, ItemStack.class, int.class);
                consumeMethod = method;
            }
            Object result = method.invoke(null, player, gunStack, requested);
            return result instanceof Integer consumed ? consumed : 0;
        } catch (ReflectiveOperationException | RuntimeException error) {
            throw linkageFailure("tryConsumeFromAll", error);
        }
    }

    /** Client-side ammo id resolver for the held gun stack. */
    static ResourceLocation getAmmoIdClient(ItemStack gunStack) {
        try {
            java.lang.reflect.Method method = ammoIdMethod;
            if (method == null) {
                Class<?> extractor = Class.forName(EXTRACTOR_CLASS);
                method = extractor.getMethod("getAmmoIdClient", ItemStack.class);
                ammoIdMethod = method;
            }
            Object result = method.invoke(null, gunStack);
            return result instanceof ResourceLocation id ? id : null;
        } catch (ReflectiveOperationException | RuntimeException error) {
            throw linkageFailure("getAmmoIdClient", error);
        }
    }

    /** Whether the client cache already holds a snapshot for this ammo id. */
    static boolean hasData(ResourceLocation ammoId) {
        try {
            java.lang.reflect.Method method = hasDataMethod;
            if (method == null) {
                method = cacheMethod("hasData", ResourceLocation.class);
                hasDataMethod = method;
            }
            return Boolean.TRUE.equals(method.invoke(null, ammoId));
        } catch (ReflectiveOperationException | RuntimeException error) {
            throw linkageFailure("hasData", error);
        }
    }

    /** Ask the server for a quick single-ammo count update. */
    static void requestQuick(ResourceLocation ammoId) {
        try {
            java.lang.reflect.Method method = requestQuickMethod;
            if (method == null) {
                method = cacheMethod("requestQuick", ResourceLocation.class);
                requestQuickMethod = method;
            }
            method.invoke(null, ammoId);
        } catch (ReflectiveOperationException | RuntimeException error) {
            throw linkageFailure("requestQuick", error);
        }
    }

    /** Cached count for this ammo id across all networks known to the client. */
    static int getCount(ResourceLocation ammoId) {
        try {
            java.lang.reflect.Method method = getCountMethod;
            if (method == null) {
                method = cacheMethod("getCount", ResourceLocation.class);
                getCountMethod = method;
            }
            Object result = method.invoke(null, ammoId);
            return result instanceof Integer count ? count : 0;
        } catch (ReflectiveOperationException | RuntimeException error) {
            throw linkageFailure("getCount", error);
        }
    }

    private static java.lang.reflect.Method cacheMethod(String name, Class<?>... parameterTypes)
            throws ReflectiveOperationException {
        return Class.forName(CACHE_CLASS).getMethod(name, parameterTypes);
    }

    private static LinkageError linkageFailure(String member, Throwable error) {
        // The shape was verified before this call; failing here means the invocation itself is
        // unreliable, so surface it as a linkage problem and let the orchestrator stop the round.
        return new LinkageError(
                "Beyond Integration ammo API invocation failed: "
                        + member + " (" + error.getClass().getSimpleName() + ")",
                error);
    }
}
