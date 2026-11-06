package com.mafuyu404.taczaddon.compat.tacz.api;

import com.tacz.guns.crafting.GunSmithTableRecipe;
import it.unimi.dsi.fastutil.ints.Int2IntArrayMap;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import javax.annotation.Nullable;

public interface TaczGunSmithScreenAccess {
    @Nullable
    GunSmithTableRecipe taczaddon$getSelectedRecipe();

    void taczaddon$setSelectedRecipe(
            @Nullable GunSmithTableRecipe recipe
    );

    List<ResourceLocation> taczaddon$getSelectedRecipeList();

    void taczaddon$setSelectedRecipeList(
            List<ResourceLocation> recipeList
    );

    @Nullable
    ResourceLocation taczaddon$getSelectedType();

    void taczaddon$setSelectedType(
            @Nullable ResourceLocation selectedType
    );

    int taczaddon$getIndexPage();

    void taczaddon$setIndexPage(int indexPage);

    int taczaddon$getTypePage();

    void taczaddon$setTypePage(int typePage);

    Int2IntArrayMap taczaddon$getPlayerIngredientCount();

    void taczaddon$setPlayerIngredientCount(
            Int2IntArrayMap playerIngredientCount
    );
}
