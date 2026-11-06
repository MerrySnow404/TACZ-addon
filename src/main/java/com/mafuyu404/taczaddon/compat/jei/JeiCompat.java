package com.mafuyu404.taczaddon.compat.jei;

import com.mafuyu404.taczaddon.compat.api.ApiShapeProbe;
import com.mafuyu404.taczaddon.compat.api.OptionalAbiStatus;

import com.mojang.logging.LogUtils;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.fml.ModList;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;

/**
 * Optional boundary: no JEI types are resolved until a verified backend is
 * used.
 *
 * <p>{@link #isSupported()} verifies the JEI runtime shape the recipe bridge
 * links against ({@code IJeiRuntime#getIngredientManager},
 * {@code getRecipesGui}, {@code getJeiHelpers}); an unknown JEI ABI therefore
 * disables only recipe navigation.
 */
@OnlyIn(Dist.CLIENT)
public final class JeiCompat {
    private static final String MOD_ID = "jei";
    private static final String JEI_RUNTIME =
            "mezz.jei.api.runtime.IJeiRuntime";
    private static boolean installed;
    private static volatile boolean linkageBroken;
    private static volatile OptionalAbiStatus status;
    private static final AtomicBoolean LINKAGE_WARNING_LOGGED = new AtomicBoolean();

    private JeiCompat() {
    }

    public static void init() {
        ModList mods = ModList.get();
        installed = mods != null && mods.isLoaded(MOD_ID);
    }

    public static boolean isInstalled() {
        return installed;
    }

    public static OptionalAbiStatus status() {
        OptionalAbiStatus current = status;
        if (current != null) {
            return current;
        }
        ApiShapeProbe.ClassBytes source =
                ApiShapeProbe.sourceFor(JeiCompat.class);
        OptionalAbiStatus resolved = OptionalAbiStatus.of(
                ApiShapeProbe.hasClass(source, JEI_RUNTIME),
                ApiShapeProbe.hasMethod(
                        source,
                        JEI_RUNTIME,
                        "getIngredientManager",
                        "()Lmezz/jei/api/runtime/IIngredientManager;"
                ) && ApiShapeProbe.hasMethod(
                        source,
                        JEI_RUNTIME,
                        "getRecipesGui",
                        "()Lmezz/jei/api/runtime/IRecipesGui;"
                ) && ApiShapeProbe.hasMethod(
                        source,
                        JEI_RUNTIME,
                        "getJeiHelpers",
                        "()Lmezz/jei/api/helpers/IJeiHelpers;"
                )
        );
        status = resolved;
        return resolved;
    }

    public static boolean isSupported() {
        return status().supported();
    }

    public static boolean isUsable() {
        return installed && isSupported() && !linkageBroken;
    }

    public static boolean showRecipes(ItemStack itemStack) {
        if (!isUsable() || itemStack == null || itemStack.isEmpty()) {
            return false;
        }
        return runGuarded(() -> JeiPlugin.showRecipes(itemStack));
    }

    static boolean runGuarded(BooleanSupplier operation) {
        if (linkageBroken) {
            return false;
        }
        try {
            return operation.getAsBoolean();
        } catch (LinkageError error) {
            linkageBroken = true;
            if (LINKAGE_WARNING_LOGGED.compareAndSet(false, true)) {
                LogUtils.getLogger().warn(
                        "[TACZ-addon] JEI API unavailable; recipe navigation disabled for this session", error);
            }
            return false;
        }
    }
}
