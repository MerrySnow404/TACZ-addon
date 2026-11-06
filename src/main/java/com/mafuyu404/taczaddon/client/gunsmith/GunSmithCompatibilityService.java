package com.mafuyu404.taczaddon.client.gunsmith;

import com.mafuyu404.taczaddon.client.gunsmith.BetterGunSmithTable;
import com.mafuyu404.taczaddon.client.gunsmith.GunSmithDisplayInventory;
import com.mafuyu404.taczaddon.compat.tacz.api.TaczGunSmithScreenAccess;

import com.tacz.guns.crafting.GunSmithTableIngredient;
import com.tacz.guns.crafting.GunSmithTableRecipe;
import it.unimi.dsi.fastutil.ints.Int2IntArrayMap;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import javax.annotation.Nullable;

@OnlyIn(Dist.CLIENT)
public final class GunSmithCompatibilityService {
    private GunSmithCompatibilityService() {
    }

    public static Int2IntArrayMap computeCombinedIngredientCounts(
            GunSmithTableRecipe recipe,
            Inventory playerInventory,
            List<ItemStack> externalStacks
    ) {
        Int2IntArrayMap counts = new Int2IntArrayMap(
                recipe.getInputs().size()
        );
        Inventory displayInventory = new GunSmithDisplayInventory(
                playerInventory.player,
                externalStacks
        );

        for (int index = 0;
             index < recipe.getInputs().size();
             index++) {
            GunSmithTableIngredient input =
                    recipe.getInputs().get(index);
            int count = 0;
            for (ItemStack stack : displayInventory.items) {
                if (!stack.isEmpty()
                        && input.getIngredient().test(stack)) {
                    count += stack.getCount();
                }
            }
            counts.put(index, count);
        }
        return counts;
    }

    public static void saveBrowseState(
            TaczGunSmithScreenAccess access,
            @Nullable ResourceLocation tableId
    ) {
        if (tableId == null) {
            return;
        }
        GunSmithTableRecipe recipe =
                access.taczaddon$getSelectedRecipe();
        ResourceLocation recipeId = recipe == null
                ? null
                : recipe.getId();
        int attachmentPropertyIndex =
                GunSmithPropertyFilter.supportsWorkbench(tableId)
                        && access instanceof GunSmithPropertyFilterAccess filter
                        ? filter.taczaddon$getAttachmentPropertyIndex()
                        : 0;
        BetterGunSmithTable.saveBrowseState(
                tableId,
                access.taczaddon$getSelectedType(),
                recipeId,
                access.taczaddon$getTypePage(),
                access.taczaddon$getIndexPage(),
                attachmentPropertyIndex
        );
    }

    /** Called at init HEAD, before TaCZ classifies recipes. */
    public static void restorePropertyFilter(GunSmithPropertyFilterAccess access,
                                             @Nullable ResourceLocation tableId) {
        if (!GunSmithPropertyFilter.supportsWorkbench(tableId)) {
            access.taczaddon$setAttachmentPropertyIndex(0);
            return;
        }
        BetterGunSmithTable.getBrowseState(tableId).ifPresent(state ->
                access.taczaddon$setAttachmentPropertyIndex(clamp(state.attachmentPropIndex(),
                        0, Math.max(0, access.taczaddon$getAttachmentPropertyCount() - 1))));
    }

    public static boolean restoreBrowseState(
            TaczGunSmithScreenAccess access,
            @Nullable ResourceLocation tableId,
            Map<ResourceLocation, List<ResourceLocation>> recipes,
            LinkedHashMap<ResourceLocation, ?> recipeKeys,
            Function<ResourceLocation, GunSmithTableRecipe> resolver
    ) {
        BetterGunSmithTable.BrowseState state =
                tableId == null
                        ? null
                        : BetterGunSmithTable.getBrowseState(tableId)
                        .orElse(null);
        if (state == null) {
            return false;
        }

        ResourceLocation savedType = state.selectedType();
        List<ResourceLocation> savedTypeRecipeList =
                savedType == null ? null : recipes.get(savedType);
        if (savedTypeRecipeList != null) {
            access.taczaddon$setSelectedType(savedType);
            access.taczaddon$setSelectedRecipeList(
                    savedTypeRecipeList
            );
        }

        int maxTypePage = recipeKeys.isEmpty()
                ? 0
                : (recipeKeys.size() - 1) / 7;
        access.taczaddon$setTypePage(
                clamp(state.typePage(), 0, maxTypePage)
        );

        List<ResourceLocation> selectedRecipeList =
                access.taczaddon$getSelectedRecipeList();
        if (selectedRecipeList == null
                || selectedRecipeList.isEmpty()) {
            access.taczaddon$setIndexPage(0);
            access.taczaddon$setSelectedRecipe(null);
            return true;
        }

        int maxRecipePage =
                (selectedRecipeList.size() - 1) / 6;
        int indexPage = clamp(
                state.indexPage(),
                0,
                maxRecipePage
        );
        access.taczaddon$setIndexPage(indexPage);

        ResourceLocation savedRecipeId =
                state.selectedRecipeId();
        if (savedRecipeId != null
                && selectedRecipeList.contains(savedRecipeId)) {
            GunSmithTableRecipe restoredRecipe =
                    resolver.apply(savedRecipeId);
            if (restoredRecipe != null) {
                access.taczaddon$setSelectedRecipe(
                        restoredRecipe
                );
                return true;
            }
        }

        int fallbackIndex = Math.min(
                indexPage * 6,
                selectedRecipeList.size() - 1
        );
        GunSmithTableRecipe fallbackRecipe =
                resolver.apply(
                        selectedRecipeList.get(fallbackIndex)
                );
        if (fallbackRecipe == null) {
            for (ResourceLocation recipeId : selectedRecipeList) {
                fallbackRecipe = resolver.apply(recipeId);
                if (fallbackRecipe != null) {
                    break;
                }
            }
        }
        access.taczaddon$setSelectedRecipe(fallbackRecipe);
        return true;
    }

    private static int clamp(
            int value,
            int minimum,
            int maximum
    ) {
        return Math.max(minimum, Math.min(maximum, value));
    }
}
