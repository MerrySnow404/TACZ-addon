package com.mafuyu404.taczaddon.refit;

import com.mafuyu404.taczaddon.rule.RuleRegistry;
import com.mafuyu404.taczaddon.storage.VirtualInventory;

import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.IAttachment;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.api.item.builder.AttachmentItemBuilder;
import com.tacz.guns.network.message.ServerMessageRefreshRefitScreen;
import com.tacz.guns.resource.index.CommonAttachmentIndex;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import java.util.*;
import javax.annotation.Nullable;

/**
 * Builds short-lived liberated attachment catalogs from TaCZ's current common
 * attachment index. No mutable ItemStack is cached across calls or worlds.
 */
public final class LiberateAttachmentService {
    public static final int CANDIDATE_START_SLOT = 9;

    private static final Comparator<ResourceLocation> RESOURCE_LOCATION_ORDER =
            Comparator.comparing(ResourceLocation::getNamespace)
                    .thenComparing(ResourceLocation::getPath);

    private LiberateAttachmentService() {
    }

    public static boolean isEnabled(ServerPlayer player) {
        return player.level()
                .getGameRules()
                .getBoolean(RuleRegistry.LIBERATE_ATTACHMENT);
    }

    /**
     * Returns the real inventory unchanged unless liberation is enabled.
     */
    public static Inventory createInventory(
            Inventory realInventory,
            boolean enabled
    ) {
        if (!enabled) {
            return realInventory;
        }
        return createLiberatedInventory(realInventory);
    }

    /**
     * Layout: the real selected gun reference at its real hotbar index,
     * compatible independent candidates from slot 9 onward, then one sink.
     */
    public static VirtualInventory createLiberatedInventory(
            Inventory realInventory
    ) {
        ItemStack gunStack = realInventory.getSelected();
        List<Candidate> candidates = getCompatibleCandidates(gunStack);
        int sinkSlot = CANDIDATE_START_SLOT + candidates.size();

        VirtualInventory virtualInventory = new VirtualInventory(
                sinkSlot + 1,
                realInventory.player,
                true
        ).withSinkSlot(sinkSlot);
        virtualInventory.selected = realInventory.selected;

        if (realInventory.selected >= 0
                && realInventory.selected < CANDIDATE_START_SLOT) {
            // Deliberately retain the same object: TaCZ mutates gun NBT in-place.
            virtualInventory.setItem(
                    realInventory.selected,
                    gunStack
            );
        }

        for (int index = 0; index < candidates.size(); index++) {
            virtualInventory.setItem(
                    CANDIDATE_START_SLOT + index,
                    candidates.get(index).stack().copy()
            );
        }
        return virtualInventory;
    }

    public static List<Candidate> getCompatibleCandidates(
            ItemStack gunStack
    ) {
        IGun gun = IGun.getIGunOrNull(gunStack);
        if (gun == null) {
            return List.of();
        }

        Map<AttachmentType, Map<ResourceLocation, ItemStack>> byType =
                new EnumMap<>(AttachmentType.class);
        for (AttachmentType type : AttachmentType.values()) {
            if (type != AttachmentType.NONE) {
                byType.put(
                        type,
                        new TreeMap<>(RESOURCE_LOCATION_ORDER)
                );
            }
        }

        for (Map.Entry<ResourceLocation, CommonAttachmentIndex> entry
                : TimelessAPI.getAllCommonAttachmentIndex()) {
            CommonAttachmentIndex index = entry.getValue();
            if (index == null
                    || index.getPojo() == null
                    || index.getPojo().isHidden()
                    || index.getType() == null
                    || index.getType() == AttachmentType.NONE) {
                continue;
            }

            ItemStack candidate = AttachmentItemBuilder.create()
                    .setId(entry.getKey())
                    .build();
            IAttachment attachment =
                    IAttachment.getIAttachmentOrNull(candidate);
            if (attachment == null) {
                continue;
            }

            ResourceLocation attachmentId =
                    attachment.getAttachmentId(candidate);
            AttachmentType actualType = attachment.getType(candidate);
            Map<ResourceLocation, ItemStack> typeEntries =
                    byType.get(actualType);
            if (attachmentId == null
                    || typeEntries == null
                    || actualType != index.getType()) {
                continue;
            }

            typeEntries.putIfAbsent(attachmentId, candidate);
        }

        List<Candidate> result = new ArrayList<>();
        Set<ResourceLocation> seenIds = new HashSet<>();
        for (AttachmentType type : AttachmentType.values()) {
            if (type == AttachmentType.NONE) {
                continue;
            }
            Map<ResourceLocation, ItemStack> typeEntries = byType.get(type);
            if (typeEntries == null) {
                continue;
            }
            for (Map.Entry<ResourceLocation, ItemStack> entry
                    : typeEntries.entrySet()) {
                if (!seenIds.add(entry.getKey())) {
                    continue;
                }
                ItemStack candidate = entry.getValue().copy();
                if (gun.allowAttachment(gunStack, candidate)) {
                    result.add(new Candidate(
                            entry.getKey(),
                            type,
                            candidate
                    ));
                }
            }
        }
        return List.copyOf(result);
    }

