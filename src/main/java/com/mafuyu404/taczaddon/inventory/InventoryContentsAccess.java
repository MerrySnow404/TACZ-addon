package com.mafuyu404.taczaddon.inventory;

import net.minecraft.core.NonNullList;
import net.minecraft.world.item.ItemStack;

import java.util.List;

public interface InventoryContentsAccess {
    void setItems(NonNullList<ItemStack> items); // 生成 setter 方法

    NonNullList<ItemStack> getItems(); // 生成 getter 方法

    void setCompartments(List<NonNullList<ItemStack>> compartments); // 设置 compartments 字段的值

    List<NonNullList<ItemStack>> getCompartments(); // 获取 compartments 字段的值
}
