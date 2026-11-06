package com.mafuyu404.taczaddon.mixin.tacz.v1_1_8;

import com.mafuyu404.taczaddon.client.gunsmith.GunSmithCompatibilityService;
import com.mafuyu404.taczaddon.client.gunsmith.GunSmithPropertyFilter;
import com.mafuyu404.taczaddon.client.gunsmith.GunSmithPropertyFilterAccess;
import com.mafuyu404.taczaddon.client.ui.DropDown;
import com.mafuyu404.taczaddon.compat.tacz.api.TaczGunSmithScreenAccess;

import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.client.gui.GunSmithTableScreen;
import com.tacz.guns.crafting.GunSmithTableRecipe;
import com.tacz.guns.inventory.GunSmithTableMenu;
import com.tacz.guns.resource.modifier.AttachmentPropertyManager;
import it.unimi.dsi.fastutil.Pair;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Mixin(value = GunSmithTableScreen.class, remap = false)
public abstract class GunSmithTablePropertyFilterMixin extends AbstractContainerScreen<GunSmithTableMenu>
        implements GunSmithPropertyFilterAccess {
    @Shadow private List<ResourceLocation> selectedRecipeList;
    @Shadow private int indexPage;
    @Shadow private int typePage;
    @Shadow @Final private LinkedHashMap<ResourceLocation, ?> recipeKeys;
    @Shadow public abstract void updateIngredientCount();
    @Shadow private GunSmithTableRecipe getSelectedRecipe(ResourceLocation id) { throw new AssertionError(); }

    @Unique private List<Component> taczaddon$properties;
    @Unique private Map<ResourceLocation, String> taczaddon$attachmentData;
    @Unique private int taczaddon$propertyIndex;
    @Unique private DropDown taczaddon$dropdown;

    protected GunSmithTablePropertyFilterMixin(GunSmithTableMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    @Inject(method = "init()V", at = @At("HEAD"), remap = true, require = 1)
    private void taczaddon$prepareFilter(CallbackInfo ci) {
        if (!taczaddon$supportsPropertyFilter()) {
            taczaddon$properties = List.of();
            taczaddon$attachmentData = Map.of();
            taczaddon$propertyIndex = 0;
            taczaddon$dropdown = null;
            return;
        }
        if (taczaddon$properties != null) return;
        taczaddon$properties = GunSmithPropertyFilter.catalogKeys(
                AttachmentPropertyManager.getModifiers().keySet(), Language.getInstance()::has)
                .stream().map(key -> (Component) Component.translatable(key)).toList();
        Map<ResourceLocation, String> data = new HashMap<>();
        TimelessAPI.getAllClientAttachmentIndex().forEach(entry -> {
            StringBuilder text = new StringBuilder();
            entry.getValue().getData().getModifier().values().forEach(property ->
                    property.getComponents().forEach(component -> text.append(component.getString())));
            data.put(entry.getKey(), text.toString());
        });
        taczaddon$attachmentData = Map.copyOf(data);
        GunSmithCompatibilityService.restorePropertyFilter(this, menu.getBlockId());
    }

    // The constructor also classifies, before client initialization. Index zero is deliberately neutral.
    @Redirect(method = "classifyRecipes()V", at = @At(value = "INVOKE",
            target = "Ljava/util/List;add(Ljava/lang/Object;)Z"), require = 1, expect = 1, allow = 1)
    private boolean taczaddon$filterRecipe(List<Object> list, Object entry) {
        if (!taczaddon$supportsPropertyFilter()
                || taczaddon$propertyIndex == 0
                || taczaddon$properties == null) {
            return list.add(entry);
        }
        if (!(entry instanceof Pair<?, ?> pair) || !(pair.right() instanceof ResourceLocation recipeId)) {
            return list.add(entry);
        }
        return GunSmithPropertyFilter.matches(recipeId,
                taczaddon$properties.get(taczaddon$propertyIndex).getString(),
                taczaddon$attachmentData, this::getSelectedRecipe) && list.add(entry);
    }

    @Inject(method = "classifyRecipes()V", at = @At("TAIL"), require = 1)
    private void taczaddon$clampFilteredPages(CallbackInfo ci) {
        if (!taczaddon$supportsPropertyFilter()) return;
        indexPage = Math.max(0, Math.min(indexPage,
                selectedRecipeList == null ? 0 : Math.max(0, (selectedRecipeList.size() - 1) / 6)));
        typePage = Math.max(0, Math.min(typePage, Math.max(0, (recipeKeys.size() - 1) / 7)));
    }

    @Inject(method = "init()V", at = @At("TAIL"), remap = true, require = 1)
    private void taczaddon$addDropdown(CallbackInfo ci) {
        if (!taczaddon$supportsPropertyFilter()) return;
        int y = Math.max(0, topPos - 22);
        taczaddon$dropdown = new DropDown(leftPos + 143, y, 150, height - y,
                taczaddon$properties, taczaddon$propertyIndex, index -> {
                    taczaddon$setAttachmentPropertyIndex(index);
                    GunSmithCompatibilityService.saveBrowseState(
                            (TaczGunSmithScreenAccess) (Object) this, menu.getBlockId());
                    // Verified 1.1.8-hotfix: this calls init once, which classifies, repairs selection,
                    // refreshes ingredient counts and clears/rebuilds all widgets.
                    updateIngredientCount();
                });
        addRenderableWidget(taczaddon$dropdown);
    }

    @Inject(method = "mouseScrolled(DDD)Z", at = @At("HEAD"), remap = true,
            cancellable = true, require = 1)
    private void taczaddon$scrollFilter(double x, double y, double delta, CallbackInfoReturnable<Boolean> cir) {
        if (!taczaddon$supportsPropertyFilter()) return;
        if (taczaddon$dropdown != null && taczaddon$dropdown.mouseScrolled(x, y, delta)) {
            cir.setReturnValue(true);
        } else if (x > leftPos + 143 && x < leftPos + 237 && y > topPos + 66 && y < topPos + 151
                && (selectedRecipeList == null || selectedRecipeList.isEmpty())) {
            indexPage = 0;
            cir.setReturnValue(true);
        }
    }

    @Override public boolean taczaddon$handlePropertyClick(double x, double y, int button) {
        return taczaddon$supportsPropertyFilter()
                && taczaddon$dropdown != null
                && taczaddon$dropdown.mouseClicked(x, y, button);
    }
    @Override public int taczaddon$getAttachmentPropertyIndex() {
        return taczaddon$supportsPropertyFilter() ? taczaddon$propertyIndex : 0;
    }
    @Override public int taczaddon$getAttachmentPropertyCount() {
        return taczaddon$supportsPropertyFilter() && taczaddon$properties != null
                ? taczaddon$properties.size() : 0;
    }
    @Override public void taczaddon$setAttachmentPropertyIndex(int index) {
        if (!taczaddon$supportsPropertyFilter()) {
            taczaddon$propertyIndex = 0;
            return;
        }
        taczaddon$propertyIndex = Math.max(0, Math.min(Math.max(0, taczaddon$getAttachmentPropertyCount() - 1), index));
    }

    @Unique
    private boolean taczaddon$supportsPropertyFilter() {
        return GunSmithPropertyFilter.supportsWorkbench(menu.getBlockId());
    }
}
