package com.mafuyu404.taczaddon.gunsmith.recipe;

import com.mafuyu404.taczaddon.inventory.ItemStackData;
import com.mafuyu404.taczaddon.refit.AttachmentUnlocks;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.Level;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class DataDrivenSmithingRecipe implements SmithingRecipe {
    private final Ingredient template;
    private final Ingredient base;
    private final Ingredient addition;
    private final List<ResourceLocation> additionalItems;

    public DataDrivenSmithingRecipe(
            Ingredient template,
            Ingredient base,
            Ingredient addition,
            List<ResourceLocation> additionalItems) {
        this.template = template;
        this.base = base;
        this.addition = addition;
        this.additionalItems = List.copyOf(additionalItems);

        List<ResourceLocation> keyList = new ArrayList<>(additionalItems);
        for (ItemStack item : addition.getItems()) {
            ResourceLocation itemId = AttachmentUnlocks.getItemRegistryId(item.getItem());
            if (itemId != null) AttachmentUnlocks.cacheLegacyUnlocks(itemId, keyList);
        }
    }

    @Override
    public boolean matches(@NotNull SmithingRecipeInput input, @NotNull Level level) {
        return template.test(input.template())
                && base.test(input.base())
                && addition.test(input.addition());
    }

    @Override
    public @NotNull ItemStack assemble(
            @NotNull SmithingRecipeInput input, @NotNull HolderLookup.Provider registries) {
        ItemStack result = input.base().copy();
        ItemStack additionItem = input.addition();

        ItemStackData.updateCustomData(
                result,
                customData -> {
                    /*
                     * New authoritative format: write the actual unlocked attachment IDs.
                     */
                    AttachmentUnlocks.addUnlockedAttachments(customData, this.additionalItems);

                    /*
                     * Retain the old ingredient-history tag for compatibility with
                     * existing addon behavior and old worlds.
                     */
                    ListTag combinedItems =
                            customData.contains("CombinedItems", Tag.TAG_LIST)
                                    ? customData.getList("CombinedItems", Tag.TAG_STRING)
                                    : new ListTag();

                    ResourceLocation additionItemId =
                            BuiltInRegistries.ITEM.getKey(additionItem.getItem());

                    String serializedAdditionId = additionItemId.toString();

                    boolean alreadyPresent = false;

                    for (Tag tag : combinedItems) {
                        if (serializedAdditionId.equals(tag.getAsString())) {
                            alreadyPresent = true;
                            break;
                        }
                    }

                    if (!alreadyPresent) {
                        combinedItems.add(StringTag.valueOf(serializedAdditionId));
                    }

                    customData.put("CombinedItems", combinedItems);
                });

        return result;
    }

    @Override
    public boolean isTemplateIngredient(@NotNull ItemStack stack) {
        return template.test(stack);
    }

    @Override
    public boolean isBaseIngredient(@NotNull ItemStack stack) {
        return base.test(stack);
    }

    @Override
    public boolean isAdditionIngredient(@NotNull ItemStack stack) {
        return addition.test(stack);
    }

    @Override
    public @NotNull RecipeSerializer<?> getSerializer() {
        return ModRecipeSerializers.DATA_DRIVEN_SMITHING.get();
    }

    @Override
    public @NotNull RecipeType<?> getType() {
        return RecipeType.SMITHING;
    }

    @Override
    public boolean isIncomplete() {
        return false;
    }

    @Override
    public @NotNull ItemStack getResultItem(@NotNull HolderLookup.Provider registries) {
        return ItemStack.EMPTY;
    }

    @Override
    public @NotNull NonNullList<Ingredient> getIngredients() {
        return NonNullList.of(Ingredient.EMPTY, template, base, addition);
    }

    public Ingredient getTemplate() {
        return template;
    }

    public Ingredient getBase() {
        return base;
    }

    public Ingredient getAddition() {
        return addition;
    }

    public List<ResourceLocation> getAdditionalItems() {
        return additionalItems;
    }
}
