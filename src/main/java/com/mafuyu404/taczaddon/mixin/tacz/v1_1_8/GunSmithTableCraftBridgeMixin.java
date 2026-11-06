package com.mafuyu404.taczaddon.mixin.tacz.v1_1_8;

import com.mafuyu404.taczaddon.client.config.ClientSyncedConfig;
import com.mafuyu404.taczaddon.client.gunsmith.GunSmithCraftBridgeState;
import com.mafuyu404.taczaddon.client.gunsmith.GunSmithCraftRouting;
import com.mafuyu404.taczaddon.client.gunsmith.GunSmithCraftScreenAccess;
import com.mafuyu404.taczaddon.client.gunsmith.GunSmithSourceScreenAccess;
import com.mafuyu404.taczaddon.gunsmith.CraftingTransaction;

import com.tacz.guns.client.gui.GunSmithTableScreen;
import com.tacz.guns.crafting.GunSmithTableRecipe;
import com.tacz.guns.inventory.GunSmithTableMenu;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import javax.annotation.Nullable;

@Mixin(value = GunSmithTableScreen.class, remap = false)
public abstract class GunSmithTableCraftBridgeMixin
        implements GunSmithCraftScreenAccess {

    @Shadow
    @Nullable
    private GunSmithTableRecipe selectedRecipe;

    @Unique
    private final GunSmithCraftBridgeState taczaddon$craftState =
            new GunSmithCraftBridgeState();

    @Override
    public boolean taczaddon$acceptCraftResult(
            int containerId,
            long requestId,
            boolean success,
            int craftedExecutions,
            ItemStack outputPerCraft,
            @Nullable CraftingTransaction.CraftFailure failure
    ) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen != (Object) this
                || !(minecraft.screen instanceof GunSmithTableScreen screen)
                || screen.getMenu().containerId != containerId) {
            return false;
        }
        return this.taczaddon$craftState.acceptCraftResult(
                containerId,
                requestId
        );
    }

    @Override
    public void taczaddon$tickCraftState() {
        /*
         * Releases a timed-out request without re-sending it, so the craft
         * button can never stay permanently stuck in a waiting state.
         */
        this.taczaddon$craftState.tick();
    }

    @ModifyArg(
            method = "addCraftButton()V",
            at = @At(
                    value = "INVOKE",
                    target =
                            "Lnet/minecraft/client/gui/components/"
                                    + "ImageButton;<init>(IIIIIII"
                                    + "Lnet/minecraft/resources/"
                                    + "ResourceLocation;"
                                    + "Lnet/minecraft/client/gui/"
                                    + "components/Button$OnPress;)V",
                    remap = true
            ),
            index = 8,
            remap = false,
            require = 1
    )
    private Button.OnPress taczaddon$wrapCraftButton(
            Button.OnPress originalOnPress
    ) {
        return button -> {
            Minecraft minecraft = Minecraft.getInstance();
            if (this.selectedRecipe == null
                    || minecraft.player == null
                    || !(minecraft.player.containerMenu
                    instanceof GunSmithTableMenu menu)) {
                /*
                 * No routing information: keep TaCZ's own behaviour.
                 */
                originalOnPress.onPress(button);
                return;
            }

            ResourceLocation recipeId =
                    this.selectedRecipe.getId();
            boolean shiftDown = Screen.hasShiftDown();
            boolean playerHasMaterials =
                    GunSmithCraftRouting.playerInventorySatisfies(
                            minecraft.player,
                            this.selectedRecipe
                    );
            boolean extendedAuthorized =
                    minecraft.screen
                            instanceof GunSmithSourceScreenAccess sources
                            && sources
                            .taczaddon$externalSourcesAuthorized();

            if (GunSmithCraftRouting.decide(
                    shiftDown,
                    playerHasMaterials,
                    extendedAuthorized
            ) == GunSmithCraftRouting.Route.NATIVE) {
                originalOnPress.onPress(button);
                return;
            }

            /*
             * Exactly one path per press: once the extended request is sent it
             * is never re-sent and never supplemented with a native craft.
             */
            this.taczaddon$craftState.requestCraft(
                    menu.containerId,
                    recipeId,
                    shiftDown,
                    ClientSyncedConfig.batchCraftMax()
            );
        };
    }
}
