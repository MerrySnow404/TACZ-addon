package com.mafuyu404.taczaddon.mixin.tacz.v1_1_8;

import com.mafuyu404.taczaddon.compat.tacz.api.TaczGunSmithScreenAccess;

import com.tacz.guns.client.gui.GunSmithTableScreen;
import com.tacz.guns.crafting.GunSmithTableRecipe;
import it.unimi.dsi.fastutil.ints.Int2IntArrayMap;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import java.util.List;
import javax.annotation.Nullable;

/**
 * Version-specific structural access to TaCZ GunSmithTableScreen state.
 *
 * This Mixin deliberately contains no browse, source-view, or crafting
 * behavior. Other gunsmith adapters depend on this binding through
 * TaczFeature.GUNSMITH_SCREEN_ACCESS.
 */
@Mixin(value = GunSmithTableScreen.class, remap = false)
public abstract class GunSmithTableScreenAccessMixin
        implements TaczGunSmithScreenAccess {

    @Shadow
    @Nullable
    private GunSmithTableRecipe selectedRecipe;

    @Shadow
    private List<ResourceLocation> selectedRecipeList;

    @Shadow
    @Nullable
    private ResourceLocation selectedType;

    @Shadow
    private int indexPage;

    @Shadow
    private int typePage;

    @Shadow
    private Int2IntArrayMap playerIngredientCount;

    @Override
    @Nullable
    public GunSmithTableRecipe taczaddon$getSelectedRecipe() {
        return this.selectedRecipe;
    }

    @Override
    public void taczaddon$setSelectedRecipe(
            @Nullable GunSmithTableRecipe recipe
    ) {
        this.selectedRecipe = recipe;
    }

    @Override
    public List<ResourceLocation> taczaddon$getSelectedRecipeList() {
        return this.selectedRecipeList;
    }

    @Override
    public void taczaddon$setSelectedRecipeList(
            List<ResourceLocation> recipeList
    ) {
        this.selectedRecipeList = recipeList;
    }

    @Override
    @Nullable
    public ResourceLocation taczaddon$getSelectedType() {
        return this.selectedType;
    }

    @Override
    public void taczaddon$setSelectedType(
            @Nullable ResourceLocation selectedType
    ) {
        this.selectedType = selectedType;
    }

    @Override
    public int taczaddon$getIndexPage() {
        return this.indexPage;
    }

    @Override
    public void taczaddon$setIndexPage(int indexPage) {
        this.indexPage = indexPage;
    }

    @Override
    public int taczaddon$getTypePage() {
        return this.typePage;
    }

    @Override
    public void taczaddon$setTypePage(int typePage) {
        this.typePage = typePage;
    }

    @Override
    public Int2IntArrayMap taczaddon$getPlayerIngredientCount() {
        return this.playerIngredientCount;
    }

    @Override
    public void taczaddon$setPlayerIngredientCount(
            Int2IntArrayMap playerIngredientCount
    ) {
        this.playerIngredientCount = playerIngredientCount;
    }
}
