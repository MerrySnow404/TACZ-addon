package com.mafuyu404.taczaddon.mixin;

import com.mafuyu404.taczaddon.gunsmith.GunSmithRecipeAccess;
import com.tacz.guns.crafting.GunSmithTableRecipe;
import com.tacz.guns.inventory.GunSmithTableMenu;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.RecipeManager;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(value = GunSmithTableMenu.class, remap = false)
public interface GunSmithTableMenuAccess extends GunSmithRecipeAccess {
    @Invoker("getRecipe")
    GunSmithTableRecipe taczaddon$invokeGetRecipe(ResourceLocation id, RecipeManager manager);
}
