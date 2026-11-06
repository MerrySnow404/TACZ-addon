package com.mafuyu404.taczaddon.refit;

import static com.mafuyu404.taczaddon.refit.AttachmentRefitEffects.*;
import static com.mafuyu404.taczaddon.refit.AttachmentRefitValidation.*;

import com.mafuyu404.taczaddon.inventory.ExternalSourceExtraction;
import com.mafuyu404.taczaddon.inventory.MainInventoryTransaction;
import com.mojang.logging.LogUtils;
import com.tacz.guns.api.item.IAttachment;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.attachment.AttachmentType;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import org.slf4j.Logger;

import java.util.Optional;

/**
 * Server-authoritative attachment refit transaction service.
 *
 * <p>Both {@link com.mafuyu404.taczaddon.refit.network.VirtualAttachmentRefitPacket} and the mixin
 * that takes over TaCZ's {@code ClientMessageUnloadAttachment} delegate here, so there is exactly
 * one ownership/transaction implementation.
 *
 * <p>Ownership model:
 *
 * <ul>
 *   <li>PHYSICAL attachments (no explicit virtual marker) are returned to the player's main
 *       inventory on replacement/unload. The return is a fail-safe {@link
 *       MainInventoryTransaction}: simulated preflight first, and any unexpected insertion failure
 *       rolls the gun and the main inventory back instead of losing the item.
 *   <li>VIRTUAL attachments carry an explicit CUSTOM_DATA marker and are retired (discarded) on
 *       replacement/unload. They never enter any real inventory and never require inventory
 *       capacity.
 * </ul>
 *
 * <p>Side effects (postChangeEvent, EXTENDED_MAG ammo drop, inventory sync, screen refresh) only
 * run after the whole transaction has committed.
 */
public final class AttachmentRefitService {

    private static final Logger LOGGER = LogUtils.getLogger();

    /** sourceSlot value used by the refit screen for virtual attachments. */
    public static final int VIRTUAL_SOURCE_SLOT = -1;

    private static final int HOTBAR_SIZE = 9;

    private static final String NO_SPACE_KEY = "gui.tacz.gun_refit.unload.no_space";

    private AttachmentRefitService() {}

    public enum InstallResult {
        SUCCESS,
        REJECTED,
        NO_SPACE,
        INTERNAL_FAILURE
    }

    /** Extends the existing ownership model with a reversible nearby source. */
    public static InstallResult installExternal(
            ServerPlayer player,
            int gunSlot,
            RefitSource source,
            int sourceSlot,
            ResourceLocation expectedId,
            AttachmentType expectedType) {
        Inventory inventory = player.getInventory();
        if (!RefitSourceResolver.canUseSources(player)
                || !isValidGunSlot(inventory, gunSlot)
                || !source.isValid()
                || !(source.handler()
                        instanceof net.neoforged.neoforge.items.IItemHandlerModifiable handler)
                || sourceSlot < 0
                || sourceSlot >= handler.getSlots()) return InstallResult.REJECTED;
        ItemStack gunStack = inventory.getItem(gunSlot);
        ItemStack beforeRefit = gunStack.copy();
        IGun gun = IGun.getIGunOrNull(gunStack);
        ItemStack current = handler.getStackInSlot(sourceSlot);
        IAttachment attachment = IAttachment.getIAttachmentOrNull(current);
        if (gun == null
                || gun.hasAttachmentLock(gunStack)
                || attachment == null
                || !expectedId.equals(attachment.getAttachmentId(current))
                || expectedType != attachment.getType(current)
                || !gun.allowAttachment(gunStack, current)) return InstallResult.REJECTED;
        ExternalSourceExtraction extraction = new ExternalSourceExtraction(handler, sourceSlot);
        if (!extraction.simulateOne()) return InstallResult.REJECTED;
        ItemStack oldAttachment =
                gun.getAttachment(player.registryAccess(), gunStack, expectedType).copy();
        ItemStack physicalReturn =
                VirtualAttachmentData.isVirtual(oldAttachment) ? ItemStack.EMPTY : oldAttachment;
        MainInventoryTransaction transaction = MainInventoryTransaction.begin(inventory);
        try {
            ItemStack extracted = extraction.extractOne();
            gun.installAttachment(player.registryAccess(), gunStack, extracted);
            ItemStack remainder = transaction.commitInsert(physicalReturn);
            source.markChanged();
            if (!remainder.isEmpty()) {
                var dropped =
                        new net.minecraft.world.entity.item.ItemEntity(
                                player.level(),
                                player.getX(),
                                player.getY(),
                                player.getZ(),
                                remainder);
                dropped.setDefaultPickUpDelay();
                dropped.setTarget(player.getUUID());
                if (!player.level().addFreshEntity(dropped))
                    throw new IllegalStateException("Attachment drop rejected");
            }
            transaction.close();
        } catch (RuntimeException exception) {
            logInternalFailure("external-install", expectedId, exception);
            try {
                extraction.rollback();
                source.markChanged();
            } catch (RuntimeException recoveryException) {
                LOGGER.error(
                        "CRITICAL: external source rollback failed at {} slot {}",
                        source.locator(),
                        sourceSlot,
                        recoveryException);
            }
            handleRollbackOutcome(transaction, player, inventory, "external-install", expectedId);
            return InstallResult.INTERNAL_FAILURE;
        }
        postChange(player, gunStack, expectedType, inventory, beforeRefit);
        return InstallResult.SUCCESS;
    }

