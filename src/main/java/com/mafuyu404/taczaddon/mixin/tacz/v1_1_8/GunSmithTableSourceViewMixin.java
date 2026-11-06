package com.mafuyu404.taczaddon.mixin.tacz.v1_1_8;

import com.mafuyu404.taczaddon.client.gunsmith.GunSmithExternalSourceState;
import com.mafuyu404.taczaddon.client.gunsmith.GunSmithSourceScreenAccess;
import com.mafuyu404.taczaddon.compat.tacz.api.TaczGunSmithScreenAccess;

import com.tacz.guns.client.gui.GunSmithTableScreen;
import com.tacz.guns.crafting.GunSmithTableRecipe;
import com.tacz.guns.inventory.GunSmithTableMenu;
import it.unimi.dsi.fastutil.ints.Int2IntArrayMap;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(
        value = GunSmithTableScreen.class,
        remap = false
)
public abstract class GunSmithTableSourceViewMixin
        extends AbstractContainerScreen<GunSmithTableMenu>
        implements GunSmithSourceScreenAccess {

    @Shadow
    private Int2IntArrayMap playerIngredientCount;

    @Unique
    private final GunSmithExternalSourceState
            taczaddon$sourceState =
            new GunSmithExternalSourceState();

    protected GunSmithTableSourceViewMixin(
            GunSmithTableMenu menu,
            Inventory inventory,
            Component title
    ) {
        super(
                menu,
                inventory,
                title
        );
    }

    @Override
    public GunSmithSourceScreenAccess.AcceptResult
    taczaddon$acceptSourceSnapshot(
            int containerId,
            long requestId,
            long sourceRevision,
            List<ItemStack> externalStacks,
            boolean externalSourcesAuthorized,
            boolean displayTruncated,
            int[] aggregateCounts
    ) {
        GunSmithSourceScreenAccess.AcceptResult result =
                this.taczaddon$sourceState
                        .acceptSourceSnapshot(
                                containerId,
                                requestId,
                                sourceRevision,
                                externalSourcesAuthorized,
                                displayTruncated,
                                externalStacks,
                                aggregateCounts
                        );

        if (result
                == GunSmithSourceScreenAccess.AcceptResult.UPDATED) {
            this.taczaddon$applyAggregateCounts();
        }

        return result;
    }

    @Override
    public boolean
    taczaddon$externalSourcesAuthorized() {
        return this.taczaddon$sourceState
                .externalSourcesAuthorized();
    }

    /**
     * Compatibility view for the currently selected recipe only.
     *
     * Never return aggregate data belonging to a different recipe.
     */
    @Override
    public int[]
    taczaddon$aggregateIngredientCounts() {
        GunSmithTableRecipe selected =
                ((TaczGunSmithScreenAccess) (Object) this)
                        .taczaddon$getSelectedRecipe();

        if (selected == null) {
            return new int[0];
        }

        int[] counts =
                this.taczaddon$sourceState
                        .aggregateCountsFor(
                                selected.getId()
                        );

        return counts == null
                ? new int[0]
                : counts;
    }

    @Override
    public void taczaddon$requestSourceRefresh() {
        GunSmithTableRecipe selected =
                ((TaczGunSmithScreenAccess) (Object) this)
                        .taczaddon$getSelectedRecipe();

        this.taczaddon$sourceState
                .requestSourceRefresh(
                        this.menu.containerId,
                        selected == null
                                ? null
                                : selected.getId()
                );
    }

    @Override
    public void taczaddon$tickSourceRefresh() {
        this.taczaddon$sourceState
                .tickSourceRefresh(
                        this.menu.containerId
                );
    }

    @Override
    public void taczaddon$onScreenInit() {
        GunSmithTableRecipe selected =
                ((TaczGunSmithScreenAccess) (Object) this)
                        .taczaddon$getSelectedRecipe();

        this.taczaddon$sourceState
                .onScreenInit(
                        this.menu.containerId,
                        selected == null
                                ? null
                                : selected.getId()
                );
    }

    @Override
    public List<ItemStack>
    taczaddon$getExternalDisplayStacks() {
        return this.taczaddon$sourceState
                .getExternalDisplayStacks();
    }

    @Inject(
            method = "getPlayerIngredientCount("
                    + "Lcom/tacz/guns/crafting/"
                    + "GunSmithTableRecipe;)V",
            at = @At("HEAD"),
            cancellable = true,
            remap = false,
            require = 1
    )
    private void taczaddon$combineExternalSourceCounts(
            GunSmithTableRecipe recipe,
            CallbackInfo ci
    ) {
        /*
         * This method is TaCZ's semantic point for:
         *
         * - initial selected recipe;
         * - recipe selection changes;
         * - browse/filter selection repair;
         * - ingredient-count recalculation.
         *
         * Observe recipe identity before considering whether an external
         * snapshot is currently usable.
         */
        this.taczaddon$sourceState.observeRecipe(
                this.menu.containerId,
                recipe == null
                        ? null
                        : recipe.getId()
        );

        if (recipe == null) {
            return;
        }

        if (!this.taczaddon$sourceState
                .externalSourcesAuthorized()) {
            return;
        }

        Int2IntArrayMap counts =
                this.taczaddon$aggregateCountsFor(
                        recipe
                );

        if (counts == null) {
            /*
             * No snapshot for this exact recipe yet.
             *
             * Do not cancel TaCZ here: its native player-inventory count is
             * the safe temporary fallback until the server reply arrives.
             */
            return;
        }

        this.playerIngredientCount =
                counts;

        ci.cancel();
    }

    @Unique
    private void taczaddon$applyAggregateCounts() {
        TaczGunSmithScreenAccess access =
                (TaczGunSmithScreenAccess) (Object) this;

        GunSmithTableRecipe recipe =
                access.taczaddon$getSelectedRecipe();

        if (recipe == null) {
            return;
        }

        Int2IntArrayMap counts =
                this.taczaddon$aggregateCountsFor(
                        recipe
                );

        if (counts != null) {
            access.taczaddon$setPlayerIngredientCount(
                    counts
            );
        }
    }

    @Unique
    private Int2IntArrayMap
    taczaddon$aggregateCountsFor(
            GunSmithTableRecipe recipe
    ) {
        if (!this.taczaddon$sourceState
                .externalSourcesAuthorized()) {
            return null;
        }

        int[] aggregate =
                this.taczaddon$sourceState
                        .aggregateCountsFor(
                                recipe.getId()
                        );

        if (aggregate == null
                || aggregate.length
                != recipe.getInputs().size()) {
            return null;
        }

        Int2IntArrayMap counts =
                new Int2IntArrayMap(
                        aggregate.length
                );

        for (int index = 0;
             index < aggregate.length;
             index++) {
            counts.put(
                    index,
                    aggregate[index]
            );
        }

        return counts;
    }
}