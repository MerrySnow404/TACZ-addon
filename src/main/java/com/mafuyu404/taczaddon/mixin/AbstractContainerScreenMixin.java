package com.mafuyu404.taczaddon.mixin;

import com.mafuyu404.taczaddon.config.ClientConfig;
import com.mafuyu404.taczaddon.presentation.client.ItemRelationService;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Set;

import javax.annotation.Nullable;

@Mixin(AbstractContainerScreen.class)
public abstract class AbstractContainerScreenMixin extends Screen {

    @Shadow @Final protected AbstractContainerMenu menu;

    @Shadow protected int leftPos;

    @Shadow protected int topPos;

    @Unique @Nullable private Slot taczaddon$relationHoveredSlot;

    @Unique private ItemStack taczaddon$relationHoveredStack = ItemStack.EMPTY;

    @Unique @Nullable private Slot taczaddon$relationCachedHoveredSlot;

    @Unique private ItemStack taczaddon$relationCachedHoverStack = ItemStack.EMPTY;

    @Unique private int taczaddon$relationCachedMenuHash = Integer.MIN_VALUE;

    @Unique private Set<Slot> taczaddon$relatedSlots = Set.of();

    protected AbstractContainerScreenMixin(Component title) {
        super(title);
    }

    @Inject(method = "render", at = @At("HEAD"))
    private void taczaddon$captureRelationHoveredSlot(
            GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        this.taczaddon$relationHoveredSlot = null;
        this.taczaddon$relationHoveredStack = ItemStack.EMPTY;

        if (!ClientConfig.SHOW_ITEM_RELATION.get()
                || (taczaddon$isSophisticatedScreen()
                        && !ClientConfig.showItemRelationInSophisticatedStorage())) {
            this.taczaddon$invalidateRelationCache();
            return;
        }

        Slot hoveredSlot = this.taczaddon$findHoveredSlot(mouseX, mouseY);

        if (hoveredSlot == null || !hoveredSlot.hasItem() || hoveredSlot.getItem().isEmpty()) {
            this.taczaddon$invalidateRelationCache();
            return;
        }

        this.taczaddon$relationHoveredSlot = hoveredSlot;

        this.taczaddon$relationHoveredStack = hoveredSlot.getItem();

        this.taczaddon$refreshRelationCache(hoveredSlot);
    }

    @Inject(method = "renderSlot", at = @At("RETURN"))
    private void taczaddon$renderRelationHighlight(
            GuiGraphics guiGraphics, Slot slot, CallbackInfo ci) {
        if (!ClientConfig.SHOW_ITEM_RELATION.get()
                || (taczaddon$isSophisticatedScreen()
                        && !ClientConfig.showItemRelationInSophisticatedStorage())) {
            return;
        }

        if (slot == this.taczaddon$relationHoveredSlot) {
            return;
        }

        if (!this.taczaddon$relatedSlots.contains(slot)) {
            return;
        }

        AbstractContainerScreen.renderSlotHighlight(guiGraphics, slot.x, slot.y, 0, 0x80FFA500);
    }

    @Unique
    private boolean taczaddon$isSophisticatedScreen() {
        String name = this.getClass().getName();
        return name.startsWith("net.p3pp3rf1y.sophisticatedbackpacks.")
                || name.startsWith("net.p3pp3rf1y.sophisticatedstorage.");
    }

    @Unique
    @Nullable
    private Slot taczaddon$findHoveredSlot(double mouseX, double mouseY) {
        for (Slot slot : this.menu.slots) {
            if (!slot.isActive()) {
                continue;
            }

            if (this.taczaddon$isMouseOverSlot(slot, mouseX, mouseY)) {
                return slot;
            }
        }

        return null;
    }

    @Unique
    private boolean taczaddon$isMouseOverSlot(Slot slot, double mouseX, double mouseY) {
        return mouseX >= this.leftPos + slot.x
                && mouseX < this.leftPos + slot.x + 16
                && mouseY >= this.topPos + slot.y
                && mouseY < this.topPos + slot.y + 16;
    }

    @Unique
    private void taczaddon$refreshRelationCache(Slot hoveredSlot) {
        int menuHash = this.taczaddon$menuContentHash();

        boolean sameHoveredSlot = this.taczaddon$relationCachedHoveredSlot == hoveredSlot;

        boolean sameHoveredStack =
                ItemStack.isSameItemSameComponents(
                                this.taczaddon$relationCachedHoverStack,
                                this.taczaddon$relationHoveredStack)
                        && this.taczaddon$relationCachedHoverStack.getCount()
                                == this.taczaddon$relationHoveredStack.getCount();

        if (sameHoveredSlot
                && sameHoveredStack
                && this.taczaddon$relationCachedMenuHash == menuHash) {
            return;
        }

        this.taczaddon$relationCachedHoveredSlot = hoveredSlot;

        this.taczaddon$relationCachedHoverStack = this.taczaddon$relationHoveredStack.copy();

        this.taczaddon$relationCachedMenuHash = menuHash;

        this.taczaddon$relatedSlots =
                ItemRelationService.findRelatedSlots(
                        this.menu, hoveredSlot, this.taczaddon$relationHoveredStack);
    }

    @Unique
    private void taczaddon$invalidateRelationCache() {
        this.taczaddon$relationHoveredSlot = null;
        this.taczaddon$relationHoveredStack = ItemStack.EMPTY;

        this.taczaddon$relationCachedHoveredSlot = null;
        this.taczaddon$relationCachedHoverStack = ItemStack.EMPTY;

        this.taczaddon$relationCachedMenuHash = Integer.MIN_VALUE;

        this.taczaddon$relatedSlots = Set.of();
    }

    @Unique
    private int taczaddon$menuContentHash() {
        int hash = 1;

        for (Slot slot : this.menu.slots) {
            ItemStack stack = slot.getItem();

            hash = 31 * hash + System.identityHashCode(slot);

            hash = 31 * hash + (slot.isActive() ? 1 : 0);

            if (stack.isEmpty()) {
                hash = 31 * hash;
                continue;
            }

            hash = 31 * hash + stack.getItem().hashCode();

            hash = 31 * hash + stack.getComponents().hashCode();

            hash = 31 * hash + stack.getCount();
        }

        return hash;
    }
}
