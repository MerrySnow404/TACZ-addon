package com.mafuyu404.taczaddon.client.gunsmith;

import com.mafuyu404.taczaddon.testutil.MinecraftTestBootstrap;
import com.tacz.guns.crafting.GunSmithTableRecipe;
import com.tacz.guns.crafting.result.GunSmithTableResult;
import com.tacz.guns.item.AttachmentItem;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.registries.ForgeRegistries;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class GunSmithPropertyFilterTest {
    private static AttachmentItem attachment;
    private static final ResourceLocation RECIPE = ResourceLocation.tryParse("test:arbitrary/nested/recipe");
    private static final ResourceLocation ATTACHMENT = ResourceLocation.tryParse("test:scope");
    @BeforeAll static void prepare() throws Exception {
        MinecraftTestBootstrap.prepare();
        attachment = new AttachmentItem();
        ForgeRegistries.ITEMS.register(ResourceLocation.tryParse("test:filter_attachment"), attachment);
    }
    private GunSmithTableRecipe recipe(ItemStack output) {
        return new GunSmithTableRecipe(RECIPE, new GunSmithTableResult(output, ResourceLocation.tryParse("test:custom_tab")), List.of());
    }
    private GunSmithTableRecipe attachmentRecipe() {
        ItemStack stack = new ItemStack(attachment);
        attachment.setAttachmentId(stack, ATTACHMENT);
        return recipe(stack);
    }

    @Test void catalogHasDefaultSpecialIgniteExclusionsAndTranslationFallback() {
        assertEquals(List.of("gui.taczaddon.gun_smith_table.default_prop",
                "tooltip.tacz.attachment.ignite.block", "tooltip.tacz.attachment.ignite.entity",
                "tooltip.tacz.attachment.damage.increase", "tooltip.tacz.attachment.accuracy"),
                GunSmithPropertyFilter.catalogKeys(List.of("ignite", "weight_modifier", "recoil", "damage", "accuracy"),
                        key -> key.equals("tooltip.tacz.attachment.damage.increase")));
        assertEquals(1, GunSmithPropertyFilter.catalogKeys(List.of(), key -> false).size());
    }

    @Test void defaultKeepsEverythingWithoutResolvingAnyRecipe() {
        assertTrue(GunSmithPropertyFilter.matches(RECIPE, null, Map.of(), id -> { fail("default resolved recipe"); return null; }));
    }

    @Test void propertyFilterScopeIsTheAttachmentWorkbenchOnly() {
        assertTrue(GunSmithPropertyFilter.supportsWorkbench(
                ResourceLocation.tryParse("tacz:attachment_workbench")
        ));
        assertFalse(GunSmithPropertyFilter.supportsWorkbench(
                ResourceLocation.tryParse("tacz:ammo_workbench")
        ));
        assertFalse(GunSmithPropertyFilter.supportsWorkbench(
                ResourceLocation.tryParse("tacz:gun_smith_table")
        ));
        assertFalse(GunSmithPropertyFilter.supportsWorkbench(
                ResourceLocation.tryParse("test:custom_table")
        ));
        assertFalse(GunSmithPropertyFilter.supportsWorkbench(null));
    }

    @Test void matchesTranslatedModifierTextUsingActualAttachmentIdRatherThanRecipePath() {
        var recipe = attachmentRecipe();
        assertTrue(GunSmithPropertyFilter.matches(RECIPE, "伤害", Map.of(ATTACHMENT, "增加伤害 10%"), id -> recipe));
        assertFalse(GunSmithPropertyFilter.matches(RECIPE, "后坐力", Map.of(ATTACHMENT, "增加伤害 10%"), id -> recipe));
        assertFalse(GunSmithPropertyFilter.matches(RECIPE, "伤害", Map.of(), id -> recipe));
    }

    @Test void nonAttachmentsAndUnresolvableOrMalformedRecipesAreConservative() {
        assertTrue(GunSmithPropertyFilter.matches(RECIPE, "damage", Map.of(), id -> recipe(new ItemStack(Items.STONE))));
        assertTrue(GunSmithPropertyFilter.matches(RECIPE, "damage", Map.of(), id -> null));
        assertTrue(GunSmithPropertyFilter.matches(null, "damage", Map.of(), id -> null));
        assertTrue(GunSmithPropertyFilter.matches(RECIPE, "damage", Map.of(), id -> { throw new IllegalArgumentException("broken recipe"); }));
    }

    @Test
    void mixinCannotFilterOrCreateDropdownWithoutWorkbenchScope()
            throws IOException {
        String mixin = Files.readString(
                Path.of(
                        "src/main/java/com/mafuyu404/taczaddon/mixin/tacz/v1_1_8/GunSmithTablePropertyFilterMixin.java"
                ),
                StandardCharsets.UTF_8
        ).replaceAll("\\s+", "");

        assertTrue(
                mixin.contains(
                        "if(!taczaddon$supportsPropertyFilter()||taczaddon$propertyIndex==0||taczaddon$properties==null){returnlist.add(entry);}"
                ),
                "classifyRecipes must add every entry for unsupported tables"
        );
        assertTrue(
                mixin.contains(
                        "taczaddon$addDropdown(CallbackInfoci){if(!taczaddon$supportsPropertyFilter())return;"
                ),
                "the property dropdown must not be created for unsupported tables"
        );
        assertTrue(
                mixin.contains(
                        "taczaddon$scrollFilter(doublex,doubley,doubledelta,CallbackInfoReturnable<Boolean>cir){if(!taczaddon$supportsPropertyFilter())return;"
                ),
                "unsupported tables must keep generic mouse scrolling"
        );
        assertTrue(
                mixin.contains(
                        "taczaddon$handlePropertyClick(doublex,doubley,intbutton){returntaczaddon$supportsPropertyFilter()"
                ),
                "unsupported tables must never consume property clicks"
        );
    }
}
