package com.mafuyu404.taczaddon.compat;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * Isolation layer over Beyond Integration's stable API.
 *
 * <p>Uses reflection on purpose: TACZ-addon must compile without a Beyond Integration dependency
 * (and without pinning to a specific BI build), while
 * {@link BeyondIntegrationCompat#isSupported()} verifies the method shape from raw class bytes
 * before this class is ever used. A missing BI or an older build therefore never reaches this
 * call site.
 */
final class BeyondAmmoApi {

    private static final String EXTRACTOR_CLASS =
            "com.solr98.beyondintegration.feature.ammo.tacz.TaczAmmoExtractor";
    private static final String CONSUME_METHOD = "tryConsumeFromAll";

    private static volatile java.lang.reflect.Method consumeMethod;

    private BeyondAmmoApi() {
    }

    /** Direct call: returns the exact amount consumed from the player's primary network. */
    static int tryConsumeFromAll(ServerPlayer player, ItemStack gunStack, int requested) {
        try {
            java.lang.reflect.Method method = consumeMethod;
            if (method == null) {
                Class<?> extractor = Class.forName(EXTRACTOR_CLASS);
                method = extractor.getMethod(
                        CONSUME_METHOD, ServerPlayer.class, ItemStack.class, int.class);
                consumeMethod = method;
            }
            Object result = method.invoke(null, player, gunStack, requested);
            return result instanceof Integer consumed ? consumed : 0;
        } catch (ReflectiveOperationException | RuntimeException error) {
            // The shape was verified before this call; failing here means the invocation itself is
            // unreliable, so surface it as a linkage problem and let the orchestrator stop the round.
            throw new LinkageError(
                    "Beyond Integration ammo API invocation failed: " + error.getClass().getSimpleName(),
                    error);
        }
    }
}
