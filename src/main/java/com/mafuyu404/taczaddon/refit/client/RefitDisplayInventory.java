package com.mafuyu404.taczaddon.refit.client;

import com.mafuyu404.taczaddon.inventory.VirtualInventory;
import com.mafuyu404.taczaddon.refit.RefitSourceResolver.ExternalCandidate;

import net.minecraft.world.entity.player.Inventory;

import java.util.*;

/** Display copies only. Physical player slot indices retain their original meaning. */
public final class RefitDisplayInventory extends VirtualInventory {
    private final Map<Integer, ExternalCandidate> externalByDisplaySlot = new HashMap<>();
    private final int displaySize;

    public RefitDisplayInventory(Inventory real, List<ExternalCandidate> external) {
        super(real.items.size() + external.size(), real.player);
        displaySize = real.items.size() + external.size();
        selected = real.selected;
        for (int slot = 0; slot < real.items.size(); slot++)
            setItem(slot, real.getItem(slot).copy());
        for (int index = 0; index < external.size(); index++) {
            int slot = real.items.size() + index;
            var entry = external.get(index);
            setItem(slot, entry.displayStack().copy());
            externalByDisplaySlot.put(slot, entry);
        }
    }

    @Override
    public int getContainerSize() {
        return displaySize;
    }

    public ExternalCandidate externalAt(int displaySlot) {
        return externalByDisplaySlot.get(displaySlot);
    }
}
