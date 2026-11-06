package com.mafuyu404.taczaddon.compat.createstorage;

import com.mafuyu404.taczaddon.compat.curios.CuriosCompat;

import net.fxnt.fxntstorage.backpack.BackpackItem;
import net.fxnt.fxntstorage.backpack.inventory.BackpackContainer;
import net.fxnt.fxntstorage.backpack.inventory.BackpackSlotLayout;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.wrapper.RangedWrapper;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.function.Predicate;

final class CreateStorageBackpacksCompatInner {
    private CreateStorageBackpacksCompatInner() {}

    static boolean visit(Player player, Predicate<IItemHandler> visitor, boolean mutate) {
        Set<ItemStack> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        Predicate<ItemStack> visitStack = stack -> {
            if (stack.isEmpty() || !(stack.getItem() instanceof BackpackItem) || !seen.add(stack)) return false;
            try {
                return visitContents(player, stack, visitor, mutate);
            } finally {
                if (mutate) {
                    player.getInventory().setChanged();
                    player.inventoryMenu.broadcastChanges();
                    player.containerMenu.broadcastChanges();
                    // Curios observes stack NBT changes during its normal equipment sync tick.
                }
            }
        };
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            if (visitStack.test(player.getInventory().getItem(slot))) return true;
        }
        Predicate<IItemHandler> curiosVisitor = handler -> {
            for (int slot = 0; slot < handler.getSlots(); slot++) {
                if (visitStack.test(handler.getStackInSlot(slot))) return true;
            }
            return false;
        };
        return mutate ? CuriosCompat.mutateHandlers(player, curiosVisitor)
                : CuriosCompat.visitHandlers(player, curiosVisitor);
    }

    static boolean visitContents(Player player, ItemStack stack, Predicate<IItemHandler> visitor, boolean mutate) {
        // Queries and upstream constructor side effects must never mutate the live stack.
        BackpackContainer container = new BackpackContainer(player, stack.copy());
        var items = BackpackSlotLayout.createLayout().items();
        IItemHandler handler = new RangedWrapper(container.getItemHandler(),
                items.getStartIndex(), items.getStartIndex() + items.getCount());
        try {
            return visitor.test(handler);
        } finally {
            if (mutate) {
                // Keep unrelated settings. Also persist partial extractions and ammo-box NBT edits.
                stack.getOrCreateTagElement("BlockEntityTag").merge(container.saveItemsToStack());
            }
        }
    }
}
