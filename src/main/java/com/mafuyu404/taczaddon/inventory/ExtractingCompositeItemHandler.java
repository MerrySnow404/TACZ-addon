package com.mafuyu404.taczaddon.inventory;

import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandlerModifiable;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class ExtractingCompositeItemHandler extends ReadOnlyCompositeItemHandler {

    private final List<ItemStack> originalStacks;
    private final List<ItemStack> workingStacks;

    public ExtractingCompositeItemHandler(Collection<CompositeItemSource> sources) {
        super(sources);

        this.originalStacks = new ArrayList<>(this.sources.size());
        this.workingStacks = new ArrayList<>(this.sources.size());

        for (CompositeItemSource source : this.sources) {
            ItemStack original = source.handler().getStackInSlot(source.slot()).copy();

            originalStacks.add(original);
            workingStacks.add(original.copy());
        }
    }

    @Override
    public @NotNull ItemStack getStackInSlot(int slot) {
        if (slot < 0 || slot >= workingStacks.size()) {
            return ItemStack.EMPTY;
        }

        return workingStacks.get(slot);
    }

    @Override
    public @NotNull ItemStack extractItem(int slot, int amount, boolean simulate) {
        CompositeItemSource source = getSource(slot);

        if (source == null || amount <= 0) {
            return ItemStack.EMPTY;
        }

        if (simulate) {
            return source.handler().extractItem(source.slot(), amount, true);
        }

        // TaCZ visits each slot once, but upgraded storage may cap each extraction
        // to a normal stack. Drain the requested amount without crossing into a
        // replacement item or changing the ammo-box working-copy transaction.
        ItemStack expected = source.handler().getStackInSlot(source.slot()).copy();
        ItemStack result = ItemStack.EMPTY;
        int remaining = amount;
        while (remaining > 0 && !expected.isEmpty()) {
            ItemStack current = source.handler().getStackInSlot(source.slot());
            if (!ItemStack.isSameItemSameComponents(expected, current)) {
                break;
            }
            ItemStack extracted = source.handler().extractItem(source.slot(), remaining, false);
            if (extracted.isEmpty()) {
                break;
            }
            if (result.isEmpty()) {
                result = extracted.copy();
            } else {
                result.grow(extracted.getCount());
            }
            remaining -= extracted.getCount();
        }
        return result;
    }

    public void commitChanges() {
        for (int slot = 0; slot < sources.size(); slot++) {
            ItemStack original = originalStacks.get(slot);
            ItemStack working = workingStacks.get(slot);

            boolean countChanged = original.getCount() != working.getCount();

            boolean componentsChanged = !ItemStack.isSameItemSameComponents(original, working);

            if (!countChanged && !componentsChanged) {
                continue;
            }

            CompositeItemSource source = sources.get(slot);

            if (source.handler() instanceof IItemHandlerModifiable modifiable) {

                modifiable.setStackInSlot(source.slot(), working.copy());
            } else {
                throw new IllegalStateException(
                        "Cannot write modified ammo box back to "
                                + source.sourceType()
                                + " slot "
                                + source.slot()
                                + ": handler is not IItemHandlerModifiable");
            }
        }
    }
}
