package com.mafuyu404.taczaddon.gunsmith.recipe;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeSerializer;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class DataDrivenSmithingRecipeSerializer
        implements RecipeSerializer<DataDrivenSmithingRecipe> {
    public static final DataDrivenSmithingRecipeSerializer INSTANCE =
            new DataDrivenSmithingRecipeSerializer();

    private static final MapCodec<DataDrivenSmithingRecipe> CODEC =
            RecordCodecBuilder.mapCodec(
                    instance ->
                            instance.group(
                                            Ingredient.CODEC
                                                    .fieldOf("template")
                                                    .forGetter(
                                                            DataDrivenSmithingRecipe::getTemplate),
                                            Ingredient.CODEC
                                                    .fieldOf("base")
                                                    .forGetter(DataDrivenSmithingRecipe::getBase),
                                            Ingredient.CODEC
                                                    .fieldOf("addition")
                                                    .forGetter(
                                                            DataDrivenSmithingRecipe::getAddition),
                                            ResourceLocation.CODEC
                                                    .listOf()
                                                    .optionalFieldOf("list", List.of())
                                                    .forGetter(
                                                            DataDrivenSmithingRecipe
                                                                    ::getAdditionalItems))
                                    .apply(instance, DataDrivenSmithingRecipe::new));

    private static final StreamCodec<RegistryFriendlyByteBuf, List<ResourceLocation>>
            RESOURCE_LOCATION_LIST_STREAM_CODEC =
                    ByteBufCodecs.collection(
                            ArrayList::new,
                            ResourceLocation.STREAM_CODEC.<RegistryFriendlyByteBuf>cast());

    private static final StreamCodec<RegistryFriendlyByteBuf, DataDrivenSmithingRecipe>
            STREAM_CODEC =
                    StreamCodec.composite(
                            Ingredient.CONTENTS_STREAM_CODEC,
                            DataDrivenSmithingRecipe::getTemplate,
                            Ingredient.CONTENTS_STREAM_CODEC,
                            DataDrivenSmithingRecipe::getBase,
                            Ingredient.CONTENTS_STREAM_CODEC,
                            DataDrivenSmithingRecipe::getAddition,
                            RESOURCE_LOCATION_LIST_STREAM_CODEC,
                            DataDrivenSmithingRecipe::getAdditionalItems,
                            DataDrivenSmithingRecipe::new);

    @Override
    public @NotNull MapCodec<DataDrivenSmithingRecipe> codec() {
        return CODEC;
    }

    @Override
    public @NotNull StreamCodec<RegistryFriendlyByteBuf, DataDrivenSmithingRecipe> streamCodec() {
        return STREAM_CODEC;
    }
}
