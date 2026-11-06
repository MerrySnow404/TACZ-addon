package com.mafuyu404.taczaddon.refit;

import com.mafuyu404.taczaddon.network.NetworkHandler;
import com.mafuyu404.taczaddon.refit.network.RefreshRefitScreenPacket;
import com.tacz.guns.api.item.*;
import com.tacz.guns.api.item.attachment.AttachmentType;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

public final class RefitExternalInstallService {
    private RefitExternalInstallService() {}

    public static AttachmentRefitService.InstallResult install(
            ServerPlayer player,
            int gunSlot,
            RefitSourceLocator locator,
            ResourceLocation expectedId,
            AttachmentType expectedType) {
        AttachmentRefitService.InstallResult result = AttachmentRefitService.InstallResult.REJECTED;
        try {
            if (!RefitSourceResolver.canUseSources(player)
                    || locator == null
                    || expectedId == null
                    || expectedType == null
                    || expectedType == AttachmentType.NONE
                    || gunSlot < 0
                    || gunSlot >= 9
                    || gunSlot != player.getInventory().selected
                    || !player.level().dimension().location().equals(locator.dimension()))
                return AttachmentRefitService.InstallResult.REJECTED;
            ItemStack gunStack = player.getInventory().getItem(gunSlot);
            IGun gun = IGun.getIGunOrNull(gunStack);
            if (gun == null || gun.hasAttachmentLock(gunStack))
                return AttachmentRefitService.InstallResult.REJECTED;
            // A locator is only a hint: resolve the complete legal set again on the server.
            for (var source : RefitSourceResolver.resolveExternalSources(player)) {
                if (!source.locator().sameSource(locator)) continue;
                var handler = source.handler();
                if (!source.isValid() || locator.slot() < 0 || locator.slot() >= handler.getSlots())
                    break;
                ItemStack current = handler.getStackInSlot(locator.slot());
                IAttachment attachment = IAttachment.getIAttachmentOrNull(current);
                if (attachment == null
                        || !expectedId.equals(attachment.getAttachmentId(current))
                        || expectedType != attachment.getType(current)
                        || !gun.allowAttachment(gunStack, current)) break;
                result =
                        AttachmentRefitService.installExternal(
                                player, gunSlot, source, locator.slot(), expectedId, expectedType);
                return result;
            }
            return AttachmentRefitService.InstallResult.REJECTED;
        } catch (RuntimeException exception) {
            com.mojang.logging.LogUtils.getLogger()
                    .error(
                            "External refit failed for {} locator {}",
                            player.getUUID(),
                            locator,
                            exception);
            return AttachmentRefitService.InstallResult.INTERNAL_FAILURE;
        } finally {
            // Refresh stale displays on rejection too; all contents remain server sourced.
            if (player != null && result != AttachmentRefitService.InstallResult.SUCCESS)
                NetworkHandler.sendToClient(player, new RefreshRefitScreenPacket(true));
        }
    }
}
