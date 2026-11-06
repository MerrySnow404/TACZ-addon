package com.mafuyu404.taczaddon.inventory;

import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class ReadOnlyCompositeItemHandler implements IItemHandler {
    protected final List<CompositeItemSource> sources;

    public ReadOnlyCompositeItemHandler(Collection<CompositeItemSource> sources) {
        this.sources = List.copyOf(sources);
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public int getSlots() {
        return sources.size();
    }

    @Override
    public @NotNull ItemStack getStackInSlot(int slot) {
        CompositeItemSource source = getSource(slot);
        if (source == null) {
            return ItemStack.EMPTY;
        }
        return source.handler().getStackInSlot(source.slot()).copy();
    }

    @Override
    public @NotNull ItemStack insertItem(int slot, @NotNull ItemStack stack, boolean simulate) {
        return stack;
    }

    @Override
    public @NotNull ItemStack extractItem(int slot, int amount, boolean simulate) {
        CompositeItemSource source = getSource(slot);
        if (source == null || amount <= 0) {
            return ItemStack.EMPTY;
        }
        // This view is for recipe/count checks only. Always simulate so callers cannot
        // accidentally mutate player, container, or backpack storage through it.
        return source.handler().extractItem(source.slot(), amount, true);
    }

    @Override
    public int getSlotLimit(int slot) {
        CompositeItemSource source = getSource(slot);
        return source == null ? 0 : source.handler().getSlotLimit(source.slot());
    }

    @Override
    public boolean isItemValid(int slot, @NotNull ItemStack stack) {
        CompositeItemSource source = getSource(slot);
        return source != null && source.handler().isItemValid(source.slot(), stack);
    }

    protected CompositeItemSource getSource(int slot) {
        if (slot < 0 || slot >= sources.size()) {
            return null;
        }
        return sources.get(slot);
    }

    public static class Builder {
        private final List<CompositeItemSource> sources = new ArrayList<>();

        public Builder addHandler(IItemHandler handler, String sourceType) {
            if (handler == null) {
                return this;
            }
            for (int slot = 0; slot < handler.getSlots(); slot++) {
                if (!handler.getStackInSlot(slot).isEmpty()) {
                    sources.add(new CompositeItemSource(handler, slot, sourceType));
                }
            }
            return this;
        }

        public ReadOnlyCompositeItemHandler buildReadOnly() {
            return new ReadOnlyCompositeItemHandler(sources);
        }

        public ExtractingCompositeItemHandler buildExtracting() {
            return new ExtractingCompositeItemHandler(sources);
        }
    }
}
