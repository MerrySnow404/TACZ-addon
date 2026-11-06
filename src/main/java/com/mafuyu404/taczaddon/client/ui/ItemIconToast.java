package com.mafuyu404.taczaddon.client.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.client.gui.components.toasts.ToastComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.jetbrains.annotations.NotNull;

@OnlyIn(Dist.CLIENT)
public class ItemIconToast implements Toast {
    private final Component title;
    private final Component description;
    private final ItemStack icon;  // 要显示的物品图标
    private long visibleTime;

    // 构造函数
    public ItemIconToast(Component title, Component description, ItemStack icon) {
        this.title = title;
        this.description = description;
        this.icon = icon;
    }

    @Override
    public @NotNull Toast.Visibility render(@NotNull GuiGraphics gui, @NotNull ToastComponent toastComponent, long timer) {
        gui.blit(TEXTURE, 0, 0, 0, 0, this.width(), this.height());

        gui.renderFakeItem(icon, 8, 8);

        gui.drawString(toastComponent.getMinecraft().font, title.getString(), 30, 7, 0xFFD700);
        gui.drawString(toastComponent.getMinecraft().font, description.getString(), 30, 18, 0xFFFFFF);

        return timer >= 2000L ? Visibility.HIDE : Visibility.SHOW;
    }

    // 快速显示方法
    public static void show(Component title, Component description, ItemStack icon) {
        Minecraft.getInstance().getToasts().addToast(new ItemIconToast(title, description, icon));
    }

    // 示例用法：显示一个钻石图标的 Toast
    public static void create(String title, String desc, ItemStack itemStack) {
        show(
                Component.translatable(title),
                Component.translatable(desc),
                itemStack // 替换为任意物品
        );
    }
}
