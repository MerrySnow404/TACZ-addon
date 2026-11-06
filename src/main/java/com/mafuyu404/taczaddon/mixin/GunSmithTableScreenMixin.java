package com.mafuyu404.taczaddon.mixin;

import com.mafuyu404.taczaddon.gunsmith.client.GunSmithScreenController;
import com.tacz.guns.client.gui.GunSmithTableScreen;
import com.tacz.guns.crafting.GunSmithTableRecipe;
import com.tacz.guns.inventory.GunSmithTableMenu;
import com.tacz.guns.resource.pojo.data.block.TabConfig;

import it.unimi.dsi.fastutil.Pair;
import it.unimi.dsi.fastutil.ints.Int2IntArrayMap;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.*;

import javax.annotation.Nullable;

@Mixin(value = GunSmithTableScreen.class, remap = false)
public abstract class GunSmithTableScreenMixin extends AbstractContainerScreen<GunSmithTableMenu>
        implements GunSmithScreenController.Host {
    @Unique private GunSmithScreenController taczaddon$controller;
    @Shadow @Nullable private RecipeHolder<GunSmithTableRecipe> selectedRecipe;

    @Shadow private List<ResourceLocation> selectedRecipeList;
    @Shadow private int indexPage;

    @Shadow
    public abstract void updateIngredientCount();

    @Shadow private ResourceLocation selectedType;

    @Shadow @Final private Map<ResourceLocation, List<ResourceLocation>> recipes;

    @Shadow @Final private LinkedHashMap<ResourceLocation, TabConfig> recipeKeys;

    @Shadow private int typePage;

    @Shadow
    @Nullable
    private RecipeHolder<GunSmithTableRecipe> getSelectedRecipe(ResourceLocation recipeId) {
        throw new AssertionError();
    }

    @Shadow
    private void getPlayerIngredientCount(RecipeHolder<GunSmithTableRecipe> recipe) {
        throw new AssertionError();
    }

    public GunSmithTableScreenMixin(
            GunSmithTableMenu p_97741_, Inventory p_97742_, Component p_97743_) {
        super(p_97741_, p_97742_, p_97743_);
    }

    @Inject(method = "<init>", at = @At("TAIL"))
    private void taczaddon$initFields(CallbackInfo ci) {
        taczaddon$getController();
    }

    @Unique
    private GunSmithScreenController taczaddon$getController() {
        // TaCZ also classifies recipes inside its constructor, before our TAIL injection.
        if (this.taczaddon$controller == null) {
            this.taczaddon$controller = new GunSmithScreenController(this);
            this.taczaddon$controller.initFields();
        }
        return this.taczaddon$controller;
    }

    @Redirect(
            method = "classifyRecipes",
            at = @At(value = "INVOKE", target = "Ljava/util/List;add(Ljava/lang/Object;)Z"))
    private boolean taczaddon$filterAttachmentProperty(
            List<Pair<ResourceLocation, ResourceLocation>> list, Object e) {
        return taczaddon$getController().filterAttachmentProperty(list, e);
    }

    @Inject(method = "init", at = @At("HEAD"), remap = true)
    private void taczaddon$restoreAttachmentFilterBeforeClassify(CallbackInfo ci) {
        taczaddon$getController().restoreAttachmentFilterBeforeClassify();
    }

    @Inject(
            method = "init",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lcom/tacz/guns/client/gui/GunSmithTableScreen;updateSelectedRecipeAfterFiltering()V",
                            shift = At.Shift.AFTER),
            remap = false)
    private void taczaddon$restoreBrowseStateAfterClassify(CallbackInfo ci) {
        taczaddon$getController().restoreBrowseStateAfterClassify();
    }

    @Inject(method = "init", at = @At("TAIL"), remap = true)
    private void taczaddon$onScreenChanged(CallbackInfo ci) {
        taczaddon$getController().onScreenChanged();
    }

    @ModifyArg(
            method = "render",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/client/gui/GuiGraphics;drawString(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;IIIZ)I",
                            ordinal = 0),
            index = 1,
            remap = true)
    private Component taczaddon$renderPageInfo(Component p_282131_) {
        return taczaddon$getController().renderPageInfo();
    }

    @Inject(method = "mouseScrolled", at = @At("HEAD"), cancellable = true, remap = true)
    private void taczaddon$guardMouseScrolledWhenNoRecipes(
            double mouseX,
            double mouseY,
            double scrollX,
            double scrollY,
            CallbackInfoReturnable<Boolean> cir) {
        if (taczaddon$getController().guardMouseScrolledWhenNoRecipes(mouseX, mouseY, scrollX, scrollY))
            cir.setReturnValue(true);
    }

    @ModifyVariable(method = "getPlayerIngredientCount", at = @At("STORE"), ordinal = 0)
    private Inventory taczaddon$modifyIngredientShow(Inventory inventory) {
        return taczaddon$getController().modifyIngredientShow(inventory);
    }

    @Inject(method = "render", at = @At("HEAD"), remap = true)
    private void taczaddon$storedMousePos(
            GuiGraphics graphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        taczaddon$getController().storeMousePosition(mouseX, mouseY);
    }

    @Redirect(
            method = "renderIngredient",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/client/gui/GuiGraphics;renderFakeItem(Lnet/minecraft/world/item/ItemStack;II)V"),
            remap = true)
    private void taczaddon$renderBetterIngredient(
            GuiGraphics graphics, ItemStack itemStack, int x, int y) {
        taczaddon$getController().renderBetterIngredient(graphics, itemStack, x, y);
    }

    @Shadow @Nullable private Int2IntArrayMap playerIngredientCount;

    @ModifyArg(
            method = "addCraftButton",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lcom/tacz/guns/client/gui/components/smith/ImageButton;"
                                            + "<init>("
                                            + "IIIIIII"
                                            + "Lnet/minecraft/resources/ResourceLocation;"
                                            + "Lnet/minecraft/client/gui/components/Button$OnPress;"
                                            + ")V"),
            index = 8,
            remap = false)
    private Button.OnPress taczaddon$wrapCraftButtonCallback(Button.OnPress original) {
        return taczaddon$getController().craftButtonAction();
    }

    @Override
    public RecipeHolder<GunSmithTableRecipe> taczaddon$getSelectedRecipe() {
        return this.selectedRecipe;
    }

    @Override
    public void taczaddon$setSelectedRecipe(RecipeHolder<GunSmithTableRecipe> value) {
        this.selectedRecipe = value;
    }

    @Override
    public List<ResourceLocation> taczaddon$getSelectedRecipeList() {
        return this.selectedRecipeList;
    }

    @Override
    public void taczaddon$setSelectedRecipeList(List<ResourceLocation> value) {
        this.selectedRecipeList = value;
    }

    @Override
    public int taczaddon$getIndexPage() {
        return this.indexPage;
    }

    @Override
    public void taczaddon$setIndexPage(int value) {
        this.indexPage = value;
    }

    @Override
    public ResourceLocation taczaddon$getSelectedType() {
        return this.selectedType;
    }

    @Override
    public void taczaddon$setSelectedType(ResourceLocation value) {
        this.selectedType = value;
    }

    @Override
    public Map<ResourceLocation, List<ResourceLocation>> taczaddon$getRecipes() {
        return this.recipes;
    }

    @Override
    public LinkedHashMap<ResourceLocation, TabConfig> taczaddon$getRecipeKeys() {
        return this.recipeKeys;
    }

    @Override
    public int taczaddon$getTypePage() {
        return this.typePage;
    }

    @Override
    public void taczaddon$setTypePage(int value) {
        this.typePage = value;
    }

    @Override
    public GunSmithTableMenu taczaddon$getMenu() {
        return this.menu;
    }

    @Override
    public int taczaddon$getLeftPos() {
        return this.leftPos;
    }

    @Override
    public int taczaddon$getTopPos() {
        return this.topPos;
    }

    @Override
    public void taczaddon$addWidget(net.minecraft.client.gui.components.AbstractWidget widget) {
        addRenderableWidget(widget);
    }

    @Override
    public RecipeHolder<GunSmithTableRecipe> taczaddon$getSelectedRecipe(ResourceLocation id) {
        return getSelectedRecipe(id);
    }

    @Override
    public void taczaddon$getPlayerIngredientCount(RecipeHolder<GunSmithTableRecipe> recipe) {
        getPlayerIngredientCount(recipe);
    }

    @Override
    public void taczaddon$updateIngredientCount() {
        updateIngredientCount();
    }
}
