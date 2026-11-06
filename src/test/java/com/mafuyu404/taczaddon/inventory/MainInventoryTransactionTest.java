package com.mafuyu404.taczaddon.inventory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import org.junit.jupiter.api.Test;

/**
 * MainInventoryTransaction lifecycle and rollback tests.
 *
 * <p>{@code Inventory.setItem/getItem} only map through the compartment lists and never dereference
 * the player, so a {@code null} player holder is sufficient to exercise the snapshot/rollback
 * mechanics with real vanilla classes (no mocks).
 */
class MainInventoryTransactionTest {

    private static final int MAIN_INVENTORY_SIZE = 36;

    private static Inventory newInventory() {
        return new Inventory(null);
    }

    private static void fillMainInventory(Inventory inventory) {
        for (int slot = 0; slot < MAIN_INVENTORY_SIZE; slot++) {
            inventory.setItem(slot, new ItemStack(Items.STONE, 64));
        }
    }

    /**
     * Regression for the stale gunStack rollback bug: the snapshot is the single rollback
     * authority. After rollback the slot holds a fresh stack restored from the snapshot, and the
     * previously captured live gun reference is detached (mutating it must not affect the
     * inventory).
     */
    @Test
    void rollbackRestoresGunSlotAndDetachesStaleReference() {
        Inventory inventory = newInventory();

        ItemStack oldGun = new ItemStack(Items.DIAMOND_SWORD);
        ItemStackData.updateCustomData(oldGun, tag -> tag.putString("provenance", "OLD"));
        inventory.setItem(0, oldGun);

        MainInventoryTransaction transaction = MainInventoryTransaction.begin(inventory);

        /*
         * The live reference is what TaCZ's installAttachment mutates in
         * place. Simulate the intermediate "gun NEW" state.
         */
        ItemStack liveGun = inventory.getItem(0);
        ItemStackData.updateCustomData(liveGun, tag -> tag.putString("provenance", "NEW"));

        transaction.rollback();

        ItemStack restored = inventory.getItem(0);

        assertNotSame(
                restored,
                liveGun,
                "rollback must replace the slot with a snapshot copy, "
                        + "not the stale mutated live reference");
        assertEquals("OLD", ItemStackData.getCustomDataCopy(restored).getString("provenance"));
    }

    @Test
    void rollbackIsIdempotent() {
        Inventory inventory = newInventory();
        inventory.setItem(0, new ItemStack(Items.STONE));

        MainInventoryTransaction transaction = MainInventoryTransaction.begin(inventory);

        inventory.setItem(0, new ItemStack(Items.DIAMOND));
        transaction.rollback();

        // Mutate again after rollback; a second rollback must be a no-op.
        inventory.setItem(0, new ItemStack(Items.EMERALD));
        transaction.rollback();

        assertEquals(Items.EMERALD, inventory.getItem(0).getItem());
    }

    @Test
    void rollbackAfterCloseDoesNotMutate() {
        Inventory inventory = newInventory();
        inventory.setItem(0, new ItemStack(Items.STONE));

        MainInventoryTransaction transaction = MainInventoryTransaction.begin(inventory);
        transaction.close();

        transaction.rollback();

        assertEquals(Items.STONE, inventory.getItem(0).getItem());
    }

    @Test
    void commitInsertAfterCloseThrows() {
        Inventory inventory = newInventory();

        MainInventoryTransaction transaction = MainInventoryTransaction.begin(inventory);
        transaction.close();

        assertThrows(
                IllegalStateException.class,
                () -> transaction.commitInsert(new ItemStack(Items.STONE)));
    }

    @Test
    void commitInsertFailureStillAllowsRollback() {
        Inventory inventory = newInventory();
        fillMainInventory(inventory);

        MainInventoryTransaction transaction = MainInventoryTransaction.begin(inventory);

        ItemStack remainder = transaction.commitInsert(new ItemStack(Items.DIAMOND));

        assertFalse(remainder.isEmpty());

        transaction.rollback();

        assertEquals(Items.STONE, inventory.getItem(0).getItem());
        assertEquals(64, inventory.getItem(0).getCount());
    }

    @Test
    void preflightNoSpaceThenCloseLeavesInventoryUntouched() {
        Inventory inventory = newInventory();
        fillMainInventory(inventory);

        MainInventoryTransaction transaction = MainInventoryTransaction.begin(inventory);

        assertFalse(transaction.canFullyInsert(new ItemStack(Items.DIAMOND)));

        transaction.close();

        assertEquals(Items.STONE, inventory.getItem(0).getItem());
        assertEquals(64, inventory.getItem(0).getCount());
    }

    @Test
    void preflightEmptySlotAllowsInsert() {
        Inventory inventory = newInventory();

        MainInventoryTransaction transaction = MainInventoryTransaction.begin(inventory);

        assertTrue(transaction.canFullyInsert(new ItemStack(Items.DIAMOND)));
    }
}
