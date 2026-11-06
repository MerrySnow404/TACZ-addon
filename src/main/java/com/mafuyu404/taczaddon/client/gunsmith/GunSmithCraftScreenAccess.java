package com.mafuyu404.taczaddon.client.gunsmith;

import com.mafuyu404.taczaddon.gunsmith.CraftingTransaction;

import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;

public interface GunSmithCraftScreenAccess {
    boolean taczaddon$acceptCraftResult(
            int containerId,
            long requestId,
            boolean success,
            int craftedExecutions,
            ItemStack outputPerCraft,
            @Nullable CraftingTransaction.CraftFailure failure
    );

    /**
     * Advances the in-flight craft bookkeeping. A timed-out request is
     * released without being re-sent.
     */
    void taczaddon$tickCraftState();
}
