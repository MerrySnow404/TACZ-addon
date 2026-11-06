package com.mafuyu404.taczaddon.client.refit;

import com.mafuyu404.taczaddon.refit.RefitSourceResolver;
import com.mafuyu404.taczaddon.storage.api.InventoryAccess;
import com.mafuyu404.taczaddon.storage.api.RefitSourceLocator;

import com.google.common.collect.ImmutableList;
import net.minecraft.core.NonNullList;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.List;
import java.util.Objects;
import javax.annotation.Nullable;

/**
 * Client-only display inventory for TaCZ's GunRefitScreen candidate list.
 *
 * The display slots after the normal player range never identify a real
 * player inventory slot. Every click must resolve through {@link #originFor}.
 */
@OnlyIn(Dist.CLIENT)
public final class RefitDisplayInventory extends Inventory {
    private final int playerSlotCount;
    private final List<RefitSourceResolver.RefitExternalCandidate>
            externalCandidates;

    public RefitDisplayInventory(
            Inventory realInventory,
            List<RefitSourceResolver.RefitExternalCandidate>
                    externalCandidates
    ) {
        super(Objects.requireNonNull(
                realInventory.player,
                "real inventory player"
        ));

        this.playerSlotCount = realInventory.getContainerSize();
        this.externalCandidates =
                externalCandidates == null
                        ? List.of()
                        : List.copyOf(externalCandidates);

        NonNullList<ItemStack> displayItems = NonNullList.withSize(
                this.playerSlotCount + this.externalCandidates.size(),
                ItemStack.EMPTY
        );

        for (int slot = 0; slot < this.playerSlotCount; slot++) {
            ItemStack stack = realInventory.getItem(slot);
            displayItems.set(
                    slot,
                    stack == null || stack.isEmpty()
                            ? ItemStack.EMPTY
                            : stack.copy()
            );
        }

        for (int index = 0;
             index < this.externalCandidates.size();
             index++) {
            RefitSourceResolver.RefitExternalCandidate candidate =
                    this.externalCandidates.get(index);
            displayItems.set(
                    this.playerSlotCount + index,
                    candidate.displayStack().copy()
            );
        }

        InventoryAccess accessor =
                (InventoryAccess) (Object) this;
        accessor.setItems(displayItems);
        accessor.setCompartments(
                ImmutableList.of(
                        accessor.getItems(),
                        this.armor,
                        this.offhand
                )
        );

        this.selected = realInventory.selected;
    }

    @Override
    public int getContainerSize() {
        return this.items.size();
    }

    public int getPlayerSlotCount() {
        return this.playerSlotCount;
    }

    public OriginMapping originFor(int displaySlot) {
        if (displaySlot >= 0 && displaySlot < this.playerSlotCount) {
            return new OriginMapping(
                    Origin.PLAYER,
                    displaySlot,
                    null
            );
        }

        int externalIndex =
                displaySlot - this.playerSlotCount;
        if (externalIndex >= 0
                && externalIndex < this.externalCandidates.size()) {
            return new OriginMapping(
                    Origin.EXTERNAL,
                    -1,
                    this.externalCandidates
                            .get(externalIndex)
                            .locator()
            );
        }

        return new OriginMapping(Origin.UNKNOWN, -1, null);
    }

    @Nullable
    public RefitSourceLocator externalLocatorFor(int displaySlot) {
        OriginMapping mapping = originFor(displaySlot);
        return mapping.origin() == Origin.EXTERNAL
                ? mapping.locator()
                : null;
    }

    public enum Origin {
        PLAYER,
        EXTERNAL,
        UNKNOWN
    }

    public record OriginMapping(
            Origin origin,
            int realSlot,
            @Nullable RefitSourceLocator locator
    ) {
    }
}
