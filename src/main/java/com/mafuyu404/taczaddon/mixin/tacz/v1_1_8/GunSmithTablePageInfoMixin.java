package com.mafuyu404.taczaddon.mixin.tacz.v1_1_8;

import com.mafuyu404.taczaddon.client.gunsmith.GunSmithPageInfo;

import com.tacz.guns.client.gui.GunSmithTableScreen;
import com.tacz.guns.inventory.GunSmithTableMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(value = GunSmithTableScreen.class, remap = false)
public abstract class GunSmithTablePageInfoMixin extends AbstractContainerScreen<GunSmithTableMenu> {
    @Shadow private ResourceLocation selectedType;
    @Shadow private List<ResourceLocation> selectedRecipeList;
    @Shadow private int indexPage;
    protected GunSmithTablePageInfoMixin(GunSmithTableMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    @Unique private Component taczaddon$pageTitle(Component original) {
        return GunSmithPageInfo.title(selectedType, indexPage,
                selectedRecipeList == null ? 0 : selectedRecipeList.size(), original);
    }

    @ModifyArg(method = "render(Lnet/minecraft/client/gui/GuiGraphics;IIF)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphics;drawString(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;IIIZ)I",
                    ordinal = 0), index = 1, remap = true, require = 1)
    private Component taczaddon$augmentTitle(Component original) { return taczaddon$pageTitle(original); }

    // TaCZ skips the original title invocation entirely when classification has no category.
    @Inject(method = "render(Lnet/minecraft/client/gui/GuiGraphics;IIF)V", at = @At("TAIL"), remap = true, require = 1)
    private void taczaddon$emptyTitle(GuiGraphics graphics, int x, int y, float partialTick, CallbackInfo ci) {
        if (selectedType == null) graphics.drawString(font, taczaddon$pageTitle(Component.empty()),
                leftPos + 150, topPos + 32, 0x555555, false);
    }
}
