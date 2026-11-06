package com.mafuyu404.taczaddon.mixin.tacz.v1_1_8;

import com.mafuyu404.taczaddon.refit.LiberatedRefitService;

import com.tacz.guns.network.message.ClientMessageRefitGun;
import net.minecraftforge.network.NetworkEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/**
 * TaCZ 1.1.8-hotfix (Curse file 8141310) enqueues packet work from the
 * real handle method. Wrap that task so all player/inventory validation
 * runs on the server scheduled thread before the original worker runs.
 */
@Mixin(value = ClientMessageRefitGun.class, remap = false)
public abstract class ClientMessageRefitGunMixin {
    @Redirect(
            method =
                    "handle(Lcom/tacz/guns/network/message/"
                            + "ClientMessageRefitGun;"
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
    private static CompletableFuture taczaddon$enqueueValidatedRefit(
            NetworkEvent.Context context,
            Runnable originalWork,
            ClientMessageRefitGun message,
            Supplier<NetworkEvent.Context> contextSupplier
    ) {
        ClientMessageRefitGunAccess access =
                (ClientMessageRefitGunAccess) message;
        return LiberatedRefitService.enqueueNativeRefit(
                context,
                originalWork,
                access.taczaddon$getAttachmentSlotIndex(),
                access.taczaddon$getGunSlotIndex(),
                access.taczaddon$getAttachmentType()
        );
    }
}
