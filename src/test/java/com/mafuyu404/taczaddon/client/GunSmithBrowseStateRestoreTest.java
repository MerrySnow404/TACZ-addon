package com.mafuyu404.taczaddon.client;

import com.mafuyu404.taczaddon.client.gunsmith.GunSmithCompatibilityService;
import com.mafuyu404.taczaddon.client.gunsmith.GunSmithPropertyFilterAccess;

import com.mafuyu404.taczaddon.client.gunsmith.BetterGunSmithTable;
import com.mafuyu404.taczaddon.compat.tacz.api.TaczGunSmithScreenAccess;
import com.tacz.guns.crafting.GunSmithTableRecipe;
import it.unimi.dsi.fastutil.ints.Int2IntArrayMap;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class GunSmithBrowseStateRestoreTest {
    private static final ResourceLocation TABLE =
            new ResourceLocation("test", "table");
    private static final ResourceLocation ATTACHMENT_TABLE =
            new ResourceLocation(
                    "tacz",
                    "attachment_workbench"
            );
    private static final ResourceLocation AMMO_TABLE =
            new ResourceLocation(
                    "tacz",
                    "ammo_workbench"
            );
    private static final ResourceLocation GUN_SMITH_TABLE =
            new ResourceLocation(
                    "tacz",
                    "gun_smith_table"
            );
    private static final ResourceLocation TYPE_0 =
            new ResourceLocation("test", "type_0");

    @BeforeEach
    void clearBefore() {
        BetterGunSmithTable.clearAllBrowseStates();
    }

    @AfterEach
    void clearAfter() {
        BetterGunSmithTable.clearAllBrowseStates();
    }

    @Test
    void indexPageIsIndependentFromSelectedRecipe() {
        List<ResourceLocation> recipeIds = recipeIds(18);
        List<GunSmithTableRecipe> recipes = recipeObjects(18);
        Map<ResourceLocation, GunSmithTableRecipe> byId =
                recipeById(recipes);

        BetterGunSmithTable.saveBrowseState(
                TABLE,
                TYPE_0,
                recipeIds.get(1),
                0,
                2
        );

        FakeAccess access = accessForType(recipeIds);
        assertTrue(restore(access, recipeIds, byId));

        assertNotNull(access.selectedRecipe);
        assertEquals(
                recipes.get(1).getId(),
                access.selectedRecipe.getId()
        );
        assertEquals(2, access.indexPage);
    }

    @Test
    void typePageIsIndependentFromSelectedType() {
        List<ResourceLocation> recipeIds = recipeIds(6);
        List<GunSmithTableRecipe> recipes = recipeObjects(6);
        Map<ResourceLocation, GunSmithTableRecipe> byId =
                recipeById(recipes);

        BetterGunSmithTable.saveBrowseState(
                TABLE,
                TYPE_0,
                recipeIds.get(0),
                1,
                0
        );

        FakeAccess access = accessForType(recipeIds);
        assertTrue(restore(access, recipeIds, byId));

        assertEquals(TYPE_0, access.selectedType);
        assertEquals(1, access.typePage);
        assertEquals(0, access.indexPage);
    }

    @Test
    void invalidIndexPageIsClampedToMaximum() {
        List<ResourceLocation> recipeIds = recipeIds(18);
        List<GunSmithTableRecipe> recipes = recipeObjects(18);
        Map<ResourceLocation, GunSmithTableRecipe> byId =
                recipeById(recipes);

        BetterGunSmithTable.saveBrowseState(
                TABLE,
                TYPE_0,
                null,
                0,
                99
        );

        FakeAccess access = accessForType(recipeIds);
        assertTrue(restore(access, recipeIds, byId));

        assertEquals(2, access.indexPage);
        assertEquals(recipes.get(12).getId(), access.selectedRecipe.getId());
    }

    @Test
    void invalidTypePageIsClampedToMaximum() {
        List<ResourceLocation> recipeIds = recipeIds(6);
        List<GunSmithTableRecipe> recipes = recipeObjects(6);
        Map<ResourceLocation, GunSmithTableRecipe> byId =
                recipeById(recipes);

        BetterGunSmithTable.saveBrowseState(
                TABLE,
                TYPE_0,
                null,
                99,
                0
        );

        FakeAccess access = accessForType(recipeIds);
        assertTrue(restore(access, recipeIds, byId));

        assertEquals(1, access.typePage);
        assertEquals(0, access.indexPage);
    }

    @Test
    void invalidRecipePreservesValidIndexPage() {
        List<ResourceLocation> recipeIds = recipeIds(18);
        List<GunSmithTableRecipe> recipes = recipeObjects(18);
        Map<ResourceLocation, GunSmithTableRecipe> byId =
                recipeById(recipes);

        BetterGunSmithTable.saveBrowseState(
                TABLE,
                TYPE_0,
                new ResourceLocation("test", "missing"),
                0,
                2
        );

        FakeAccess access = accessForType(recipeIds);
        assertTrue(restore(access, recipeIds, byId));

        assertEquals(2, access.indexPage);
        assertEquals(recipes.get(12).getId(), access.selectedRecipe.getId());
    }

    @Test
    void missingSavedTypeKeepsTaCzCurrentTypeAndPages() {
        List<ResourceLocation> recipeIds = recipeIds(18);
        List<GunSmithTableRecipe> recipes = recipeObjects(18);
        Map<ResourceLocation, GunSmithTableRecipe> byId =
                recipeById(recipes);

        BetterGunSmithTable.saveBrowseState(
                TABLE,
                new ResourceLocation("test", "missing_type"),
                null,
                1,
                2
        );

        FakeAccess access = accessForType(recipeIds);
        assertTrue(restore(access, recipeIds, byId));

        assertEquals(TYPE_0, access.selectedType);
        assertEquals(1, access.typePage);
        assertEquals(2, access.indexPage);
    }

    @Test
    void filterRestoresBeforeClassificationAndSaveCarriesAllFiveFields() {
        BetterGunSmithTable.saveBrowseState(
                ATTACHMENT_TABLE,
                TYPE_0,
                null,
                2,
                3,
                99
        );
        FakeAccess access = new FakeAccess();
        GunSmithCompatibilityService.restorePropertyFilter(
                access,
                ATTACHMENT_TABLE
        );
        assertEquals(3, access.propertyIndex); // Catalog has four entries.
        access.selectedType = TYPE_0;
        access.typePage = 2;
        access.indexPage = 3;
        GunSmithCompatibilityService.saveBrowseState(
                access,
                ATTACHMENT_TABLE
        );
        var saved = BetterGunSmithTable.getBrowseState(
                ATTACHMENT_TABLE
        ).orElseThrow();
        assertEquals(3, saved.attachmentPropIndex());
        assertEquals(2, saved.typePage());
        assertEquals(3, saved.indexPage());
        assertEquals(TYPE_0, saved.selectedType());
    }

    @Test
    void nonAttachmentWorkbenchesSaveAndRestoreANeutralPropertyIndex() {
        for (ResourceLocation tableId : List.of(
                AMMO_TABLE,
                GUN_SMITH_TABLE
        )) {
            BetterGunSmithTable.saveBrowseState(
                    tableId,
                    TYPE_0,
                    null,
                    1,
                    2,
                    99
            );

            FakeAccess access = new FakeAccess();
            access.propertyIndex = 99;
            GunSmithCompatibilityService.restorePropertyFilter(
                    access,
                    tableId
            );
            assertEquals(
                    0,
                    access.propertyIndex,
                    tableId + " must not restore a property filter"
            );

            access.propertyIndex = 99;
            access.selectedType = TYPE_0;
            access.typePage = 1;
            access.indexPage = 2;
            GunSmithCompatibilityService.saveBrowseState(
                    access,
                    tableId
            );
            var saved = BetterGunSmithTable.getBrowseState(
                    tableId
            ).orElseThrow();
            assertEquals(
                    0,
                    saved.attachmentPropIndex(),
                    tableId + " must save a neutral property index"
            );
            assertEquals(
                    TYPE_0,
                    saved.selectedType(),
                    "generic browse memory must remain available"
            );
            assertEquals(
                    1,
                    saved.typePage(),
                    "generic browse memory must remain available"
            );
            assertEquals(
                    2,
                    saved.indexPage(),
                    "generic browse memory must remain available"
            );
        }
    }

    @Test
    void memoryIsNullSafeClampedAndBoundedByAccessOrder() {
        BetterGunSmithTable.saveBrowseState(null, null, null, 1, 1, 1);
        assertTrue(BetterGunSmithTable.getBrowseState(null).isEmpty());
        for (int i = 0; i < 64; i++) BetterGunSmithTable.saveBrowseState(
                ResourceLocation.tryParse("test:table_" + i), null, null, -2, -3, -4);
        ResourceLocation oldest = ResourceLocation.tryParse("test:table_0");
        var state = BetterGunSmithTable.getBrowseState(oldest).orElseThrow();
        assertEquals(0, state.typePage()); assertEquals(0, state.indexPage()); assertEquals(0, state.attachmentPropIndex());
        BetterGunSmithTable.saveBrowseState(TABLE, null, null, 0, 0, 0);
        assertTrue(BetterGunSmithTable.getBrowseState(oldest).isPresent());
        assertFalse(BetterGunSmithTable.getBrowseState(ResourceLocation.tryParse("test:table_1")).isPresent());
    }

    private boolean restore(
            FakeAccess access,
            List<ResourceLocation> recipeIds,
            Map<ResourceLocation, GunSmithTableRecipe> byId
    ) {
        return GunSmithCompatibilityService.restoreBrowseState(
                access,
                TABLE,
                Map.of(TYPE_0, recipeIds),
                recipeKeys(),
                byId::get
        );
    }

    private static FakeAccess accessForType(
            List<ResourceLocation> recipeIds
    ) {
        FakeAccess access = new FakeAccess();
        access.selectedType = TYPE_0;
        access.selectedRecipeList = recipeIds;
        return access;
    }

    private static LinkedHashMap<ResourceLocation, Object> recipeKeys() {
        LinkedHashMap<ResourceLocation, Object> keys =
                new LinkedHashMap<>();
        for (int index = 0; index < 14; index++) {
            keys.put(
                    new ResourceLocation(
                            "test",
                            "type_" + index
                    ),
                    new Object()
            );
        }
        return keys;
    }

    private static List<ResourceLocation> recipeIds(int count) {
        ArrayList<ResourceLocation> ids = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            ids.add(new ResourceLocation(
                    "test",
                    "recipe_" + index
            ));
        }
        return ids;
    }

    private static List<GunSmithTableRecipe> recipeObjects(int count) {
        ArrayList<GunSmithTableRecipe> recipes =
                new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            recipes.add(new GunSmithTableRecipe(
                    new ResourceLocation(
                            "test",
                            "recipe_" + index
                    ),
                    null,
                    List.of()
            ));
        }
        return recipes;
    }

    private static Map<ResourceLocation, GunSmithTableRecipe> recipeById(
            List<GunSmithTableRecipe> recipes
    ) {
        LinkedHashMap<ResourceLocation, GunSmithTableRecipe> byId =
                new LinkedHashMap<>();
        for (GunSmithTableRecipe recipe : recipes) {
            byId.put(recipe.getId(), recipe);
        }
        return byId;
    }

    private static final class FakeAccess
            implements TaczGunSmithScreenAccess, GunSmithPropertyFilterAccess {
        private int propertyIndex;
        @Override public int taczaddon$getAttachmentPropertyIndex() { return propertyIndex; }
        @Override public int taczaddon$getAttachmentPropertyCount() { return 4; }
        @Override public void taczaddon$setAttachmentPropertyIndex(int index) { propertyIndex = index; }
        private GunSmithTableRecipe selectedRecipe;
        private List<ResourceLocation> selectedRecipeList = List.of();
        private ResourceLocation selectedType;
        private int indexPage;
        private int typePage;
        private Int2IntArrayMap playerIngredientCount =
                new Int2IntArrayMap();

        @Override
        public GunSmithTableRecipe taczaddon$getSelectedRecipe() {
            return this.selectedRecipe;
        }

        @Override
        public void taczaddon$setSelectedRecipe(
                GunSmithTableRecipe recipe
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
        public ResourceLocation taczaddon$getSelectedType() {
            return this.selectedType;
        }

        @Override
        public void taczaddon$setSelectedType(
                ResourceLocation selectedType
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
}