    public enum UnloadResult {
        SUCCESS,
        REJECTED,
        NO_SPACE,
        INTERNAL_FAILURE
    }

    /**
     * Handles one install/replacement transaction. The transaction covers the four source
     * combinations:
     *
     * <pre>
     * old \ new       VIRTUAL            PHYSICAL
     * VIRTUAL         install+discard    install+discard, consume source
     * NONE/PHYSICAL   fail-safe return   TaCZ native swap semantics
     * </pre>
     *
     * <p>For PHYSICAL -> VIRTUAL the old physical attachment is preflighted into the main inventory
     * before the gun is touched. If the real insert ever fails afterwards, the gun attachment and
     * the main inventory are rolled back to their previous state and no side effects run.
     */
    public static InstallResult install(
            ServerPlayer player, int sourceSlot, int gunSlot, ResourceLocation attachmentId) {
        if (player == null || attachmentId == null) {
            return InstallResult.REJECTED;
        }

        Inventory inventory = player.getInventory();

        if (!isValidGunSlot(inventory, gunSlot)) {
            return InstallResult.REJECTED;
        }

        ItemStack gunStack = inventory.getItem(gunSlot);
        ItemStack beforeRefit = gunStack.copy();

        IGun gun = IGun.getIGunOrNull(gunStack);

        if (gun == null || gun.hasAttachmentLock(gunStack)) {
            return InstallResult.REJECTED;
        }

        boolean newVirtual = sourceSlot == VIRTUAL_SOURCE_SLOT;

        ItemStack attachmentStack;

        if (newVirtual) {
            Optional<ItemStack> virtualStack =
                    buildValidatedVirtualAttachment(player, gunStack, attachmentId);

            if (virtualStack.isEmpty()) {
                return InstallResult.REJECTED;
            }

            attachmentStack = virtualStack.get();
        } else {
            Optional<ItemStack> physicalStack =
                    getValidatedPhysicalAttachment(inventory, sourceSlot, attachmentId);

            if (physicalStack.isEmpty()) {
                return InstallResult.REJECTED;
            }

            attachmentStack = physicalStack.get();
        }

        IAttachment attachment = IAttachment.getIAttachmentOrNull(attachmentStack);

        if (attachment == null) {
            return InstallResult.REJECTED;
        }

        ResourceLocation actualAttachmentId = attachment.getAttachmentId(attachmentStack);

        if (!attachmentId.equals(actualAttachmentId)) {
            return InstallResult.REJECTED;
        }

        if (!gun.allowAttachment(gunStack, attachmentStack)) {
            return InstallResult.REJECTED;
        }

        AttachmentType actualType = attachment.getType(attachmentStack);

        if (actualType == AttachmentType.NONE) {
            return InstallResult.REJECTED;
        }

        var registryAccess = player.registryAccess();

        ItemStack oldAttachment = gun.getAttachment(registryAccess, gunStack, actualType).copy();

        boolean oldVirtual = VirtualAttachmentData.isVirtual(oldAttachment);

        if (!newVirtual) {
            /*
             * PHYSICAL install: TaCZ native source-slot swap semantics.
             *
             * The old attachment replaces the consumed source stack (or the
             * source slot is emptied when the old one was virtual). A source
             * stack that unexpectedly carries the virtual marker is never
             * "converted" to physical: the marker stays and the server keeps
             * treating it as virtual provenance.
             *
             * Order is install-first, setItem-second, so an install exception
             * cannot leave the source slot mutated. This path intentionally
             * follows TaCZ native swap semantics and is NOT covered by the
             * defensive MainInventoryTransaction rollback used by the
             * physical-return paths (a source-slot swap cannot partially
             * insert into arbitrary inventory slots).
             */
            gun.installAttachment(registryAccess, gunStack, attachmentStack);

            inventory.setItem(sourceSlot, oldVirtual ? ItemStack.EMPTY : oldAttachment);

            postChange(player, gunStack, actualType, inventory, beforeRefit);

            return InstallResult.SUCCESS;
        }

        /*
         * VIRTUAL install. Old virtual attachments are simply retired.
         * Old physical attachments go through a fail-safe main-inventory
         * return transaction.
         */
        if (oldVirtual || oldAttachment.isEmpty()) {
            gun.installAttachment(registryAccess, gunStack, attachmentStack);

            postChange(player, gunStack, actualType, inventory, beforeRefit);

            return InstallResult.SUCCESS;
        }

        MainInventoryTransaction transaction = MainInventoryTransaction.begin(inventory);

        try {
            if (!transaction.canFullyInsert(oldAttachment)) {
                transaction.close();
                sendNoSpace(player);
                return InstallResult.NO_SPACE;
            }

            gun.installAttachment(registryAccess, gunStack, attachmentStack);

            ItemStack remainder = transaction.commitInsert(oldAttachment);

            if (!remainder.isEmpty()) {
                logInternalFailure("install", attachmentId, remainder);
                handleRollbackOutcome(transaction, player, inventory, "install", attachmentId);
                return InstallResult.INTERNAL_FAILURE;
            }

            transaction.close();
        } catch (RuntimeException exception) {
            logInternalFailure("install", attachmentId, exception);
            handleRollbackOutcome(transaction, player, inventory, "install", attachmentId);
            return InstallResult.INTERNAL_FAILURE;
        }

        postChange(player, gunStack, actualType, inventory, beforeRefit);

        return InstallResult.SUCCESS;
    }

