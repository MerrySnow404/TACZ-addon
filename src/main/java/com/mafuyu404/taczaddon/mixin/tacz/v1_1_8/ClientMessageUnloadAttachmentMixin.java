package com.mafuyu404.taczaddon.mixin.tacz.v1_1_8;

import com.mafuyu404.taczaddon.refit.LiberatedRefitService;

import com.tacz.guns.network.message.ClientMessageUnloadAttachment;
import net.minecraftforge.network.NetworkEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/**
 * Keeps the stable handle/enqueueWork boundary and performs liberated
 * unloads entirely on the server before TaCZ's original worker runs.
 */
@Mixin(value = ClientMessageUnloadAttachment.class, remap = false)
public abstract class ClientMessageUnloadAttachmentMixin {
    @Redirect(
            method =
                    "handle(Lcom/tacz/guns/network/message/"
                            + "ClientMessageUnloadAttachment;"
                            + "Ljava/util/function/Supplier;)V",
            at = @At(
                    value = "INVOKE",
                    target =
                            "Lnet/minecraftforge/network/NetworkEvent$Context;"
                                    + "enqueueWork(Ljava/lang/Runnable;)"
                                    + "Ljava/util/concurrent/CompletableFuture;",
                    remap = false
            ),
            remap = false,
            require = 1
    )
    private static CompletableFuture taczaddon$enqueueValidatedUnload(
            NetworkEvent.Context context,
            Runnable originalWork,
            ClientMessageUnloadAttachment message,
            Supplier<NetworkEvent.Context> contextSupplier
    ) {
        ClientMessageUnloadAttachmentAccess access =
                (ClientMessageUnloadAttachmentAccess) message;
        return LiberatedRefitService.enqueueNativeUnload(
                context,
                originalWork,
                access.taczaddon$getGunSlotIndex(),
                access.taczaddon$getAttachmentType()
        );
    }
}
