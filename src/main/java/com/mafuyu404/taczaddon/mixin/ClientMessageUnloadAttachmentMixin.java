package com.mafuyu404.taczaddon.mixin;

import com.mafuyu404.taczaddon.refit.AttachmentRefitService;
import com.tacz.guns.network.message.ClientMessageUnloadAttachment;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Takes over TaCZ's native {@code ClientMessageUnloadAttachment.handle(...)}.
 *
 * <p>The native handler calls {@code inventory.add(attachment)} for every unloaded attachment,
 * which materializes virtual attachments into the real inventory and refuses to unload them when
 * the inventory is full. This mixin cancels the original handle at HEAD (keeping the {@code
 * enqueueWork} threading semantics) and delegates to the unified server-side {@link
 * AttachmentRefitService#unload(ServerPlayer, int, AttachmentType)} transaction, which is
 * provenance-aware.
 *
 * <p>Version-bound: this targets the exact {@code handle} signature of TaCZ 1.21.1. The {@code
 * require = 1} makes the injection fail fast if TaCZ is updated and the target disappears.
 */
@Mixin(value = ClientMessageUnloadAttachment.class, remap = false)
public class ClientMessageUnloadAttachmentMixin {

    @Inject(
            method =
                    "handle("
                            + "Lcom/tacz/guns/network/message/"
                            + "ClientMessageUnloadAttachment;"
                            + "Lnet/neoforged/neoforge/network/handling/"
                            + "IPayloadContext;"
                            + ")V",
            at = @At("HEAD"),
            cancellable = true,
            remap = false,
            require = 1)
    private static void taczaddon$delegateUnload(
            ClientMessageUnloadAttachment packet, IPayloadContext context, CallbackInfo ci) {
        ci.cancel();

        context.enqueueWork(
                () -> {
                    if (!(context.player() instanceof ServerPlayer player)) {
                        return;
                    }

                    ClientMessageUnloadAttachmentAccessor accessor =
                            (ClientMessageUnloadAttachmentAccessor) packet;

                    AttachmentRefitService.unload(
                            player,
                            accessor.taczaddon$gunSlotIndex(),
                            accessor.taczaddon$attachmentType());
                });
    }
}
