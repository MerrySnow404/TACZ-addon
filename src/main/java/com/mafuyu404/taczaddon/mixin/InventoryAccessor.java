package com.mafuyu404.taczaddon.mixin;

import com.mafuyu404.taczaddon.inventory.InventoryContentsAccess;

import net.minecraft.core.NonNullList;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

@Mixin(Inventory.class)
public interface InventoryAccessor extends InventoryContentsAccess {
    @Accessor("items")
    @Mutable
    void setItems(NonNullList<ItemStack> items); // 生成 setter 方法

    @Accessor("items")
    NonNullList<ItemStack> getItems(); // 生成 getter 方法

    @Accessor("compartments") // 访问 compartments 字段
    @Mutable // 移除 final 修饰符
    void setCompartments(List<NonNullList<ItemStack>> compartments); // 设置 compartments 字段的值

    @Accessor("compartments")
    List<NonNullList<ItemStack>> getCompartments(); // 获取 compartments 字段的值
}
