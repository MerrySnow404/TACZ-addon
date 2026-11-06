package com.mafuyu404.taczaddon.mixin;

import com.mafuyu404.taczaddon.compat.sophisticated.client.SophisticatedBackpacksClientSync;

import net.neoforged.neoforge.network.handling.IPayloadContext;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Method;
import java.util.UUID;

@Pseudo
@Mixin(
        targets = "net.p3pp3rf1y.sophisticatedbackpacks.network.BackpackContentsPayload",
        remap = false)
public abstract class BackpackContentsPayloadMixin {
    @Inject(
            method = "handlePayload",
            at = @At("TAIL"),
            remap = false,
            /*
             * TaczAddonMixinPlugin is the primary contract gate and skips
             * this mixin when the target bytecode does not match. require = 0
             * is only a secondary safety net so a still-unexpected method
             * shape degrades to a missing optional feature instead of a
             * startup crash.
             */
            require = 0)
    private static void taczaddon$afterBackpackContentsReceived(
            @Coerce Object payload, IPayloadContext context, CallbackInfo ci) {
        if (payload == null) {
            return;
        }

        try {
            Method uuidAccessor = payload.getClass().getMethod("backpackUuid");

            Method contentsAccessor = payload.getClass().getMethod("backpackContents");

            Object contents = contentsAccessor.invoke(payload);
            if (contents == null) {
                return;
            }

            Object rawUuid = uuidAccessor.invoke(payload);
            if (rawUuid instanceof UUID uuid) {
                SophisticatedBackpacksClientSync.onContentsUpdated(uuid);
            }
        } catch (ReflectiveOperationException ignored) {
            /*
             * Optional compatibility hook. Do not break payload handling if a
             * future Sophisticated Backpacks release changes record accessors.
             */
        }
    }
}