    public static Optional<Candidate> findCandidate(
            VirtualInventory inventory,
            ResourceLocation attachmentId
    ) {
        int sinkSlot = inventory.getSinkSlot();
        for (int slot = CANDIDATE_START_SLOT;
             slot >= 0 && slot < sinkSlot;
             slot++) {
            ItemStack stack = inventory.getItem(slot);
            IAttachment attachment =
                    IAttachment.getIAttachmentOrNull(stack);
            if (attachment == null) {
                continue;
            }
            ResourceLocation actualId =
                    attachment.getAttachmentId(stack);
            if (attachmentId.equals(actualId)) {
                return Optional.of(new Candidate(
                        actualId,
                        attachment.getType(stack),
                        stack.copy()
                ));
            }
        }
        return Optional.empty();
    }

    public static boolean isValidIndex(
            Inventory inventory,
            int slot
    ) {
        return isSlotInRange(inventory.getContainerSize(), slot);
    }

    public static boolean isSlotInRange(
            int inventorySize,
            int slot
    ) {
        return inventorySize >= 0
                && slot >= 0
                && slot < inventorySize;
    }

    public static boolean isValidCandidate(
            @Nullable ResourceLocation requestedId,
            @Nullable ResourceLocation actualId,
            @Nullable AttachmentType requestedType,
            @Nullable AttachmentType actualType,
            boolean attachmentLocked,
            boolean allowed
    ) {
        return requestedId != null
                && requestedId.equals(actualId)
                && requestedType != null
                && requestedType != AttachmentType.NONE
                && requestedType == actualType
                && !attachmentLocked
                && allowed;
    }

    static boolean isCurrentGunSelection(
            int inventorySize,
            int gunSlot,
            int selectedSlot,
            boolean sameMainHandReference,
            boolean isGun
    ) {
        return isSlotInRange(inventorySize, gunSlot)
                && gunSlot == selectedSlot
                && sameMainHandReference
                && isGun;
    }

    @Nullable
    public static ItemStack getCurrentRealGun(
            ServerPlayer player,
            int gunSlot
    ) {
        Inventory inventory = player.getInventory();
        if (!isValidIndex(inventory, gunSlot)) {
            return null;
        }

        ItemStack gunStack = inventory.getItem(gunSlot);
        if (!isCurrentGunSelection(
                inventory.getContainerSize(),
                gunSlot,
                inventory.selected,
                gunStack == player.getMainHandItem(),
                IGun.getIGunOrNull(gunStack) != null
        )) {
            return null;
        }
        return gunStack;
    }

    public static void refreshRefitScreen(ServerPlayer player) {
        com.tacz.guns.network.NetworkHandler.sendToClientPlayer(
                new ServerMessageRefreshRefitScreen(),
                player
        );
    }

    public record Candidate(
            ResourceLocation id,
            AttachmentType type,
            ItemStack stack
    ) {
    }
}
