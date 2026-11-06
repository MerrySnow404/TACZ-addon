package com.mafuyu404.taczaddon.client.gunsmith;

import net.minecraft.world.item.ItemStack;

import java.util.List;

public interface GunSmithSourceScreenAccess {
    enum AcceptResult {
        UNCHANGED,
        UPDATED,
        REJECTED
    }

    AcceptResult taczaddon$acceptSourceSnapshot(
            int containerId,
            long requestId,
            long sourceRevision,
            List<ItemStack> externalStacks,
            boolean externalSourcesAuthorized,
            boolean displayTruncated,
            int[] aggregateCounts
    );

    /**
     * Server-confirmed authorization for external material sources on the
     * currently open menu.
     */
    boolean taczaddon$externalSourcesAuthorized();

    /** Aggregated per-input counts, complete regardless of display truncation. */
    int[] taczaddon$aggregateIngredientCounts();

    void taczaddon$requestSourceRefresh();

    void taczaddon$tickSourceRefresh();

    void taczaddon$onScreenInit();

    List<ItemStack> taczaddon$getExternalDisplayStacks();
}
