package com.mafuyu404.taczaddon.storage;

import com.mafuyu404.taczaddon.storage.api.CraftingItemSource;
import com.mafuyu404.taczaddon.storage.api.CraftingSourceKey;

import com.mafuyu404.taczaddon.testutil.MinecraftTestBootstrap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.function.BiFunction;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Fail-closed contract for every handler-driven rollback insertion.
 */
class SafeSourceInsertTest {

    @BeforeAll
    static void bootstrap() throws Exception {
        MinecraftTestBootstrap.prepare();
    }

    @Test
    void normalFullInsertionIsKnownAndLeavesNoRemainder() {
        Handler handler = new Handler(
                (slot, stack) -> ItemStack.EMPTY
        );

        SafeSourceInsert.Result result = SafeSourceInsert.commit(
                handler,
                0,
                new ItemStack(Items.IRON_INGOT, 4)
        );

        assertTrue(result.known());
        assertTrue(result.remainder().isEmpty());
        assertNull(result.failure());
    }

    @Test
    void normalPartialInsertionReportsTheExactRemainder() {
        Handler handler = new Handler(
                (slot, stack) -> stack.copyWithCount(1)
        );

        SafeSourceInsert.Result result = SafeSourceInsert.commit(
                handler,
                0,
                new ItemStack(Items.IRON_INGOT, 4)
        );

        assertTrue(result.known());
        assertEquals(1, result.remainder().getCount());
        assertTrue(
                ItemStack.isSameItemSameTags(
                        new ItemStack(Items.IRON_INGOT),
                        result.remainder()
                )
        );
    }

    @Test
    void normalRejectionKeepsTheWholeStackAsRemainder() {
        Handler handler = new Handler(
                (slot, stack) -> stack.copy()
        );

        SafeSourceInsert.Result result = SafeSourceInsert.commit(
                handler,
                0,
                new ItemStack(Items.IRON_INGOT, 4)
        );

        assertTrue(result.known());
        assertEquals(4, result.remainder().getCount());
    }

    @Test
    void emptyStackNeverReachesTheHandler() {
        Handler handler = new Handler(
                (slot, stack) -> {
                    throw new AssertionError(
                            "an empty stack must not be inserted"
                    );
                }
        );

        SafeSourceInsert.Result result = SafeSourceInsert.commit(
                handler,
                0,
                ItemStack.EMPTY
        );

        assertTrue(result.known());
        assertTrue(result.remainder().isEmpty());
    }

    @Test
    void mutateThenRuntimeExceptionIsUnknown() {
        Handler handler = new Handler(
                (slot, stack) -> {
                    throw new IllegalStateException(
                            "insert failed after mutation"
                    );
                }
        );

        SafeSourceInsert.Result result = SafeSourceInsert.commit(
                handler,
                0,
                new ItemStack(Items.IRON_INGOT, 4)
        );

        assertFalse(result.known());
        assertTrue(result.remainder().isEmpty());
        assertTrue(result.failure() instanceof IllegalStateException);
    }

    @Test
    void mutateThenLinkageErrorIsUnknown() {
        Handler handler = new Handler(
                (slot, stack) -> {
                    throw new NoSuchMethodError(
                            "third-party insert ABI mismatch"
                    );
                }
        );

        SafeSourceInsert.Result result = SafeSourceInsert.commit(
                handler,
                0,
                new ItemStack(Items.IRON_INGOT, 4)
        );

        assertFalse(result.known());
        assertTrue(result.remainder().isEmpty());
        assertTrue(result.failure() instanceof NoSuchMethodError);
    }

    @Test
    void handlerReturningMoreThanOfferedIsInvalid() {
        Handler handler = new Handler(
                (slot, stack) -> new ItemStack(
                        Items.IRON_INGOT,
                        5
                )
        );

        SafeSourceInsert.Result result = SafeSourceInsert.commit(
                handler,
                0,
                new ItemStack(Items.IRON_INGOT, 4)
        );

        assertFalse(result.known());
        assertTrue(result.remainder().isEmpty());
        assertNull(
                result.failure(),
                "an invalid remainder is not a Java exception"
        );
    }

    @Test
    void handlerReturningADifferentItemIsInvalid() {
        Handler handler = new Handler(
                (slot, stack) -> new ItemStack(Items.GOLD_INGOT, 1)
        );

        SafeSourceInsert.Result result = SafeSourceInsert.commit(
                handler,
                0,
                new ItemStack(Items.IRON_INGOT, 4)
        );

        assertFalse(result.known());
        assertTrue(result.remainder().isEmpty());
        assertNull(result.failure());
    }

    @Test
    void brokenHandlerCannotMutateTheValidatedSnapshot() {
        ItemStack offered = new ItemStack(Items.IRON_INGOT, 4);
        Handler handler = new Handler(
                (slot, stack) -> {
                    /*
                     * A hostile handler empties the object it received. The
                     * wrapper must still validate against its own snapshot.
                     */
                    stack.setCount(0);
                    throw new IllegalStateException("broken handler");
                }
        );

        SafeSourceInsert.Result result = SafeSourceInsert.commit(
                handler,
                0,
                offered
        );

        assertFalse(result.known());
        assertEquals(
                4,
                offered.getCount(),
                "the caller's stack must not be mutated by the wrapper"
        );
    }

    private static final class Handler implements CraftingItemSource {
        private final BiFunction<Integer, ItemStack, ItemStack> insert;

        private Handler(
                BiFunction<Integer, ItemStack, ItemStack> insert
        ) {
            this.insert = insert;
        }

        @Override
        public CraftingSourceKey key() {
            return new CraftingSourceKey.BlockEntity(
                    null,
                    new BlockPos(1, 0, 0)
            );
        }

        @Override
        public Object backendIdentity() {
            return this;
        }

        @Override
        public int slotCount() {
            return 1;
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            return ItemStack.EMPTY;
        }

        @Override
        public ItemStack extractItem(
                int slot,
                int amount,
                boolean simulate
        ) {
            return ItemStack.EMPTY;
        }

        @Override
        public ItemStack insertItem(
                int slot,
                ItemStack stack,
                boolean simulate
        ) {
            return this.insert.apply(slot, stack);
        }

        @Override
        public boolean isValid(ServerPlayer player) {
            return true;
        }

        @Override
        public void markChanged() {
        }

        @Override
        public void synchronize(ServerPlayer player) {
        }
    }
}
