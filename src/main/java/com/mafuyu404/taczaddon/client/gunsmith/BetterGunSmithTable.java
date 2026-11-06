package com.mafuyu404.taczaddon.client.gunsmith;

import net.minecraft.resources.ResourceLocation;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import javax.annotation.Nullable;

/**
 * Client-session-only browse memory for TaCZ gunsmith tables.
 *
 * State is keyed by the data-driven table definition id. It is not persisted
 * to disk and never participates in server crafting authorization.
 */
public final class BetterGunSmithTable {
    private static final int MAX_REMEMBERED_TABLES = 64;

    private static final Map<ResourceLocation, BrowseState> BROWSE_STATES =
            new LinkedHashMap<>(16, 0.75F, true) {
                @Override
                protected boolean removeEldestEntry(
                        Map.Entry<ResourceLocation, BrowseState> eldest
                ) {
                    return size() > MAX_REMEMBERED_TABLES;
                }
            };

    private BetterGunSmithTable() {
    }

    public record BrowseState(
            @Nullable ResourceLocation selectedType,
            @Nullable ResourceLocation selectedRecipeId,
            int typePage,
            int indexPage,
            int attachmentPropIndex
    ) {
        public BrowseState(ResourceLocation selectedType, ResourceLocation selectedRecipeId,
                           int typePage, int indexPage) {
            this(selectedType, selectedRecipeId, typePage, indexPage, 0);
        }

        public BrowseState {
            typePage = Math.max(0, typePage);
            indexPage = Math.max(0, indexPage);
            attachmentPropIndex = Math.max(0, attachmentPropIndex);
        }
    }

    public static void saveBrowseState(ResourceLocation tableId, ResourceLocation type,
                                       ResourceLocation recipe, int typePage, int indexPage) {
        saveBrowseState(tableId, type, recipe, typePage, indexPage, 0);
    }

    public static synchronized void saveBrowseState(
            ResourceLocation tableDefinitionId,
            @Nullable ResourceLocation selectedType,
            @Nullable ResourceLocation selectedRecipeId,
            int typePage,
            int indexPage,
            int attachmentPropIndex
    ) {
        if (tableDefinitionId == null) {
            return;
        }

        BROWSE_STATES.put(
                tableDefinitionId,
                new BrowseState(
                        selectedType,
                        selectedRecipeId,
                        typePage,
                        indexPage,
                        attachmentPropIndex
                )
        );
    }

    public static synchronized Optional<BrowseState> getBrowseState(
            ResourceLocation tableDefinitionId
    ) {
        if (tableDefinitionId == null) {
            return Optional.empty();
        }

        return Optional.ofNullable(
                BROWSE_STATES.get(tableDefinitionId)
        );
    }

    public static synchronized void clearBrowseState(
            ResourceLocation tableDefinitionId
    ) {
        if (tableDefinitionId != null) {
            BROWSE_STATES.remove(tableDefinitionId);
        }
    }

    public static synchronized void clearAllBrowseStates() {
        BROWSE_STATES.clear();
    }
}
