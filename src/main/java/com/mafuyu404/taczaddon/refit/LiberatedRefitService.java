package com.mafuyu404.taczaddon.refit;

import com.tacz.guns.api.item.IAttachment;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.resource.modifier.AttachmentPropertyManager;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import java.util.concurrent.CompletableFuture;
import javax.annotation.Nullable;

public final class LiberatedRefitService {
    private LiberatedRefitService() {
    }

    public static CompletableFuture<?> enqueueNativeRefit(
            NetworkEvent.Context context,
            Runnable originalWork,
            int attachmentSlotIndex,
            int gunSlotIndex,
            AttachmentType attachmentType
    ) {
        return context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null
                    || !validateRefit(
                    player,
                    attachmentSlotIndex,
                    gunSlotIndex,
                    attachmentType
            )) {
                return;
            }
            originalWork.run();
        });
    }

    public static CompletableFuture<?> enqueueNativeUnload(
            NetworkEvent.Context context,
            Runnable originalWork,
            int gunSlotIndex,
            AttachmentType attachmentType
    ) {
        return context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null) {
                return;
            }

            if (!validateUnload(
                    player,
                    gunSlotIndex,
                    attachmentType
            )) {
                return;
            }

            if (LiberateAttachmentService.isEnabled(player)) {
                performLiberatedUnload(
                        player,
                        gunSlotIndex,
                        attachmentType
                );
                return;
            }

            originalWork.run();
        });
    }

    public static boolean isEnabled(ServerPlayer player) {
        return LiberateAttachmentService.isEnabled(player);
    }

    public static Inventory createInventory(
            Inventory realInventory,
            boolean enabled
    ) {
        return LiberateAttachmentService.createInventory(
                realInventory,
                enabled
        );
    }

    public static boolean isValidIndex(
            Inventory inventory,
            int slot
    ) {
        return LiberateAttachmentService.isValidIndex(inventory, slot);
    }

    public static boolean isValidCandidate(
            @Nullable ResourceLocation requestedId,
            @Nullable ResourceLocation actualId,
            @Nullable AttachmentType requestedType,
            @Nullable AttachmentType actualType,
            boolean attachmentLocked,
            boolean allowed
    ) {
        return LiberateAttachmentService.isValidCandidate(
                requestedId,
                actualId,
                requestedType,
                actualType,
                attachmentLocked,
                allowed
        );
    }

    @Nullable
    public static ItemStack getCurrentRealGun(
            ServerPlayer player,
            int gunSlot
    ) {
        return LiberateAttachmentService.getCurrentRealGun(
                player,
                gunSlot
        );
    }

    public static void refreshRefitScreen(ServerPlayer player) {
        LiberateAttachmentService.refreshRefitScreen(player);
    }

    private static boolean validateRefit(
            ServerPlayer player,
            int attachmentSlot,
            int gunSlot,
            AttachmentType claimedType
    ) {
        boolean liberated = LiberateAttachmentService.isEnabled(player);

        // Liberated clients use the addon's ID packet. Do not reinterpret a
        // virtual slot as a real inventory slot.
        if (liberated) {
            refresh(player);
            return false;
        }

        Inventory inventory =
                LiberateAttachmentService.createInventory(
                        player.getInventory(),
                        false
                );
        if (!LiberateAttachmentService.isValidIndex(
                inventory,
                attachmentSlot
        )) {
            refresh(player);
            return false;
        }

        ItemStack gunStack =
                LiberateAttachmentService.getCurrentRealGun(
                        player,
                        gunSlot
                );
        if (gunStack == null) {
            refresh(player);
            return false;
        }

        ItemStack candidate = inventory.getItem(attachmentSlot);
        IAttachment attachment =
                IAttachment.getIAttachmentOrNull(candidate);
        IGun gun = IGun.getIGunOrNull(gunStack);
        ResourceLocation attachmentId = attachment == null
                ? null
                : attachment.getAttachmentId(candidate);
        AttachmentType actualType = attachment == null
                ? null
                : attachment.getType(candidate);
        if (attachment == null
                || gun == null
                || !LiberateAttachmentService.isValidCandidate(
                attachmentId,
                attachmentId,
                claimedType,
                actualType,
                gun.hasAttachmentLock(gunStack),
                gun.allowAttachment(gunStack, candidate)
        )) {
            refresh(player);
            return false;
        }
        return true;
    }

    private static boolean validateUnload(
            ServerPlayer player,
            int gunSlot,
            AttachmentType requestedType
    ) {
        ItemStack gunStack =
                LiberateAttachmentService.getCurrentRealGun(
                        player,
                        gunSlot
                );
        if (gunStack == null
                || requestedType == null
                || requestedType == AttachmentType.NONE) {
            refresh(player);
            return false;
        }

        boolean liberated =
                LiberateAttachmentService.isEnabled(player);
        if (!liberated) {
            return true;
        }

        IGun gun = IGun.getIGunOrNull(gunStack);
        if (gun == null || gun.hasAttachmentLock(gunStack)) {
            refresh(player);
            return false;
        }

        ItemStack installed = gun.getAttachment(
                gunStack,
                requestedType
        );
        IAttachment attachment =
                IAttachment.getIAttachmentOrNull(installed);
        if (installed.isEmpty()
                || attachment == null
                || attachment.getType(installed) != requestedType
                || attachment.getAttachmentId(installed) == null) {
            refresh(player);
            return false;
        }
        return true;
    }

    private static void performLiberatedUnload(
            ServerPlayer player,
            int gunSlot,
            AttachmentType requestedType
    ) {
        try {
            ItemStack gunStack =
                    LiberateAttachmentService.getCurrentRealGun(
                            player,
                            gunSlot
                    );
            if (gunStack == null
                    || requestedType == null
                    || requestedType == AttachmentType.NONE) {
                refresh(player);
                return;
            }

            IGun gun = IGun.getIGunOrNull(gunStack);
            if (gun == null || gun.hasAttachmentLock(gunStack)) {
                refresh(player);
                return;
            }

            ItemStack installed = gun.getAttachment(
                    gunStack,
                    requestedType
            );
            IAttachment attachment =
                    IAttachment.getIAttachmentOrNull(installed);
            if (installed.isEmpty()
                    || attachment == null
                    || attachment.getType(installed)
                    != requestedType
                    || attachment.getAttachmentId(installed) == null) {
                refresh(player);
                return;
            }

            gun.unloadAttachment(gunStack, requestedType);
            AttachmentPropertyManager.postChangeEvent(
                    player,
                    gunStack
            );
            if (requestedType == AttachmentType.EXTENDED_MAG) {
                gun.dropAllAmmo(player, gunStack);
            }
            player.inventoryMenu.broadcastChanges();
            refresh(player);
        } catch (RuntimeException ignored) {
            refresh(player);
        }
    }

    private static void refresh(ServerPlayer player) {
        try {
            LiberateAttachmentService.refreshRefitScreen(player);
        } catch (RuntimeException ignored) {
            // Validation already fails closed.
        }
    }
}
