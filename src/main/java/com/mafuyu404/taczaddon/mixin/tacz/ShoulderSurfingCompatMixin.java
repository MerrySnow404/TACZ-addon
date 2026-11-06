package com.mafuyu404.taczaddon.mixin.tacz;

import com.mafuyu404.taczaddon.compat.camera.ShoulderSurfing5Compat;
import com.mafuyu404.taczaddon.compat.camera.ShoulderSurfingDispatch;

import com.tacz.guns.compat.shouldersurfing.ShoulderSurfingCompat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Bypasses TaCZ 1.1.8's obsolete SSR 4.x crosshair path only when a
 * structurally verified SSR 5.x API generation is installed.
 *
 * <p>The TaCZ binary contract only proves that the target method exists. It
 * proves nothing about Shoulder Surfing's own API generation, so the two
 * capabilities stay separate: the injection point is validated against TaCZ,
 * while the runtime decision is delegated to the optional-safe SSR facade.
 *
 * <p>Dispatch (see {@link ShoulderSurfingDispatch}):
 *
 * <ul>
 *     <li>absent or legacy - do not cancel, TaCZ's own native path runs;</li>
 *     <li>verified 5.x - answer from the addon backend;</li>
 *     <li>unknown generation, or a verified 5.x backend that broke - answer
 *     {@code false} and never enter TaCZ's legacy backend, whose classes would
 *     fail to link.</li>
 * </ul>
 */
@Mixin(value = ShoulderSurfingCompat.class, remap = false)
public class ShoulderSurfingCompatMixin {
    @Inject(
            method = "showCrosshair",
            at = @At("HEAD"),
            cancellable = true,
            remap = false,
            require = 1
    )
    private static void taczaddon$showCrosshairWithSsr5(
            CallbackInfoReturnable<Boolean> cir
    ) {
        /*
         * A non-null override means the addon owns the decision. That includes
         * the blocked case: an unknown generation (or a 5.x backend that broke)
         * must be answered here, because TaCZ's legacy backend cannot link
         * against it.
         */
        Boolean override = ShoulderSurfing5Compat.crosshairOverride();
        if (override != null) {
            cir.setReturnValue(override);
        }
    }
}
