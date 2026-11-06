package com.mafuyu404.taczaddon.gunsmith.client;

import static org.junit.jupiter.api.Assertions.*;

import com.tacz.guns.crafting.GunSmithTableRecipe;
import com.tacz.guns.crafting.result.GunSmithTableResult;
import com.tacz.guns.inventory.GunSmithTableMenu;
import com.tacz.guns.resource.pojo.data.block.TabConfig;

import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.RecipeHolder;

import org.junit.jupiter.api.Test;

import java.util.*;

class GunSmithScreenControllerTest {
    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("test", path);
    }

    private static final class Host implements GunSmithScreenController.Host {
        RecipeHolder<GunSmithTableRecipe> selected;
        List<ResourceLocation> list = List.of();
        ResourceLocation type;
        int page, typePage, recounts;
        Map<ResourceLocation, List<ResourceLocation>> recipes = new HashMap<>();
        LinkedHashMap<ResourceLocation, TabConfig> keys = new LinkedHashMap<>();

        public RecipeHolder<GunSmithTableRecipe> taczaddon$getSelectedRecipe() {
            return selected;
        }

        public void taczaddon$setSelectedRecipe(RecipeHolder<GunSmithTableRecipe> value) {
            selected = value;
        }

        public List<ResourceLocation> taczaddon$getSelectedRecipeList() {
            return list;
        }

        public void taczaddon$setSelectedRecipeList(List<ResourceLocation> value) {
            list = value;
        }

        public int taczaddon$getIndexPage() {
            return page;
        }

        public void taczaddon$setIndexPage(int value) {
            page = value;
        }

        public ResourceLocation taczaddon$getSelectedType() {
            return type;
        }

        public void taczaddon$setSelectedType(ResourceLocation value) {
            type = value;
        }

        public Map<ResourceLocation, List<ResourceLocation>> taczaddon$getRecipes() {
            return recipes;
        }

        public LinkedHashMap<ResourceLocation, TabConfig> taczaddon$getRecipeKeys() {
            return keys;
        }

        public int taczaddon$getTypePage() {
            return typePage;
        }

        public void taczaddon$setTypePage(int value) {
            typePage = value;
        }

        public GunSmithTableMenu taczaddon$getMenu() {
            return null;
        }

        public int taczaddon$getLeftPos() {
            return 0;
        }

        public int taczaddon$getTopPos() {
            return 0;
        }

        public void taczaddon$addWidget(AbstractWidget widget) {}

        public RecipeHolder<GunSmithTableRecipe> taczaddon$getSelectedRecipe(
                ResourceLocation recipeId) {
            return new RecipeHolder<>(
                    recipeId, new GunSmithTableRecipe((GunSmithTableResult) null, List.of()));
        }

        public void taczaddon$getPlayerIngredientCount(RecipeHolder<GunSmithTableRecipe> recipe) {
            recounts++;
        }

        public void taczaddon$updateIngredientCount() {}
    }

    private GunSmithScreenController controller(Host host) {
        var controller = new GunSmithScreenController(host);
        controller.initFields();
        return controller;
    }

    @Test
    void savedRecipeDeterminesPageAfterFiltering() {
        Host host = new Host();
        host.list =
                java.util.stream.IntStream.range(0, 14).mapToObj(i -> id("recipe_" + i)).toList();
        host.type = id("attachments");
        host.recipes.put(host.type, host.list);
        host.keys.put(host.type, null);
        controller(host)
                .applyBrowseState(
                        new GunSmithBrowseState.BrowseState(
                                id("table"), host.type, host.list.get(8), 99, 99, 0));
        assertEquals(1, host.page);
        assertEquals(host.list.get(8), host.selected.id());
        assertEquals(0, host.typePage);
        assertEquals(1, host.recounts);
    }

    @Test
    void removedRecipeClampsToLastAvailablePage() {
        Host host = new Host();
        host.list =
                java.util.stream.IntStream.range(0, 7).mapToObj(i -> id("recipe_" + i)).toList();
        controller(host)
                .applyBrowseState(
                        new GunSmithBrowseState.BrowseState(
                                id("table"), null, id("removed"), 99, 99, 0));
        assertEquals(1, host.page);
        assertEquals(host.list.get(6), host.selected.id());
    }

    @Test
    void emptyFilterClearsSelectionAndConsumesOnlyRecipeAreaScroll() {
        Host host = new Host();
        host.page = 3;
        var controller = controller(host);
        controller.applyBrowseState(
                new GunSmithBrowseState.BrowseState(id("table"), null, null, 99, 99, 0));
        assertEquals(0, host.page);
        assertNull(host.selected);
        assertTrue(controller.guardMouseScrolledWhenNoRecipes(150, 70, 0, 1));
        assertFalse(controller.guardMouseScrolledWhenNoRecipes(10, 10, 0, 1));
        assertEquals(0, host.recounts);
    }
}
