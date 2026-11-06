package com.mafuyu404.taczaddon.refit;

import com.mafuyu404.taczaddon.config.ClientRuleState;
import com.mafuyu404.taczaddon.config.RuleRegistry;
import com.mafuyu404.taczaddon.config.network.RuleSyncPacket;
import com.mafuyu404.taczaddon.inventory.VirtualInventory;
import com.mafuyu404.taczaddon.network.NetworkHandler;
import com.tacz.guns.api.item.IAttachment;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.item.AttachmentItem;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;

import java.util.*;

public final class VirtualAttachmentRules {
    /*
     * Vanilla player inventory hotbar slots occupy indices 0-8.
     * Virtual attachments begin at slot 9.
     */
    public static final int VIRTUAL_ATTACHMENT_SLOT_START = 9;

    private VirtualAttachmentRules() {}

    public static List<ItemStack> getAttachmentItems() {
        List<ItemStack> items = new ArrayList<>();

        for (AttachmentType type : AttachmentType.values()) {
            if (type == AttachmentType.NONE) {
                continue;
            }

            for (ItemStack stack : AttachmentItem.fillItemCategory(type)) {
                if (!stack.isEmpty()) {
                    items.add(stack.copy());
                }
            }
        }

        return items;
    }

    /**
     * Creates a read-oriented inventory view for TaCZ's refit screen and refit validation.
     *
     * <p>The selected gun deliberately remains the original ItemStack reference, because TaCZ
     * installs the attachment by mutating that stack.
     *
     * <p>Generated attachment stacks are defensive copies.
     */
    public static Inventory useVirtualInventory(Inventory realInventory) {
        Player player = realInventory.player;
        ItemStack selectedGun = realInventory.getSelected();

        if (player == null || selectedGun.isEmpty()) {
            return realInventory;
        }

        List<ItemStack> availableAttachments = getAttachmentItems();

        if (!isLiberated(player)) {
            Set<ResourceLocation> unlockedAttachments = AttachmentUnlocks.getResult(selectedGun);

            availableAttachments = filterAttachmentItems(availableAttachments, unlockedAttachments);

            if (availableAttachments.isEmpty()) {
                return realInventory;
            }
        }

        int virtualSize = VIRTUAL_ATTACHMENT_SLOT_START + availableAttachments.size();

        VirtualInventory virtualInventory = new VirtualInventory(virtualSize, player);

        /*
         * Preserve the real selected hotbar index.
         *
         * Do not put the same gun into all nine hotbar slots. A malicious or
         * stale packet using another gun slot will now resolve to an empty
         * stack and TaCZ will reject it.
         */
        virtualInventory.selected = realInventory.selected;
        virtualInventory.setItem(realInventory.selected, selectedGun);

        for (int index = 0; index < availableAttachments.size(); index++) {
            virtualInventory.setItem(
                    VIRTUAL_ATTACHMENT_SLOT_START + index, availableAttachments.get(index).copy());
        }

        return virtualInventory;
    }

    /**
     * The logical server must use only the authoritative gamerule.
     *
     * <p>com.mafuyu404.taczaddon.config.ClientRuleState is valid only for client-side GUI
     * presentation.
     */
    public static boolean isLiberated(Player player) {
        if (player == null) {
            return false;
        }

        if (player.level().isClientSide()) {
            return ClientRuleState.isLiberateAttachment();
        }

        return player.level().getGameRules().getBoolean(RuleRegistry.LIBERATE_ATTACHMENT);
    }

    public static List<ItemStack> filterAttachmentItems(
            List<ItemStack> allAttachments, Set<ResourceLocation> unlockedAttachmentIds) {
        if (unlockedAttachmentIds.isEmpty()) {
            return List.of();
        }

        List<ItemStack> result = new ArrayList<>();

        for (ItemStack stack : allAttachments) {
            IAttachment attachment = IAttachment.getIAttachmentOrNull(stack);

            if (attachment == null) {
                continue;
            }

            ResourceLocation attachmentId = attachment.getAttachmentId(stack);

            if (attachmentId != null && unlockedAttachmentIds.contains(attachmentId)) {
                result.add(stack.copy());
            }
        }

        return result;
    }

    public static void onRuleChange(MinecraftServer server, GameRules.BooleanValue value) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            NetworkHandler.sendToClient(player, createRuleSyncPacket(player));
        }
    }

    public static void syncRuleWhenLogin(ServerPlayer serverPlayer) {
        NetworkHandler.sendToClient(serverPlayer, createRuleSyncPacket(serverPlayer));
    }

    private static RuleSyncPacket createRuleSyncPacket(Player player) {
        boolean liberateAttachment =
                player.level().getGameRules().getBoolean(RuleRegistry.LIBERATE_ATTACHMENT);

        boolean showAttachmentDetail =
                player.level().getGameRules().getBoolean(RuleRegistry.SHOW_ATTACHMENT_DETAIL);

        return new RuleSyncPacket(liberateAttachment, showAttachmentDetail);
    }

    public static Optional<ItemStack> findAttachmentStack(ResourceLocation attachmentId) {
        if (attachmentId == null) {
            return Optional.empty();
        }

        for (ItemStack stack : getAttachmentItems()) {
            IAttachment attachment = IAttachment.getIAttachmentOrNull(stack);

            if (attachment == null) {
                continue;
            }

            ResourceLocation actualId = attachment.getAttachmentId(stack);

            if (attachmentId.equals(actualId)) {
                return Optional.of(stack.copy());
            }
        }

        return Optional.empty();
    }

    public static boolean canUseVirtualAttachment(
            Player player, ItemStack gunStack, ResourceLocation attachmentId) {
        if (player == null || gunStack.isEmpty() || attachmentId == null) {
            return false;
        }

        if (isLiberated(player)) {
            return true;
        }

        Collection<?> unlocked = AttachmentUnlocks.getResult(gunStack);

        String expectedId = attachmentId.toString();

        for (Object value : unlocked) {
            if (value instanceof ResourceLocation id) {
                if (attachmentId.equals(id)) {
                    return true;
                }
            } else if (expectedId.equals(String.valueOf(value))) {
                return true;
            }
        }

        return false;
    }
}
