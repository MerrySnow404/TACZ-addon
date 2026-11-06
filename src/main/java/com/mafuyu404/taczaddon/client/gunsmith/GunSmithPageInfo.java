package com.mafuyu404.taczaddon.client.gunsmith;

import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

public final class GunSmithPageInfo {
    private GunSmithPageInfo() {}
    public static Component title(ResourceLocation selectedType, int indexPage, int recipeCount, Component original) {
        boolean empty = selectedType == null || recipeCount <= 0;
        String key = empty ? "" : "tacz.type." + selectedType.getPath() + ".name";
        Component type = empty ? Component.literal("-") : Language.getInstance().has(key)
                ? Component.translatable(key) : original;
        return Component.translatable("gui.taczaddon.gun_smith_table.page_index", type,
                empty ? 0 : indexPage + 1, empty ? 0 : (recipeCount + 5) / 6);
    }
}
