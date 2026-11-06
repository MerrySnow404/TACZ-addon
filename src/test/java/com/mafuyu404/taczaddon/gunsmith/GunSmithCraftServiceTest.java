package com.mafuyu404.taczaddon.gunsmith;

import static org.junit.jupiter.api.Assertions.*;

import com.mafuyu404.taczaddon.gunsmith.crafting.CraftingTransaction.CraftFailure;
import com.mafuyu404.taczaddon.gunsmith.crafting.CraftingTransaction.CraftResult;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

class GunSmithCraftServiceTest {
    @Test
    void partialBatchReportsOnlyCommittedExecutions() {
        AtomicInteger calls = new AtomicInteger();
        var result =
                GunSmithCraftService.craftBatch(
                        64,
                        () -> true,
                        () ->
                                calls.incrementAndGet() <= 17
                                        ? CraftResult.success(new ItemStack(Items.STONE, 2))
                                        : CraftResult.fail(CraftFailure.INSUFFICIENT_MATERIALS));
        assertTrue(result.success());
        assertEquals(17, result.craftedExecutions());
        assertEquals(2, result.outputPerCraft().getCount());
        assertEquals(CraftFailure.INSUFFICIENT_MATERIALS, result.failure());
        assertEquals(18, calls.get());
    }

    @Test
    void sessionIsCheckedBeforeEachExecution() {
        AtomicInteger calls = new AtomicInteger();
        var result =
                GunSmithCraftService.craftBatch(
                        64,
                        () -> calls.get() < 2,
                        () -> {
                            calls.incrementAndGet();
                            return CraftResult.success(new ItemStack(Items.STONE));
                        });
        assertEquals(2, result.craftedExecutions());
        assertEquals(2, calls.get());
        assertEquals(CraftFailure.INVALID_SESSION, result.failure());
    }

    @Test
    void failureAfterCommitDoesNotEraseProgress() {
        AtomicInteger calls = new AtomicInteger();
        var result =
                GunSmithCraftService.craftBatch(
                        3,
                        () -> true,
                        () -> {
                            if (calls.incrementAndGet() == 2)
                                throw new IllegalStateException("source became unavailable");
                            return CraftResult.success(new ItemStack(Items.STONE));
                        });
        assertTrue(result.success());
        assertEquals(1, result.craftedExecutions());
        assertEquals(CraftFailure.TRANSACTION_FAILED, result.failure());
        assertEquals(2, calls.get());
    }

    @Test
    void rejectedSessionDoesNotTouchInventory() {
        var result =
                GunSmithCraftService.craftBatch(
                        1,
                        () -> false,
                        () -> {
                            fail("invalid session executed a transaction");
                            return null;
                        });
        assertFalse(result.success());
        assertTrue(result.outputPerCraft().isEmpty());
        assertEquals(CraftFailure.INVALID_SESSION, result.failure());
    }
}
