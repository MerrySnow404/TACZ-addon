package com.mafuyu404.taczaddon.recipe;

import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModRecipeSerializers {
    public static final DeferredRegister<RecipeSerializer<?>>
            SERIALIZERS =
            DeferredRegister.create(
                    ForgeRegistries.Keys.RECIPE_SERIALIZERS,
                    "taczaddon"
            );

    public static final RegistryObject<RecipeSerializer<?>>
            DATA_DRIVEN_SMITHING =
            SERIALIZERS.register(
                    "gun_smithing",
                    () -> DataDrivenSmithingRecipeSerializer.INSTANCE
            );
}
