package com.mafuyu404.taczaddon.client.gunsmith;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GunSmithPageInfoTest {
    @Test void sixRecipesPerPageAndCustomCategoryNamePreserved() {
        for (int count : new int[]{1, 6, 7, 12, 13}) {
            Component original = Component.literal("Custom attachments");
            var title = GunSmithPageInfo.title(ResourceLocation.tryParse("test:custom"), 1, count, original);
            var contents = (TranslatableContents) title.getContents();
            assertEquals("gui.taczaddon.gun_smith_table.page_index", contents.getKey());
            assertEquals(original, contents.getArgs()[0]);
            assertEquals(2, contents.getArgs()[1]);
            assertEquals((count + 5) / 6, contents.getArgs()[2]);
        }
    }
    @Test void noTypeOrNoRecipesShowsZeroOfZero() {
        for (var title : new Component[]{GunSmithPageInfo.title(null, 4, 8, Component.empty()),
                GunSmithPageInfo.title(ResourceLocation.tryParse("test:type"), 4, 0, Component.empty())}) {
            var args = ((TranslatableContents) title.getContents()).getArgs();
            assertEquals("-", ((Component) args[0]).getString());
            assertEquals(0, args[1]); assertEquals(0, args[2]);
        }
    }
}
