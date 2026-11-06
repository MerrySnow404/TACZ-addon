package com.mafuyu404.taczaddon.compat.curios;

import com.mafuyu404.taczaddon.compat.api.ApiShapeProbe;
import com.mafuyu404.taczaddon.compat.api.OptionalAbiStatus;

import com.mojang.logging.LogUtils;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.items.IItemHandler;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;

/**
 * Optional Forge Curios boundary.
 *
 * <p>Read-only queries may safely degrade to "not available". Mutation calls
 * must propagate linkage failure because an external handler may already have
 * changed state before throwing.
 *
 * <p>Installed, supported and usable are separate. The supported generation is
 * the verified Curios 5.x 1.20.1 API (dev baseline 5.14.1):
 * {@code CuriosApi.getCuriosInventory(LivingEntity) -> LazyOptional},
 * {@code ICuriosItemHandler.getCurios() -> Map} and
 * {@code ICurioStacksHandler.getStacks()}. Nothing else is ever called, so an
 * unknown Curios ABI disables only the Curios ammo source.
 */
public final class CuriosCompat {
    private static final String MOD_ID = "curios";
    private static final String CURIO_API =
            "top.theillusivec4.curios.api.CuriosApi";
    private static final String ITEM_HANDLER =
            "top.theillusivec4.curios.api.type.capability.ICuriosItemHandler";
    private static final String STACKS_HANDLER =
            "top.theillusivec4.curios.api.type.inventory.ICurioStacksHandler";
    private static final String DYNAMIC_STACK_HANDLER =
            "top/theillusivec4/curios/api/type/inventory/IDynamicStackHandler";

    private static volatile boolean linkageBroken;
    private static volatile OptionalAbiStatus status;

    private static final AtomicBoolean LINKAGE_WARNING_LOGGED =
            new AtomicBoolean();

    private CuriosCompat() {
    }

    public static boolean isInstalled() {
        ModList mods = ModList.get();
        return mods != null && mods.isLoaded(MOD_ID);
    }

    /** Verified Curios 5.x 1.20.1 API generation. */
    public static OptionalAbiStatus status() {
        OptionalAbiStatus current = status;
        if (current != null) {
            return current;
        }
        OptionalAbiStatus resolved = OptionalAbiStatus.of(
                ApiShapeProbe.hasClass(
                        ApiShapeProbe.sourceFor(CuriosCompat.class),
                        CURIO_API
                ),
                ApiShapeProbe.satisfies(
                        ApiShapeProbe.sourceFor(CuriosCompat.class),
                        CURIO_API,
                        List.of(ApiShapeProbe.method(
                                "getCuriosInventory",
                                "(Lnet/minecraft/world/entity/LivingEntity;)"
                                        + "Lnet/minecraftforge/common/util/"
                                        + "LazyOptional;"
                        ))
                ) && ApiShapeProbe.satisfies(
                        ApiShapeProbe.sourceFor(CuriosCompat.class),
                        ITEM_HANDLER,
                        List.of(ApiShapeProbe.method(
                                "getCurios",
                                "()Ljava/util/Map;"
                        ))
                ) && ApiShapeProbe.satisfies(
                        ApiShapeProbe.sourceFor(CuriosCompat.class),
                        STACKS_HANDLER,
                        List.of(ApiShapeProbe.method(
                                "getStacks",
                                "()L" + DYNAMIC_STACK_HANDLER + ";"
                        ))
                )
        );
        status = resolved;
        return resolved;
    }

    public static boolean isSupported() {
        return status().supported();
    }

    /**
     * The only valid gate before calling the Curios backend.
     */
    public static boolean isUsable() {
        return isInstalled() && isSupported() && !linkageBroken;
    }

    /**
     * Read-only traversal.
     *
     * Linkage failure is equivalent to this integration being unavailable;
     * no inventory mutation needs to be accounted for.
     */
    public static boolean visitHandlers(
            Player player,
            Predicate<IItemHandler> visitor
    ) {
        if (player == null || !isUsable()) {
            return false;
        }

        return runGuarded(
                () -> CuriosCompatInner.visitHandlers(
                        player,
                        visitor
                )
        );
    }

    /**
     * Mutation traversal.
     *
     * Never convert a linkage failure into normal false. The visitor may
     * already have committed part of an extraction, so the current ammo
     * request must stop instead of falling through to another source.
     */
    public static boolean mutateHandlers(
            Player player,
            Predicate<IItemHandler> visitor
    ) {
        if (player == null || !isUsable()) {
            return false;
        }

        try {
            return CuriosCompatInner.visitHandlers(
                    player,
                    visitor
            );
        } catch (LinkageError error) {
            breakLinkage(error);
            throw error;
        }
    }

    static boolean runGuarded(BooleanSupplier operation) {
        if (linkageBroken) {
            return false;
        }

        try {
            return operation.getAsBoolean();
        } catch (LinkageError error) {
            breakLinkage(error);
            return false;
        }
    }

    private static void breakLinkage(LinkageError error) {
        linkageBroken = true;

        if (LINKAGE_WARNING_LOGGED.compareAndSet(false, true)) {
            LogUtils.getLogger().warn(
                    "[TACZ-addon] Curios API unavailable; "
                            + "Curios ammo disabled for this session",
                    error
            );
        }
    }
}
