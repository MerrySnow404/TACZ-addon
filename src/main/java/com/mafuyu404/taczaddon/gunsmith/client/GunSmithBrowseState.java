package com.mafuyu404.taczaddon.gunsmith.client;

import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class GunSmithBrowseState {

    /**
     * Client-session browse state.
     *
     * <p>This is intentionally not saved to config/disk. It only remembers the last position while
     * the client is running.
     */
    public record BrowseState(
            ResourceLocation tableId,
            ResourceLocation selectedType,
            ResourceLocation selectedRecipeId,
            int typePage,
            int indexPage,
            int attachmentPropIndex) {}

    private static final Map<ResourceLocation, BrowseState> BROWSE_STATES = new HashMap<>();

    public static void saveBrowseState(
            ResourceLocation tableId,
            ResourceLocation selectedType,
            ResourceLocation selectedRecipeId,
            int typePage,
            int indexPage,
            int attachmentPropIndex) {
        if (tableId == null) {
            return;
        }

        BROWSE_STATES.put(
                tableId,
                new BrowseState(
                        tableId,
                        selectedType,
                        selectedRecipeId,
                        Math.max(0, typePage),
                        Math.max(0, indexPage),
                        Math.max(0, attachmentPropIndex)));
    }

    public static Optional<BrowseState> getBrowseState(ResourceLocation tableId) {
        if (tableId == null) {
            return Optional.empty();
        }

        return Optional.ofNullable(BROWSE_STATES.get(tableId));
    }

    public static void clearBrowseState() {
        BROWSE_STATES.clear();
    }
}
