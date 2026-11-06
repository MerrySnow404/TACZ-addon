package com.mafuyu404.taczaddon.client.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.function.IntConsumer;

/** A bounded popup whose hit area follows its expanded size. */
public final class DropDown extends AbstractWidget {
    private static final int ROW_HEIGHT = 20;
    private final List<Component> options;
    private final IntConsumer action;
    private final int visibleRows;
    private int selectedIndex;
    private int scrollOffset;
    private boolean expanded;

    public DropDown(int x, int y, int width, int availableHeight,
                    List<Component> options, int selectedIndex, IntConsumer action) {
        super(x, y, width, ROW_HEIGHT, Component.empty());
        this.options = List.copyOf(options);
        this.action = action;
        this.visibleRows = Math.max(1, Math.min(10, availableHeight / ROW_HEIGHT - 1));
        setSelected(selectedIndex);
    }

    public int getSelected() { return selectedIndex; }
    public boolean isExpanded() { return expanded; }
    public void close() { expanded = false; height = ROW_HEIGHT; }
    public void setSelected(int index) {
        selectedIndex = Math.max(0, Math.min(Math.max(0, options.size() - 1), index));
        setMessage(options.isEmpty() ? Component.empty() : options.get(selectedIndex));
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 400);
        drawRow(graphics, getY(), getMessage(), false);
        if (expanded) {
            for (int row = 0; row < Math.min(visibleRows, options.size()); row++) {
                int y = getY() + (row + 1) * ROW_HEIGHT;
                drawRow(graphics, y, options.get(scrollOffset + row),
                        mouseX >= getX() && mouseX < getX() + width && mouseY >= y && mouseY < y + ROW_HEIGHT);
            }
        }
        graphics.pose().popPose();
    }

    private void drawRow(GuiGraphics graphics, int y, Component text, boolean hovered) {
        graphics.fill(getX(), y, getX() + width, y + ROW_HEIGHT, hovered ? 0xFF444444 : 0xFF111111);
        graphics.drawString(Minecraft.getInstance().font, text, getX() + 4, y + 6, 0xFFFFFF);
    }

    @Override
    public boolean mouseClicked(double x, double y, int button) {
        if (!visible || !active) { close(); return false; }
        if (!isMouseOver(x, y)) { close(); return false; }
        if (button != 0) return false;
        if (expanded && y >= getY() + ROW_HEIGHT) {
            int index = scrollOffset + (int) ((y - getY()) / ROW_HEIGHT) - 1;
            setSelected(index);
            close();
            action.accept(selectedIndex);
        } else if (expanded) {
            close();
        } else if (!options.isEmpty()) {
            expanded = true;
            scrollOffset = Math.min(selectedIndex, Math.max(0, options.size() - visibleRows));
            height = ROW_HEIGHT * (1 + Math.min(visibleRows, options.size()));
        }
        return true;
    }

    @Override
    public boolean mouseScrolled(double x, double y, double delta) {
        if (!expanded || !visible || !active) return false;
        scrollOffset = Math.max(0, Math.min(Math.max(0, options.size() - visibleRows),
                scrollOffset - (int) Math.signum(delta)));
        return true;
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }
}
