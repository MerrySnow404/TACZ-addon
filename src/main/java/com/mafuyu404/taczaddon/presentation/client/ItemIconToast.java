package com.mafuyu404.taczaddon.presentation.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.client.gui.components.toasts.ToastComponent;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import org.jetbrains.annotations.NotNull;

@OnlyIn(Dist.CLIENT)
public class ItemIconToast implements Toast {
    private static final ResourceLocation BACKGROUND =
            ResourceLocation.withDefaultNamespace("toast/advancement");
    private final Component title;
    private final Component description;
    private final ItemStack icon;

    public ItemIconToast(Component title, Component description, ItemStack icon) {
        this.title = title;
        this.description = description;
        this.icon = icon.copy();
    }

    @Override
    public @NotNull Toast.Visibility render(
            @NotNull GuiGraphics gui, @NotNull ToastComponent toastComponent, long timer) {
        gui.blitSprite(BACKGROUND, 0, 0, this.width(), this.height());
        gui.renderFakeItem(icon, 8, 8);

        Font font = toastComponent.getMinecraft().font;
        int textWidth = this.width() - 35;
        gui.drawString(font, fitText(font, title, textWidth), 30, 7, 0xFFD700);
        gui.drawString(font, fitText(font, description, textWidth), 30, 18, 0xFFFFFF);

        return timer >= 2000.0 * toastComponent.getNotificationDisplayTimeMultiplier()
                ? Visibility.HIDE
                : Visibility.SHOW;
    }

    private static FormattedCharSequence fitText(Font font, Component text, int width) {
        if (font.width(text) <= width) {
            return text.getVisualOrderText();
        }
        Component ellipsis = Component.literal("…");
        FormattedText shortened = font.substrByWidth(text, width - font.width(ellipsis));
        return Language.getInstance().getVisualOrder(FormattedText.composite(shortened, ellipsis));
    }

    public static void show(Component title, Component description, ItemStack icon) {
        Minecraft.getInstance().getToasts().addToast(new ItemIconToast(title, description, icon));
    }

    // Convenience overload for translation keys; dynamic text should use show().
    public static void create(String title, String desc, ItemStack itemStack) {
        show(Component.translatable(title), Component.translatable(desc), itemStack);
    }
}
