package com.mafuyu404.taczaddon.refit;

import com.mafuyu404.taczaddon.inventory.ItemStackData;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.*;

public final class AttachmentUnlocks {
    public static final String UNLOCKED_ATTACHMENTS_TAG = "UnlockedAttachments";

    /*
     * Backward-compatibility map for guns created by the previous
     * CombinedItems implementation.
     *
     * New guns no longer depend on this cache.
     */
    private static final Map<ResourceLocation, Set<ResourceLocation>> LEGACY_CACHE =
            new HashMap<>();

    private AttachmentUnlocks() {}

    /** Builds the legacy reverse lookup used by old CombinedItems tags. */
    public static synchronized void cacheLegacyUnlocks(
            ResourceLocation itemId, List<ResourceLocation> attachmentIds) {
        if (itemId == null) {
            return;
        }

        Set<ResourceLocation> cachedIds =
                LEGACY_CACHE.computeIfAbsent(itemId, ignored -> new LinkedHashSet<>());

        for (ResourceLocation attachmentId : attachmentIds) {
            if (attachmentId != null) {
                cachedIds.add(attachmentId);
            }
        }
    }

    public static synchronized void clearLegacyCache() {
        LEGACY_CACHE.clear();
    }

    /**
     * Writes actual attachment IDs to the gun.
     *
     * <p>This removes the runtime dependency on recipe-constructor cache state.
     */
    public static void addUnlockedAttachments(
            CompoundTag customData, Collection<ResourceLocation> attachmentIds) {
        ListTag storedIds;

        if (customData.contains(UNLOCKED_ATTACHMENTS_TAG, Tag.TAG_LIST)) {
            storedIds = customData.getList(UNLOCKED_ATTACHMENTS_TAG, Tag.TAG_STRING);
        } else {
            storedIds = new ListTag();
        }

        Set<String> existingIds = new LinkedHashSet<>();

        for (Tag tag : storedIds) {
            existingIds.add(tag.getAsString());
        }

        for (ResourceLocation attachmentId : attachmentIds) {
            if (attachmentId == null) {
                continue;
            }

            String serializedId = attachmentId.toString();

            if (existingIds.add(serializedId)) {
                storedIds.add(StringTag.valueOf(serializedId));
            }
        }

        customData.put(UNLOCKED_ATTACHMENTS_TAG, storedIds);
    }

    public static Set<ResourceLocation> getResult(ItemStack gunStack) {
        if (gunStack.isEmpty()) {
            return Set.of();
        }

        CompoundTag customData = ItemStackData.getCustomDataCopy(gunStack);

        Set<ResourceLocation> result = new LinkedHashSet<>();

        readDirectAttachmentIds(customData, result);
        readLegacyCombinedItems(customData, result);

        return Set.copyOf(result);
    }

    private static void readDirectAttachmentIds(
            CompoundTag customData, Set<ResourceLocation> result) {
        if (!customData.contains(UNLOCKED_ATTACHMENTS_TAG, Tag.TAG_LIST)) {
            return;
        }

        ListTag attachmentIds = customData.getList(UNLOCKED_ATTACHMENTS_TAG, Tag.TAG_STRING);

        for (Tag tag : attachmentIds) {
            ResourceLocation attachmentId = ResourceLocation.tryParse(tag.getAsString());

            if (attachmentId != null) {
                result.add(attachmentId);
            }
        }
    }

    private static synchronized void readLegacyCombinedItems(
            CompoundTag customData, Set<ResourceLocation> result) {
        if (!customData.contains("CombinedItems", Tag.TAG_LIST)) {
            return;
        }

        ListTag combinedItems = customData.getList("CombinedItems", Tag.TAG_STRING);

        for (Tag tag : combinedItems) {
            ResourceLocation itemId = ResourceLocation.tryParse(tag.getAsString());

            if (itemId == null) {
                continue;
            }

            Set<ResourceLocation> legacyUnlocks = LEGACY_CACHE.get(itemId);

            if (legacyUnlocks != null) {
                result.addAll(legacyUnlocks);
            }
        }
    }

    public static ResourceLocation getItemRegistryId(Item item) {
        if (item == null) {
            return null;
        }

        return BuiltInRegistries.ITEM.getKey(item);
    }
}