    /**
     * Handles one unload transaction.
     *
     * <p>VIRTUAL attachments are simply unloaded and retired; inventory capacity is irrelevant.
     * PHYSICAL attachments are preflighted into the main inventory before the gun is modified, and
     * any unexpected insertion failure rolls both back.
     */
    public static UnloadResult unload(ServerPlayer player, int gunSlot, AttachmentType type) {
        if (player == null || type == null || type == AttachmentType.NONE) {
            return UnloadResult.REJECTED;
        }

        Inventory inventory = player.getInventory();

        if (!isValidGunSlot(inventory, gunSlot)) {
            return UnloadResult.REJECTED;
        }

        ItemStack gunStack = inventory.getItem(gunSlot);
        ItemStack beforeRefit = gunStack.copy();

        IGun gun = IGun.getIGunOrNull(gunStack);

        if (gun == null || gun.hasAttachmentLock(gunStack)) {
            return UnloadResult.REJECTED;
        }

        var registryAccess = player.registryAccess();

        ItemStack attachedStack = gun.getAttachment(registryAccess, gunStack, type);

        if (attachedStack.isEmpty()) {
            return UnloadResult.REJECTED;
        }

        boolean virtual = VirtualAttachmentData.isVirtual(attachedStack);

        if (virtual) {
            gun.unloadAttachment(registryAccess, gunStack, type);

            postChange(player, gunStack, type, inventory, beforeRefit);

            return UnloadResult.SUCCESS;
        }

        MainInventoryTransaction transaction = MainInventoryTransaction.begin(inventory);

        try {
            if (!transaction.canFullyInsert(attachedStack)) {
                transaction.close();
                sendNoSpace(player);
                return UnloadResult.NO_SPACE;
            }

            gun.unloadAttachment(registryAccess, gunStack, type);

            ItemStack remainder = transaction.commitInsert(attachedStack);

            if (!remainder.isEmpty()) {
                logInternalFailure("unload", type, remainder);
                handleRollbackOutcome(transaction, player, inventory, "unload", type);
                return UnloadResult.INTERNAL_FAILURE;
            }

            transaction.close();
        } catch (RuntimeException exception) {
            logInternalFailure("unload", type, exception);
            handleRollbackOutcome(transaction, player, inventory, "unload", type);
            return UnloadResult.INTERNAL_FAILURE;
        }

        postChange(player, gunStack, type, inventory, beforeRefit);

        return UnloadResult.SUCCESS;
    }

    private static void sendNoSpace(ServerPlayer player) {
        player.sendSystemMessage(Component.translatable(NO_SPACE_KEY));
    }
}
