package com.mafuyu404.taczaddon.rule;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.GameRules;

/**
 * Owns gamerules added by TACZ-addon.
 *
 * {@link #bootstrap()} is called explicitly from the mod constructor so the
 * rule is registered before any world is created. This class must not rely on
 * Forge event-bus registration as a class-loading side effect.
 */
public final class RuleRegistry {
    public static final GameRules.Key<GameRules.BooleanValue>
            LIBERATE_ATTACHMENT = GameRules.register(
            "liberateAttachment",
            GameRules.Category.PLAYER,
            GameRules.BooleanValue.create(
                    false,
                    RuleRegistry::onLiberateAttachmentChanged
            )
    );

    public static final GameRules.Key<GameRules.BooleanValue> SHOW_ATTACHMENT_DETAIL =
            GameRules.register(
                    "showAttachmentDetail",
                    GameRules.Category.PLAYER,
                    GameRules.BooleanValue.create(
                            true,
                            RuleRegistry::onShowAttachmentDetailChanged
                    )
            );

    private static void onShowAttachmentDetailChanged(MinecraftServer server, GameRules.BooleanValue value) {
        detailChanged.accept(server, value.get());
    }

    private static java.util.function.BiConsumer<MinecraftServer, Boolean> detailChanged = (server, enabled) -> {};
    private static java.util.function.BiConsumer<MinecraftServer, Boolean> liberateChanged = (server, enabled) -> {};

    public static void bindSync(java.util.function.BiConsumer<MinecraftServer, Boolean> detail,
                                java.util.function.BiConsumer<MinecraftServer, Boolean> liberate) {
        detailChanged = java.util.Objects.requireNonNull(detail);
        liberateChanged = java.util.Objects.requireNonNull(liberate);
    }

    private RuleRegistry() {
    }

    public static void bootstrap() {
        // Calling this method is the explicit class-initialization boundary.
    }

    private static void onLiberateAttachmentChanged(MinecraftServer server, GameRules.BooleanValue value) {
        liberateChanged.accept(server, value.get());
    }
}
