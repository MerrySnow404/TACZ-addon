package com.mafuyu404.taczaddon.gunsmith;

import com.tacz.guns.crafting.GunSmithTableRecipe;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.RecipeManager;

import javax.annotation.Nullable;

public interface GunSmithMenuAccess {

    @Nullable
    GunSmithTableRecipe taczaddon$invokeGetRecipe(
            ResourceLocation recipeId,
            RecipeManager recipeManager
    );
}
