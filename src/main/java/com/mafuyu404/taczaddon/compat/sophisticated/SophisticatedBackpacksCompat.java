package com.mafuyu404.taczaddon.compat.sophisticated;

import com.mafuyu404.taczaddon.storage.api.CraftingItemSource;

import com.mojang.logging.LogUtils;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.items.IItemHandler;
import org.slf4j.Logger;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;

/**
 * Optional Sophisticated Backpacks facade.
 *
 * <p>The outer class contains no Sophisticated API types. State is split into
 * three independent questions:
 *
 * <ul>
 *     <li>{@link #isInstalled()} - Forge reports the mod as loaded;</li>
 *     <li>{@link #isSupported()} - the installed generation exposes the
 *     ordinary backpack API this facade links against;</li>
 *     <li>{@link #isUsable()} - supported and the ordinary backend has not
 *     tripped its linkage circuit breaker.</li>
 * </ul>
 *
 * <p>Linked storage is a separate generation-isolated capability owned by
 * {@code SophisticatedLinkedStorageCompat}; ordinary backpack support stays
 * available when that backend is absent or ABI-broken.
 */
public final class SophisticatedBackpacksCompat {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String MOD_ID =
            SophisticatedBackpackGeneration.BACKPACKS_MOD_ID;

    private static volatile boolean linkageBroken;
    private static final AtomicBoolean LINKAGE_WARNING_LOGGED =
            new AtomicBoolean();

    private SophisticatedBackpacksCompat() {
    }

    public static boolean isInstalled() {
        ModList modList = ModList.get();
        return modList != null && modList.isLoaded(MOD_ID);
    }

    /** Verified ordinary-backpack API generation. */
    public static boolean isSupported() {
        return SophisticatedLinkedStorageCompat.generation()
                .ordinaryBackpacksSupported();
    }

    /**
     * Supported generation with a healthy ordinary backend.
     *
     * <p>This - not {@link #isInstalled()} - is the only valid gate before
     * calling the Sophisticated backend.
     */
    public static boolean isUsable() {
        return isInstalled() && isSupported() && !linkageBroken;
    }

    public static boolean visitInventoryBackpacks(
            Player player,
            Predicate<IItemHandler> visitor
    ) {
        if (!isUsable() || player == null) {
            return false;
        }
        try {
            return SophisticatedBackpacksCompatInner
                    .visitInventoryBackpacks(player, visitor);
        } catch (LinkageError linkageError) {
            breakLinkage(linkageError);
            return false;
        }
    }

    public static List<CraftingItemSource> resolveRefitSources(ServerPlayer player) {
        if (!isUsable() || player == null) {
            return List.of();
        }
        try {
            return SophisticatedBackpacksCompatInner.resolveRefitSources(player);
        } catch (LinkageError linkageError) {
            breakLinkage(linkageError);
            return List.of();
        }
    }

    public static boolean mutateInventoryBackpacks(
            ServerPlayer player,
            Predicate<IItemHandler> visitor
    ) {
        if (!isUsable() || player == null) {
            return false;
        }

        try {
            return SophisticatedBackpacksCompatInner
                    .mutateInventoryBackpacks(player, visitor);
        } catch (LinkageError linkageError) {
            /*
             * A mutation visitor may already have changed one or more slots.
             *
             * Record the integration as broken for future requests, but propagate
             * this failure to the current consumption operation. Returning false
             * here would incorrectly mean "consumed nothing, continue with the
             * next source", which can double-consume ammunition.
             */
            breakLinkage(linkageError);
            throw linkageError;
        }
    }

    public static void syncAllBackpack(Player player) {
        if (!isUsable() || player == null) {
            return;
        }
        try {
            SophisticatedBackpacksCompatInner.syncAllBackpack(player);
        } catch (LinkageError linkageError) {
            breakLinkage(linkageError);
        }
    }

    public static void refreshBackpackSnapshots(
            Player player
    ) {
        if (!isUsable()
                || player == null) {
            return;
        }

        try {
            SophisticatedBackpacksCompatInner
                    .refreshBackpackSnapshots(
                            player
                    );
        } catch (LinkageError linkageError) {
            /*
             * This means the stable ordinary Sophisticated API itself failed.
             * Fail through the existing global circuit breaker.
             *
             * 3.26-only linked failures are already contained inside
             * SophisticatedLinkedStorageCompat and never reach here.
             */
            breakLinkage(linkageError);
        }
    }

    private static void breakLinkage(LinkageError linkageError) {
        linkageBroken = true;
        if (LINKAGE_WARNING_LOGGED.compareAndSet(false, true)) {
            LOGGER.warn(
                    "[TACZ-addon] Sophisticated Backpacks API is unavailable; "
                            + "backpack integration disabled for this session",
                    linkageError
            );
        }
    }
}
