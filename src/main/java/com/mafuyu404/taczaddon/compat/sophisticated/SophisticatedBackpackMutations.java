package com.mafuyu404.taczaddon.compat.sophisticated;

import static com.mafuyu404.taczaddon.compat.sophisticated.SophisticatedContentsSync.syncBackpackContents;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.IItemHandlerModifiable;
import net.p3pp3rf1y.sophisticatedbackpacks.backpack.wrapper.IBackpackWrapper;
import net.p3pp3rf1y.sophisticatedcore.inventory.InventoryHandler;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/** Persists and synchronizes actual inventory mutations. */
final class SophisticatedBackpackMutations {
    static boolean mutateBackpackHandler(
            ServerPlayer player,
            IBackpackWrapper wrapper,
            IItemHandler handler,
            Predicate<IItemHandler> visitor) {
        List<ItemStack> before = snapshotHandler(handler);

        try {
            return visitor.test(handler);
        } finally {
            boolean changed = writeBackChangedStacks(before, handler);

            if (shouldPersistAndSynchronize(changed)) {
                if (handler instanceof InventoryHandler inventoryHandler) {
                    inventoryHandler.saveInventory();
                }

                player.getInventory().setChanged();
                player.containerMenu.broadcastChanges();

                syncBackpackContents(player, wrapper);
            }
        }
    }

    static boolean shouldPersistAndSynchronize(boolean changed) {
        return changed;
    }

    static List<ItemStack> snapshotHandler(IItemHandler handler) {
        List<ItemStack> before = new ArrayList<>(handler.getSlots());

        for (int slot = 0; slot < handler.getSlots(); slot++) {
            before.add(handler.getStackInSlot(slot).copy());
        }

        return before;
    }

    static boolean writeBackChangedStacks(List<ItemStack> before, IItemHandler handler) {
        boolean changed = false;

        for (int slot = 0; slot < handler.getSlots(); slot++) {

            ItemStack current = handler.getStackInSlot(slot);

            if (ItemStack.matches(before.get(slot), current)) {
                continue;
            }

            changed = true;

            if (handler instanceof IItemHandlerModifiable modifiable) {
                modifiable.setStackInSlot(slot, current.copy());
            }
        }

        return changed;
    }
}
