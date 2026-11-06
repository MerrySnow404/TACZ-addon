package com.mafuyu404.taczaddon.mixin.tacz.v1_1_8;

import com.mafuyu404.taczaddon.client.gunsmith.GunSmithCompatibilityService;
import com.mafuyu404.taczaddon.compat.tacz.api.TaczGunSmithScreenAccess;

import com.tacz.guns.client.gui.GunSmithTableScreen;
import com.tacz.guns.crafting.GunSmithTableRecipe;
import com.tacz.guns.inventory.GunSmithTableMenu;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;

/**
 * Remembers browse position without depending on synthetic button-lambda
 * descriptors. TaCZ intentionally reuses init() for normal page navigation,
 * so persisted state is restored only once for each screen instance.
 */
@Mixin(value = GunSmithTableScreen.class, remap = false)
public abstract class GunSmithTableBrowseMemoryMixin
        extends AbstractContainerScreen<GunSmithTableMenu> {

    @Shadow
    @Nullable
    private GunSmithTableRecipe selectedRecipe;

    @Shadow
    private List<ResourceLocation> selectedRecipeList;

    @Shadow
    private int indexPage;

    @Shadow
    @Nullable
    private ResourceLocation selectedType;

    @Shadow
    @Final
    private Map<ResourceLocation, List<ResourceLocation>> recipes;

    @Shadow
    @Final
    private LinkedHashMap<ResourceLocation, ?> recipeKeys;

    @Shadow
    private int typePage;

    @Shadow
    @Nullable
    private GunSmithTableRecipe getSelectedRecipe(
            ResourceLocation recipeId
    ) {
        throw new AssertionError();
    }

    @Shadow
    private void getPlayerIngredientCount(
            GunSmithTableRecipe recipe
    ) {
        throw new AssertionError();
    }

    @Unique
    private boolean taczaddon$initialBrowseRestoreAttempted;

    protected GunSmithTableBrowseMemoryMixin(
            GunSmithTableMenu menu,
            Inventory inventory,
            Component title
    ) {
        super(menu, inventory, title);
    }

    @Inject(
            method = "init()V",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/tacz/guns/client/gui/GunSmithTableScreen;updateSelectedRecipeAfterFiltering()V",
                    shift = At.Shift.AFTER,
                    remap = false
            ),
            remap = true,
            require = 1
    )
    private void taczaddon$restoreAfterRecipeClassification(
            CallbackInfo ci
    ) {
        if (this.taczaddon$initialBrowseRestoreAttempted) {
            return;
        }

        this.taczaddon$initialBrowseRestoreAttempted = true;

        ResourceLocation tableDefinitionId = this.menu.getBlockId();
        boolean restored =
                GunSmithCompatibilityService.restoreBrowseState(
                        (TaczGunSmithScreenAccess) (Object) this,
                        tableDefinitionId,
                        this.recipes,
                        this.recipeKeys,
                        this::getSelectedRecipe
                );
        if (restored && this.selectedRecipe != null) {
            this.getPlayerIngredientCount(this.selectedRecipe);
        }
    }

    @Inject(
            method = "init()V",
            at = @At("TAIL"),
            remap = true,
            require = 1
    )
    private void taczaddon$saveAfterInit(CallbackInfo ci) {
        this.taczaddon$saveCurrentState();
    }

    @Unique
    private void taczaddon$saveCurrentState() {
        ResourceLocation tableDefinitionId = this.menu.getBlockId();
        if (tableDefinitionId == null) {
            return;
        }

        GunSmithCompatibilityService.saveBrowseState(
                (TaczGunSmithScreenAccess) (Object) this,
                tableDefinitionId
        );
    }

}
