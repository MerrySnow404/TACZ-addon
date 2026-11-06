package com.mafuyu404.taczaddon.presentation.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class DropDown extends AbstractWidget {
    private final List<Component> options = new ArrayList<>();
    private boolean isExpanded = false;
    private int selectedIndex = 0;
    private int scrollOffset = 0; // 当前滚动偏移量
    private final int itemHeight = 20; // 每个选项的高度
    private final int maxVisibleItems = 10; // 最大可见选项数
    private static final int SCROLL_BAR_WIDTH = 4; // 滚动条宽度

    public DropDown(int x, int y, int width) {
        super(x, y, width, 220, Component.empty());
    }

    public void addOption(Component option) {
        options.add(option);
    }

    public int getSelected() {
        return selectedIndex;
    }

    public void setSelected(int index) {
        if (index >= 0 && index < options.size()) {
            selectedIndex = index;
        }
    }

    public Component getOption(int i) {
        return options.get(i);
    }

    @Override
    public void renderWidget(
            @NotNull GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        guiGraphics.fill(getX(), getY(), getX() + width, getY() + itemHeight, 0xFF000000);
        guiGraphics.renderOutline(getX(), getY(), width, itemHeight, 0xFFFFFFFF);
        if (!options.isEmpty()) {
            guiGraphics.drawString(
                    Minecraft.getInstance().font,
                    options.get(selectedIndex),
                    getX() + 5,
                    getY() + (itemHeight - 8) / 2,
                    0xFFFFFF);
        }

        // 展开状态
        if (isExpanded) {
            int totalHeight = Math.min(options.size(), maxVisibleItems) * itemHeight;
            guiGraphics.fill(
                    getX(),
                    getY() + itemHeight,
                    getX() + width,
                    getY() + itemHeight + totalHeight,
                    0xFF000000);

            int itemsToShow = Math.min(options.size() - scrollOffset, maxVisibleItems);
            for (int i = 0; i < itemsToShow; i++) {
                int actualIndex = i + scrollOffset;
                int yPos = getY() + itemHeight + (i * itemHeight);

                boolean isHovered = isMouseOverOption(mouseX, mouseY, i);
                if (isHovered) {
                    guiGraphics.fill(getX(), yPos, getX() + width, yPos + itemHeight, 0x80444444);
                }
                guiGraphics.drawString(
                        Minecraft.getInstance().font,
                        options.get(actualIndex),
                        getX() + 5,
                        yPos + (itemHeight - 8) / 2,
                        0xFFFFFF);
            }
            if (options.size() > maxVisibleItems) {
                drawScrollBar(guiGraphics, totalHeight);
            }
        }
    }

    public Consumer<Integer> action = null;

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!isMouseOver(mouseX, mouseY) && !isExpanded) return false;

        if (button == 0) {
            if (!isExpanded) {
                isExpanded = true;
                scrollOffset = 0; // 展开时重置滚动位置
            } else {
                for (int i = 0; i < Math.min(options.size(), maxVisibleItems); i++) {
                    if (isMouseOverOption(mouseX, mouseY, i)) {
                        selectedIndex = i + scrollOffset;
                        isExpanded = false;
                        if (action != null) action.accept(selectedIndex);
                        return true;
                    }
                }
                isExpanded = false;
            }
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void drawScrollBar(GuiGraphics guiGraphics, int totalHeight) {
        int contentHeight = options.size() * itemHeight;
        int visibleHeight = maxVisibleItems * itemHeight;
        float scrollRatio = (float) scrollOffset / (options.size() - maxVisibleItems);
        int scrollBarHeight = (int) ((float) visibleHeight * visibleHeight / contentHeight);
        int scrollY = getY() + itemHeight + (int) (scrollRatio * (totalHeight - scrollBarHeight));
        guiGraphics.fill(
                getX() + width - SCROLL_BAR_WIDTH - 1,
                getY() + itemHeight,
                getX() + width - 1,
                getY() + itemHeight + totalHeight,
                0xFF666666);
        guiGraphics.fill(
                getX() + width - SCROLL_BAR_WIDTH - 1,
                scrollY,
                getX() + width - 1,
                scrollY + scrollBarHeight,
                0xFFAAAAAA);
    }

    @Override
    protected void updateWidgetNarration(@NotNull NarrationElementOutput p_259858_) {}

    private boolean isMouseOverOption(double mouseX, double mouseY, int visibleIndex) {
        return mouseX >= getX()
                && mouseX <= getX() + width - SCROLL_BAR_WIDTH // 排除滚动条区域
                && mouseY >= getY() + itemHeight + (visibleIndex * itemHeight)
                && mouseY <= getY() + itemHeight + ((visibleIndex + 1) * itemHeight);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (isExpanded) {
            // 计算新的滚动偏移量（向上滚动为负值，向下滚动为正值）
            int newScrollOffset = (int) (scrollOffset - Math.signum(scrollY));

            // 限制滚动范围 [0, maxScroll]
            int maxScroll = Math.max(0, options.size() - maxVisibleItems);
            scrollOffset = clamp(newScrollOffset, 0, maxScroll);
            return true; // 拦截滚动事件
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    public static int clamp(int value, int min, int max) {
        return Math.min(Math.max(value, min), max);
    }
}
