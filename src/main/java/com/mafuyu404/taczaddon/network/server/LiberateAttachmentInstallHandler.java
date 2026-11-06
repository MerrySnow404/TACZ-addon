package com.mafuyu404.taczaddon.network.server;

import com.mafuyu404.taczaddon.network.LiberateAttachmentInstallPacket;
import com.mafuyu404.taczaddon.refit.LiberateAttachmentService;
import com.mafuyu404.taczaddon.storage.VirtualInventory;

import com.tacz.guns.api.item.IAttachment;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.resource.modifier.AttachmentPropertyManager;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import java.util.Optional;
import java.util.function.Supplier;
import javax.annotation.Nullable;

public final class LiberateAttachmentInstallHandler {
    private LiberateAttachmentInstallHandler() {}
    public static void handle(
            LiberateAttachmentInstallPacket message,
            Supplier<NetworkEvent.Context> contextSupplier
    ) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> handleOnServer(
                message,
                context.getSender()
        ));
        context.setPacketHandled(true);
    }

    private static void handleOnServer(
            LiberateAttachmentInstallPacket message,
            @Nullable ServerPlayer player
    ) {
        if (player == null) {
            return;
        }
        if (!LiberateAttachmentService.isEnabled(player)) {
            reject(player);
            return;
        }

        ItemStack gunStack =
                LiberateAttachmentService.getCurrentRealGun(
                        player,
                        message.gunSlotIndex()
                );
        if (gunStack == null) {
            reject(player);
            return;
        }

        IGun gun = IGun.getIGunOrNull(gunStack);
        if (gun == null || gun.hasAttachmentLock(gunStack)) {
            reject(player);
            return;
        }

        VirtualInventory virtualInventory =
                LiberateAttachmentService.createLiberatedInventory(
                        player.getInventory()
                );
        Optional<LiberateAttachmentService.Candidate> resolved =
                LiberateAttachmentService.findCandidate(
                        virtualInventory,
                        message.attachmentId()
                );
        if (resolved.isEmpty()) {
            reject(player);
            return;
        }

        ItemStack candidate = resolved.get().stack().copy();
        IAttachment attachment =
                IAttachment.getIAttachmentOrNull(candidate);
        if (attachment == null) {
            reject(player);
            return;
        }

        ResourceLocation actualId =
                attachment.getAttachmentId(candidate);
        AttachmentType actualType = attachment.getType(candidate);
        if (!LiberateAttachmentService.isValidCandidate(
                message.attachmentId(),
                actualId,
                resolved.get().type(),
                actualType,
                gun.hasAttachmentLock(gunStack),
                gun.allowAttachment(gunStack, candidate)
        )) {
            reject(player);
            return;
        }

        ItemStack replaced = gun.getAttachment(gunStack, actualType);
        gun.installAttachment(gunStack, candidate);
        virtualInventory.add(replaced);
        AttachmentPropertyManager.postChangeEvent(player, gunStack);

        if (actualType == AttachmentType.EXTENDED_MAG) {
            gun.dropAllAmmo(player, gunStack);
        }

        player.inventoryMenu.broadcastChanges();
        LiberateAttachmentService.refreshRefitScreen(player);
    }

    private static void reject(ServerPlayer player) {
        LiberateAttachmentService.refreshRefitScreen(player);
    }

}
