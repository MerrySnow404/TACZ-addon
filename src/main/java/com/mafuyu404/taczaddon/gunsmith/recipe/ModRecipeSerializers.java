package com.mafuyu404.taczaddon.gunsmith.recipe;

import com.mafuyu404.taczaddon.TACZaddon;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModRecipeSerializers {
    public static final DeferredRegister<RecipeSerializer<?>> SERIALIZERS =
            DeferredRegister.create(Registries.RECIPE_SERIALIZER, TACZaddon.MODID);

    public static final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<?>>
            DATA_DRIVEN_SMITHING =
                    SERIALIZERS.register(
                            "gun_smithing", () -> DataDrivenSmithingRecipeSerializer.INSTANCE);

    public static void register(IEventBus eventBus) {
        SERIALIZERS.register(eventBus);
    }
}
