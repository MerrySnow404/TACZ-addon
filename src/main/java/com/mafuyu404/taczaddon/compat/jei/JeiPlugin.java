package com.mafuyu404.taczaddon.compat.jei;

import com.mafuyu404.taczaddon.ModConstants;

import mezz.jei.api.IModPlugin;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;

@OnlyIn(Dist.CLIENT)
@mezz.jei.api.JeiPlugin
public class JeiPlugin implements IModPlugin {
    private static IJeiRuntime jeiRuntime;

    @Override
    public @NotNull ResourceLocation getPluginUid() {
        return ResourceLocation.fromNamespaceAndPath(
                ModConstants.MODID,
                "jei_plugin"
        );
    }

    @Override
    public void onRuntimeAvailable(@NotNull IJeiRuntime jeiRuntime) {
        JeiPlugin.jeiRuntime = jeiRuntime;
    }

    @Override
    public void onRuntimeUnavailable() {
        JeiPlugin.jeiRuntime = null;
    }

    public static Optional<IJeiRuntime> getJeiRuntime() {
        return Optional.ofNullable(jeiRuntime);
    }

    public static boolean showRecipes(ItemStack itemStack) {
        final boolean[] result = {false};
        JeiPlugin.getJeiRuntime().ifPresent(jeiRuntime -> {
            jeiRuntime.getIngredientManager().getIngredientTypeChecked(itemStack)
                .ifPresent(type -> {
                    jeiRuntime.getRecipesGui().show(
                        jeiRuntime.getJeiHelpers().getFocusFactory().createFocus(RecipeIngredientRole.OUTPUT, type, itemStack)
                    );
                    result[0] = true;
                });
        });
        return result[0];
    }
}
