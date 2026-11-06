package com.mafuyu404.taczaddon.mixin.tacz.v1_1_8;

import com.mafuyu404.taczaddon.client.gunsmith.GunSmithIngredientInteractionState;
import com.mafuyu404.taczaddon.client.gunsmith.GunSmithIngredientScreenAccess;
import com.mafuyu404.taczaddon.compat.jei.JeiCompat;

import com.tacz.guns.client.gui.GunSmithTableScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Optional;
import javax.annotation.Nullable;

/**
 * Frame-local ingredient tooltip + JEI OUTPUT navigation.
 *
 * The redirect captures the exact ItemStack TaCZ has selected for the
 * current second (tag / multi-candidate ingredients rotate), the render
 * TAIL renders the vanilla tooltip on top of everything else, and the
 * mouse release path is invoked through GunSmithIngredientScreenAccess
 * from the generic AbstractContainerScreen mixin using the release
 * coordinates directly. No global storage bridge and no global InputEvent
 * path are involved.
 */
@Mixin(value = GunSmithTableScreen.class, remap = false)
public abstract class GunSmithTableIngredientInteractionMixin
        implements GunSmithIngredientScreenAccess {

    @Unique
    @Nullable
    private GunSmithIngredientInteractionState
            taczaddon$ingredientState;

    @Unique
    private GunSmithIngredientInteractionState
    taczaddon$ingredientState() {
        if (this.taczaddon$ingredientState == null) {
            this.taczaddon$ingredientState =
                    new GunSmithIngredientInteractionState();
        }
        return this.taczaddon$ingredientState;
    }

    @Inject(
            method = "render",
            at = @At("HEAD"),
            remap = true,
            require = 1
    )
    private void taczaddon$beginIngredientFrame(
            GuiGraphics graphics,
            int mouseX,
            int mouseY,
            float partialTick,
            CallbackInfo ci
    ) {
        this.taczaddon$ingredientState().beginFrame();
    }

    @Redirect(
            method =
                    "renderIngredient("
                            + "Lnet/minecraft/client/gui/GuiGraphics;)V",
            at = @At(
                    value = "INVOKE",
                    target =
                            "Lnet/minecraft/client/gui/GuiGraphics;"
                                    + "renderFakeItem("
                                    + "Lnet/minecraft/world/item/ItemStack;II)V",
                    remap = true
            ),
            remap = false,
            require = 1
    )
    private void taczaddon$captureRenderedIngredient(
            GuiGraphics graphics,
            ItemStack itemStack,
            int x,
            int y
    ) {
        graphics.renderFakeItem(
                itemStack,
                x,
                y
        );

        this.taczaddon$ingredientState()
                .register(
                        itemStack,
                        x,
                        y
                );
    }

    @Inject(
            method = "render",
            at = @At("TAIL"),
            remap = true,
            require = 1
    )
    private void taczaddon$renderIngredientTooltip(
            GuiGraphics graphics,
            int mouseX,
            int mouseY,
            float partialTick,
            CallbackInfo ci
    ) {
        this.taczaddon$ingredientState()
                .find(mouseX, mouseY)
                .ifPresent(stack ->
                        graphics.renderTooltip(
                                Minecraft.getInstance().font,
                                stack,
                                mouseX,
                                mouseY
                        )
                );
    }

    @Override
    public boolean taczaddon$handleIngredientMouseRelease(
            double mouseX,
            double mouseY,
            int button
    ) {
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            return false;
        }

        Optional<ItemStack> target =
                this.taczaddon$ingredientState()
                        .find(
                                mouseX,
                                mouseY
                        );

        if (target.isEmpty()) {
            return false;
        }

        return JeiCompat.showRecipes(
                target.get()
        );
    }
}
