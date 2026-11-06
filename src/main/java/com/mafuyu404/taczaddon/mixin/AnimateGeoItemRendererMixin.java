package com.mafuyu404.taczaddon.mixin;

import com.mafuyu404.taczaddon.config.ClientSyncedConfig;
import com.tacz.guns.api.client.animation.statemachine.LuaAnimationStateMachine;
import com.tacz.guns.client.animation.statemachine.ItemAnimationStateContext;
import com.tacz.guns.client.renderer.item.AnimateGeoItemRenderer;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@SuppressWarnings({"rawtypes", "unchecked"})
@Mixin(value = AnimateGeoItemRenderer.class, remap = false)
public abstract class AnimateGeoItemRendererMixin {

    @Shadow
    public abstract LuaAnimationStateMachine getStateMachine(ItemStack stack);

    @Shadow
    public abstract ItemAnimationStateContext initContext(
            ItemStack stack, Player player, float partialTick);

    /**
     * This is the important extra fix.
     *
     * <p>Some outer first-person animation code asks the renderer: "how long should I wait before
     * switching the displayed item?"
     *
     * <p>If we only skip tryExit(), the animation disappears, but the outer item-switch delay can
     * still remain.
     */
    @Inject(method = "getPutAwayDuration", at = @At("HEAD"), cancellable = true)
    private void taczaddon$fastSwapPutAwayDuration(
            ItemStack stack, CallbackInfoReturnable<Long> cir) {
        if (ClientSyncedConfig.enableFastSwapGun()) {
            cir.setReturnValue(0L);
        }
    }

    /**
     * Also force getPutAwayTime() to 0.
     *
     * <p>createAnimationInstance().triggerPutAway() directly calls:
     * AnimateGeoItemRenderer.this.getPutAwayTime(this.lastItem)
     */
    @Inject(method = "getPutAwayTime", at = @At("HEAD"), cancellable = true)
    private void taczaddon$fastSwapPutAwayTime(ItemStack stack, CallbackInfoReturnable<Long> cir) {
        if (ClientSyncedConfig.enableFastSwapGun()) {
            cir.setReturnValue(0L);
        }
    }

    /** Skip put-away animation. */
    @Inject(method = "tryExit", at = @At("HEAD"), cancellable = true)
    private void taczaddon$skipPutAwayAnimationWhenFastSwap(
            ItemStack stack, long putAwayTime, CallbackInfo ci) {
        if (!ClientSyncedConfig.enableFastSwapGun()) {
            return;
        }

        LuaAnimationStateMachine stateMachine = this.getStateMachine(stack);
        if (stateMachine != null) {
            stateMachine.processContextIfExist(
                    context -> {
                        if (context instanceof ItemAnimationStateContext itemContext) {
                            itemContext.setPutAwayTime(0.0F);
                        }
                    });

            if (stateMachine.isInitialized()) {
                // Skip:
                // stateMachine.trigger("put_away");

                stateMachine.exit();

                // Let re-init / item-switch become available immediately.
                stateMachine.setExitingTime(0L);
            }
        }

        ci.cancel();
    }
}
