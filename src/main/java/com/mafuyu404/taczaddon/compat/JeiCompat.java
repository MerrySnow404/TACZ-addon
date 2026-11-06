package com.mafuyu404.taczaddon.compat;

import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.fml.ModList;

@OnlyIn(Dist.CLIENT)
public final class JeiCompat {
    private static final String MOD_ID = "jei";

    private JeiCompat() {}

    public static void init() {
        // Kept for existing setup calls; mod-loaded state is checked lazily.
    }

    public static boolean isInstalled() {
        return ModList.get() != null && ModList.get().isLoaded(MOD_ID);
    }

    public static boolean showRecipes(ItemStack itemStack) {
        if (!isInstalled() || itemStack == null || itemStack.isEmpty()) return false;
        return JeiPlugin.showRecipes(itemStack);
    }
}
