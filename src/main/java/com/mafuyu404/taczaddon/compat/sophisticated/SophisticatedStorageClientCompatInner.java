package com.mafuyu404.taczaddon.compat.sophisticated;

import com.mafuyu404.taczaddon.config.Config;
import com.mafuyu404.taczaddon.recipe.ItemRelationHelper;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.event.ContainerScreenEvent;
import net.p3pp3rf1y.sophisticatedcore.client.gui.StorageScreenBase;
import net.p3pp3rf1y.sophisticatedcore.common.gui.StorageContainerMenuBase;

public final class SophisticatedStorageClientCompatInner {
    private static final int RELATION_HIGHLIGHT_COLOR = 0x80FFA500;

    private SophisticatedStorageClientCompatInner() {
    }

    public static boolean isStorageScreen(AbstractContainerScreen<?> screen) {
        return screen instanceof StorageScreenBase<?>;
    }

    public static void renderItemRelations(ContainerScreenEvent.Render.Foreground event) {
        AbstractContainerScreen<?> screen = event.getContainerScreen();
        if (!(screen instanceof StorageScreenBase<?>)) {
            return;
        }

        if (!Config.showItemRelationInSophisticatedStorage()) {
            return;
        }

        Slot hoveredSlot = screen.getSlotUnderMouse();
        if (hoveredSlot == null
                || !hoveredSlot.isActive()
                || !hoveredSlot.hasItem()) {
            return;
        }

        ItemStack hoveredStack = hoveredSlot.getItem();
        if (hoveredStack.isEmpty()) {
            return;
        }

        AbstractContainerMenu menu = screen.getMenu();
        for (Slot slot : menu.slots) {
            if (!shouldCheckSlot(screen, menu, slot, hoveredSlot)) {
                continue;
            }

            if (ItemRelationHelper.areRelated(hoveredStack, slot.getItem())) {
                AbstractContainerScreen.renderSlotHighlight(
                        event.getGuiGraphics(),
                        slot.x,
                        slot.y,
                        0,
                        RELATION_HIGHLIGHT_COLOR
                );
            }
        }
    }

    private static boolean shouldCheckSlot(
            AbstractContainerScreen<?> screen,
            AbstractContainerMenu menu,
            Slot slot,
            Slot hoveredSlot
    ) {
        if (slot == hoveredSlot
                || !slot.isActive()
                || !slot.hasItem()) {
            return false;
        }

        if (!isSlotInsideScreen(screen, slot)) {
            return false;
        }

        if (menu instanceof StorageContainerMenuBase<?> storageMenu
                && storageMenu.isStorageInventorySlot(slot.index)
                && storageMenu.isInaccessibleSlot(slot.index)) {
            return false;
        }

        return true;
    }

    private static boolean isSlotInsideScreen(
            AbstractContainerScreen<?> screen,
            Slot slot
    ) {
        return slot.x >= 0
                && slot.y >= 0
                && slot.x + 16 <= screen.getXSize()
                && slot.y + 16 <= screen.getYSize();
    }
}
