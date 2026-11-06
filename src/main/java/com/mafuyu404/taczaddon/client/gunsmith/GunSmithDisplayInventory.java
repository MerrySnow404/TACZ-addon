package com.mafuyu404.taczaddon.client.gunsmith;

import com.mafuyu404.taczaddon.storage.api.InventoryAccess;

import com.google.common.collect.ImmutableList;
import net.minecraft.core.NonNullList;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Objects;

/**
 * Display-only inventory used by the TaCZ gunsmith-table ingredient counter.
 *
 * TaCZ 1.1.8-hotfix reads Inventory#items directly. This inventory therefore
 * contains copies of the player's normal item slots followed by copies of
 * external source stacks.
 *
 * No mutation performed on this object is propagated to a real inventory.
 */
public final class GunSmithDisplayInventory extends Inventory {
    public GunSmithDisplayInventory(
            Player player,
            List<ItemStack> externalStacks
    ) {
        super(Objects.requireNonNull(player, "player"));

        Inventory playerInventory = player.getInventory();
        int playerItemSlots = playerInventory.items.size();
        int externalSlots =
                externalStacks == null ? 0 : externalStacks.size();

        NonNullList<ItemStack> displayItems =
                NonNullList.withSize(
                        playerItemSlots + externalSlots,
                        ItemStack.EMPTY
                );

        for (int slot = 0; slot < playerItemSlots; slot++) {
            ItemStack stack = playerInventory.items.get(slot);
            displayItems.set(
                    slot,
                    stack.isEmpty() ? ItemStack.EMPTY : stack.copy()
            );
        }

        if (externalStacks != null) {
            for (int index = 0; index < externalStacks.size(); index++) {
                ItemStack stack = externalStacks.get(index);

                displayItems.set(
                        playerItemSlots + index,
                        stack == null || stack.isEmpty()
                                ? ItemStack.EMPTY
                                : stack.copy()
                );
            }
        }

        /*
         * InventoryAccess is added to Inventory by Mixin at runtime.
         * The intermediate Object cast is required because this class is final
         * and javac cannot otherwise see the transformed interface.
         */
        InventoryAccess accessor =
                (InventoryAccess) (Object) this;

        accessor.setItems(displayItems);

        /*
         * Keep Inventory's internal compartment list consistent with the
         * replaced items field, even though TaCZ currently reads items directly.
         */
        accessor.setCompartments(
                ImmutableList.of(
                        accessor.getItems(),
                        this.armor,
                        this.offhand
                )
        );

        this.selected = playerInventory.selected;
    }
}
