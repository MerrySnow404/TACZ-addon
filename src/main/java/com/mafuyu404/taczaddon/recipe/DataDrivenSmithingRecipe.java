package com.mafuyu404.taczaddon.recipe;

import net.minecraft.core.NonNullList;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SmithingRecipe;
import net.minecraft.world.level.Level;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class DataDrivenSmithingRecipe implements SmithingRecipe {
    private final ResourceLocation id;
    private final Ingredient template;
    private final Ingredient base;
    private final Ingredient addition;
    private final List<ResourceLocation> additionalItems;

    public DataDrivenSmithingRecipe(ResourceLocation id, Ingredient template, Ingredient base, Ingredient addition, List<ResourceLocation> additionalItems) {
        this.id = id;
        this.template = template;
        this.base = base;
        this.addition = addition;
        this.additionalItems = additionalItems;
    }

    @Override
    public boolean matches(@NotNull Container container, @NotNull Level level) {
        return template.test(container.getItem(0)) &&
                base.test(container.getItem(1)) &&
                addition.test(container.getItem(2));
    }

    @Override
    public @NotNull ItemStack assemble(@NotNull Container container, @NotNull RegistryAccess registryAccess) {
        ItemStack baseItem = container.getItem(1).copy();
        ItemStack additionItem = container.getItem(2);

        // 获取或创建NBT标签
        CompoundTag nbt = baseItem.getOrCreateTag();
        ListTag itemList;

        if (nbt.contains("CombinedItems", 9)) { // 9是ListTag的ID
            itemList = nbt.getList("CombinedItems", 8); // 8是StringTag的ID
        } else {
            itemList = new ListTag();
        }

        // 添加addition物品的ID到列表
        ResourceLocation additionItemId = ForgeRegistries.ITEMS.getKey(additionItem.getItem());
        if (additionItemId == null) {
            return baseItem;
        }

        if (!itemList.contains(StringTag.valueOf(additionItemId.toString()))) {
            itemList.add(StringTag.valueOf(additionItemId.toString()));
        }

        // 添加配方中定义的额外物品ID到列表
//        for (ResourceLocation itemId : additionalItems) {
//            itemList.add(StringTag.valueOf(itemId.toString()));
//        }

        // 保存回NBT
        nbt.put("CombinedItems", itemList);

        return baseItem;
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
    public @NotNull ResourceLocation getId() {
        return id;
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
    public @NotNull ItemStack getResultItem(@NotNull RegistryAccess registryAccess) {
        return ItemStack.EMPTY;
    }

    @Override
    public @NotNull NonNullList<Ingredient> getIngredients() {
        return NonNullList.of(Ingredient.EMPTY, template, base, addition);
    }

    // Getter方法，用于序列化
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
