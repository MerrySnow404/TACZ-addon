package com.mafuyu404.taczaddon.gunsmith;

import com.tacz.guns.crafting.GunSmithTableRecipe;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.RecipeManager;

/** Recipe lookup supplied by the TaCZ menu adapter. */
public interface GunSmithRecipeAccess {
    GunSmithTableRecipe taczaddon$invokeGetRecipe(ResourceLocation id, RecipeManager manager);
}
