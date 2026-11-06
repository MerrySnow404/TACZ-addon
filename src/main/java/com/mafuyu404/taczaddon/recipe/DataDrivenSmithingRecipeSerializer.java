package com.mafuyu404.taczaddon.recipe;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeSerializer;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class DataDrivenSmithingRecipeSerializer implements RecipeSerializer<DataDrivenSmithingRecipe> {
    public static final DataDrivenSmithingRecipeSerializer INSTANCE = new DataDrivenSmithingRecipeSerializer();

    @Override
    public @NotNull DataDrivenSmithingRecipe fromJson(@NotNull ResourceLocation recipeId, @NotNull JsonObject json) {
        Ingredient template = Ingredient.fromJson(GsonHelper.getAsJsonObject(json, "template"));
        Ingredient base = Ingredient.fromJson(GsonHelper.getAsJsonObject(json, "base"));
        Ingredient addition = Ingredient.fromJson(GsonHelper.getAsJsonObject(json, "addition"));

        // 解析额外的物品列表
        List<ResourceLocation> additionalItems = new ArrayList<>();
        if (json.has("list")) {
            JsonArray listArray = GsonHelper.getAsJsonArray(json, "list");
            for (JsonElement element : listArray) {
                String attachmentId = element.getAsString();
                ResourceLocation id = ResourceLocation.tryParse(attachmentId);
                if (id == null) {
                    throw new JsonSyntaxException("Invalid resource location in smithing list: " + attachmentId);
                }
                additionalItems.add(id);
            }
        }

        return new DataDrivenSmithingRecipe(recipeId, template, base, addition, additionalItems);
    }

    @Override
    public @NotNull DataDrivenSmithingRecipe fromNetwork(@NotNull ResourceLocation recipeId, @NotNull FriendlyByteBuf buffer) {
        Ingredient template = Ingredient.fromNetwork(buffer);
        Ingredient base = Ingredient.fromNetwork(buffer);
        Ingredient addition = Ingredient.fromNetwork(buffer);

        // 读取额外的物品列表
        int listSize = buffer.readVarInt();
        List<ResourceLocation> additionalItems = new ArrayList<>(listSize);
        for (int i = 0; i < listSize; i++) {
            additionalItems.add(buffer.readResourceLocation());
        }

        return new DataDrivenSmithingRecipe(recipeId, template, base, addition, additionalItems);
    }

    @Override
    public void toNetwork(@NotNull FriendlyByteBuf buffer, @NotNull DataDrivenSmithingRecipe recipe) {
        recipe.getTemplate().toNetwork(buffer);
        recipe.getBase().toNetwork(buffer);
        recipe.getAddition().toNetwork(buffer);

        // 写入额外的物品列表
        buffer.writeVarInt(recipe.getAdditionalItems().size());
        for (ResourceLocation itemId : recipe.getAdditionalItems()) {
            buffer.writeResourceLocation(itemId);
        }
    }
}
