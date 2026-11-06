package com.mafuyu404.taczaddon.refit;

import com.mafuyu404.taczaddon.compat.TaczOemCompat;
import com.tacz.guns.api.item.IAttachment;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import java.util.Optional;

final class AttachmentRefitValidation {
    private static final int HOTBAR_SIZE = 9;

    /** The refit screen only ever operates on the selected main-hand slot. */
    static boolean isValidGunSlot(Inventory inventory, int gunSlot) {
        return gunSlot >= 0 && gunSlot < HOTBAR_SIZE && gunSlot == inventory.selected;
    }

    /**
     * Server-side virtual permission check, then canonical stack construction and marking. The
     * client never supplies the ItemStack or the marker.
     */
    static Optional<ItemStack> buildValidatedVirtualAttachment(
            ServerPlayer player, ItemStack gunStack, ResourceLocation attachmentId) {
        var oem = TaczOemCompat.createAllowed(gunStack, attachmentId);
        if (oem.isPresent()) return oem;
        if (!VirtualAttachmentRules.canUseVirtualAttachment(player, gunStack, attachmentId)) {
            return Optional.empty();
        }

        return VirtualAttachmentRules.findAttachmentStack(attachmentId)
                .map(VirtualAttachmentData::markVirtual);
    }

    /**
     * Physical source slots are restricted to the main inventory (Inventory.items, 0..35).
     * Armor/offhand/backpack slots are never valid sources, matching the "physical return stays in
     * the main inventory" storage domain.
     */
    static Optional<ItemStack> getValidatedPhysicalAttachment(
            Inventory inventory, int sourceSlot, ResourceLocation expectedId) {
        if (sourceSlot < 0 || sourceSlot >= inventory.items.size()) {
            return Optional.empty();
        }

        if (sourceSlot == inventory.selected) {
            return Optional.empty();
        }

        ItemStack stack = inventory.getItem(sourceSlot);

        if (stack.isEmpty()) {
            return Optional.empty();
        }

        IAttachment attachment = IAttachment.getIAttachmentOrNull(stack);

        if (attachment == null) {
            return Optional.empty();
        }

        ResourceLocation actualId = attachment.getAttachmentId(stack);

        if (!expectedId.equals(actualId)) {
            return Optional.empty();
        }

        return Optional.of(stack);
    }
}
