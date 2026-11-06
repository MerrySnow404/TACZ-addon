package com.mafuyu404.taczaddon.mixin;

import com.mafuyu404.taczaddon.refit.client.RefitExternalSourceState;
import com.mafuyu404.taczaddon.refit.client.RefitScreenController;
import com.tacz.guns.client.gui.GunRefitScreen;

import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = GunRefitScreen.class, remap = false)
public abstract class GunRefitScreenMixin extends Screen implements RefitScreenController.Host {
    @Shadow private int currentPage;
    @Unique private RefitScreenController taczaddon$controller;

    protected GunRefitScreenMixin(Component title) {
        super(title);
    }

    @Unique
    private RefitScreenController taczaddon$controller() {
        if (taczaddon$controller == null) taczaddon$controller = new RefitScreenController(this);
        return taczaddon$controller;
    }

    @Inject(method = "init", at = @At("HEAD"), require = 1)
    private void taczaddon$refreshExternalCandidates(CallbackInfo ci) {
        RefitExternalSourceState.requestIfNeeded();
    }

    @Inject(
            method = "addAttachmentTypeButtons()V",
            at = @At("HEAD"),
            cancellable = true,
            remap = false,
            require = 1)
    private void taczaddon$rebuildAttachmentTypeButtons(CallbackInfo ci) {
        if (taczaddon$controller().rebuildAttachmentTypeButtons()) ci.cancel();
    }

    @Inject(
            method = "addInventoryAttachmentButtons()V",
            at = @At("HEAD"),
            cancellable = true,
            remap = false)
    private void taczaddon$addSafeAttachmentButtons(CallbackInfo ci) {
        ci.cancel();
        taczaddon$controller().addSafeAttachmentButtons();
    }

    @Override
    public int taczaddon$width() {
        return width;
    }

    @Override
    public int taczaddon$height() {
        return height;
    }

    @Override
    public int taczaddon$page() {
        return currentPage;
    }

    @Override
    public void taczaddon$setPage(int value) {
        currentPage = value;
    }

    @Override
    public void taczaddon$rebuild() {
        init();
    }

    @Override
    public void taczaddon$addWidget(AbstractWidget widget) {
        addRenderableWidget(widget);
    }
}
