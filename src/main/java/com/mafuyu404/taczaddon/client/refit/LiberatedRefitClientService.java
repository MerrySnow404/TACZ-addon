package com.mafuyu404.taczaddon.client.refit;

import com.mafuyu404.taczaddon.network.LiberateAttachmentInstallPacket;
import com.mafuyu404.taczaddon.network.NetworkHandler;
import com.mafuyu404.taczaddon.refit.LiberateAttachmentService;

import com.tacz.guns.api.item.IAttachment;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.client.gui.components.refit.InventoryAttachmentSlot;
import com.tacz.guns.client.sound.SoundPlayManager;
import com.tacz.guns.sound.SoundManager;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class LiberatedRefitClientService {
    private LiberatedRefitClientService() {
    }

    public static void sendAttachmentIdInsteadOfVirtualSlot(
            Inventory inventory,
            Button button
    ) {
        LocalPlayer player = inventory.player instanceof LocalPlayer value
                ? value
                : null;
        if (player == null) {
            return;
        }

        if (!(button instanceof InventoryAttachmentSlot attachmentSlot)) {
            return;
        }

        int slot = attachmentSlot.getSlotIndex();
        if (!LiberateAttachmentService.isValidIndex(inventory, slot)) {
            return;
        }

        ItemStack candidate = inventory.getItem(slot);
        IAttachment attachment =
                IAttachment.getIAttachmentOrNull(candidate);
        if (attachment == null
                || attachment.getType(candidate)
                == AttachmentType.NONE) {
            return;
        }

        ResourceLocation attachmentId =
                attachment.getAttachmentId(candidate);
        if (attachmentId == null) {
            return;
        }

        SoundPlayManager.playerRefitSound(
                candidate,
                player,
                SoundManager.INSTALL_SOUND
        );
        NetworkHandler.CHANNEL.sendToServer(
                new LiberateAttachmentInstallPacket(
                        inventory.selected,
                        attachmentId
                )
        );
    }
}
