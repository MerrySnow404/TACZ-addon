package com.mafuyu404.taczaddon.inventory;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import net.neoforged.neoforge.items.wrapper.PlayerMainInvWrapper;

import java.util.ArrayList;
import java.util.List;

/**
 * Fail-safe physical-attachment return transaction scoped to the player's main inventory only.
 *
 * <p>The storage domain is deliberately {@code Inventory.items} via {@link PlayerMainInvWrapper} —
 * exactly the main inventory (36 slots). It never touches armor, offhand, curios, backpacks or the
 * world, and it never uses the overridable {@code Capabilities.ItemHandler.ENTITY} capability.
 *
 * <p>Usage:
 *
 * <ol>
 *   <li>{@link #begin(Inventory)} snapshots every main slot (copies).
 *   <li>{@link #canFullyInsert(ItemStack)} simulates without mutating.
 *   <li>The caller mutates the gun, then calls {@link #commitInsert(ItemStack)}.
 *   <li>If the remainder is non-empty or an exception is thrown, the caller invokes {@link
 *       #rollback()} which restores the main inventory from the snapshot and never drops anything.
 *   <li>{@link #close()} releases the snapshot after a successful commit.
 * </ol>
 *
 * <p>State machine:
 *
 * <pre>
 * OPEN ── close() ──────────────────────────► CLOSED
 * OPEN ── rollback() ───────────────────────► ROLLED_BACK (closed)
 * OPEN ── commitInsert() remainder/exception ► still OPEN (rollback allowed)
 * </pre>
 *
 * <p>{@code commitInsert()} never auto-closes: the caller must be able to roll back after a failed
 * insertion. After {@code close()} or {@code rollback()} the transaction is closed; {@code
 * rollback()} and {@code close()} become no-ops and {@code commitInsert()} refuses to run.
 *
 * <p>The snapshot is the single rollback authority: it copies every main slot (including the gun
 * slot), so a rollback restores the complete gun ItemStack (identity, CUSTOM_DATA, serialized
 * attachments, ammo/state components, everything) to the pre-transaction state. Callers must never
 * try to re-apply changes to the stale pre-rollback gun reference afterwards.
 */
public final class MainInventoryTransaction {

    private final Inventory inventory;
    private final PlayerMainInvWrapper mainHandler;
    private final List<ItemStack> snapshot;
    private boolean closed;

    private MainInventoryTransaction(Inventory inventory) {
        this.inventory = inventory;
        this.mainHandler = new PlayerMainInvWrapper(inventory);

        /*
         * Inventory.getContainerSize() returns 41 (items + armor + offhand),
         * but this transaction is deliberately main-inventory only, so the
         * snapshot covers Inventory.items (36 slots).
         */
        int mainSize = inventory.items.size();
        this.snapshot = new ArrayList<>(mainSize);

        for (int slot = 0; slot < mainSize; slot++) {
            this.snapshot.add(inventory.getItem(slot).copy());
        }
    }

    public static MainInventoryTransaction begin(Inventory inventory) {
        return new MainInventoryTransaction(inventory);
    }

    /**
     * Simulated full insertion. Returns true only when the entire stack can fit into the main
     * inventory; a partial fit is not enough. Never mutates the real inventory.
     */
    public boolean canFullyInsert(ItemStack stack) {
        return canFullyInsert(mainHandler, stack);
    }

    /**
     * Pure decision helper used by both the production wrapper and tests. Simulates insertion
     * through any IItemHandler (the production handler is the main-inventory {@link
     * PlayerMainInvWrapper}); returns true only for a full fit.
     */
    public static boolean canFullyInsert(IItemHandler handler, ItemStack stack) {
        if (stack.isEmpty()) {
            return true;
        }

        return ItemHandlerHelper.insertItemStacked(handler, stack.copy(), true).isEmpty();
    }

    /**
     * Real insertion into the main inventory. The input stack is never mutated. Returns the
     * remainder (empty on full success). Never closes the transaction, so the caller can still roll
     * back on failure.
     */
    public ItemStack commitInsert(ItemStack stack) {
        if (closed) {
            throw new IllegalStateException(
                    "MainInventoryTransaction is already closed; "
                            + "no further mutation is allowed");
        }

        return commitInsert(mainHandler, stack);
    }

    /** Pure commit helper used by both the production wrapper and tests. */
    public static ItemStack commitInsert(IItemHandler handler, ItemStack stack) {
        if (stack.isEmpty()) {
            return ItemStack.EMPTY;
        }

        return ItemHandlerHelper.insertItemStacked(handler, stack.copy(), false);
    }

    /**
     * Restores the main inventory to the snapshot state. Safe to call at most once; afterwards the
     * transaction is closed.
     */
    public void rollback() {
        if (closed) {
            return;
        }

        for (int slot = 0; slot < snapshot.size(); slot++) {
            inventory.setItem(slot, snapshot.get(slot).copy());
        }

        inventory.setChanged();
        close();
    }

    /** Releases the snapshot after a successful commit. */
    public void close() {
        if (closed) {
            return;
        }

        this.closed = true;
        this.snapshot.clear();
    }
}
