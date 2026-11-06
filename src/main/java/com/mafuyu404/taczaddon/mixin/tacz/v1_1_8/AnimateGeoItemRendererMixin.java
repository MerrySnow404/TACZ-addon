package com.mafuyu404.taczaddon.mixin.tacz.v1_1_8;

import com.mafuyu404.taczaddon.client.config.ClientSyncedConfig;

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
            ItemStack stack,
            Player player,
            float partialTick
    );

    /**
     * Fast-swap is server-owned.
     *
     * The client renderer only consumes the value synchronized from the
     * currently connected server. It must not read the deprecated local
     * FAST_SWAP_GUN config, otherwise animation timing and authoritative
     * gameplay timing can diverge.
     */
    @Inject(
            method = "getPutAwayDuration",
            at = @At("HEAD"),
            cancellable = true
    )
    private void taczaddon$fastSwapPutAwayDuration(
            ItemStack stack,
            CallbackInfoReturnable<Long> cir
    ) {
        if (ClientSyncedConfig.enableFastSwapGun()) {
            cir.setReturnValue(0L);
        }
    }

    @Inject(
            method = "getPutAwayTime",
            at = @At("HEAD"),
            cancellable = true
    )
    private void taczaddon$fastSwapPutAwayTime(
            ItemStack stack,
            CallbackInfoReturnable<Long> cir
    ) {
        if (ClientSyncedConfig.enableFastSwapGun()) {
            cir.setReturnValue(0L);
        }
    }

    @Inject(
            method = "tryExit",
            at = @At("HEAD"),
            cancellable = true
    )
    private void taczaddon$skipPutAwayAnimationWhenFastSwap(
            ItemStack stack,
            long putAwayTime,
            CallbackInfo ci
    ) {
        if (!ClientSyncedConfig.enableFastSwapGun()) {
            return;
        }

        LuaAnimationStateMachine stateMachine =
                this.getStateMachine(stack);

        if (stateMachine != null) {
            stateMachine.processContextIfExist(context -> {
                if (context instanceof ItemAnimationStateContext itemContext) {
                    itemContext.setPutAwayTime(0.0F);
                }
            });

            if (stateMachine.isInitialized()) {
                stateMachine.exit();
                stateMachine.setExitingTime(0L);
            }
        }

        ci.cancel();
    }
}