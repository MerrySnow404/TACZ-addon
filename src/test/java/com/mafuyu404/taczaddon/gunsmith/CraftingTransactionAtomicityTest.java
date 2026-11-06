package com.mafuyu404.taczaddon.gunsmith;

import com.mafuyu404.taczaddon.storage.api.CraftingItemSource;
import com.mafuyu404.taczaddon.storage.api.CraftingSourceKey;

import com.mafuyu404.taczaddon.testutil.MinecraftTestBootstrap;
import com.tacz.guns.crafting.GunSmithTableIngredient;
import com.tacz.guns.crafting.GunSmithTableRecipe;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class CraftingTransactionAtomicityTest {
    @BeforeAll
    static void bootstrapMinecraftRegistries() throws Exception {
        MinecraftTestBootstrap.prepare();
    }

    @Test
    void playerOnlyExactMaterialsAreConsumed() throws Exception {
        FakeSource player = FakeSource.player(5);

        CraftingTransaction transaction = transaction(
                List.of(player),
                5
        );

        assertTrue(invokeBoolean(transaction, "plan"));
        assertTrue(invokeBoolean(transaction, "simulate"));
        assertTrue(invokeBoolean(transaction, "commit"));

        assertEquals(0, player.count());
        assertEquals(5, player.extracted());
    }

    @Test
    void containerOnlyMaterialsAreConsumed() throws Exception {
        FakeSource chest = FakeSource.chest(
                new BlockPos(1, 0, 0),
                5
        );

        CraftingTransaction transaction = transaction(
                List.of(chest),
                5
        );

        assertTrue(invokeBoolean(transaction, "plan"));
        assertTrue(invokeBoolean(transaction, "simulate"));
        assertTrue(invokeBoolean(transaction, "commit"));

        assertEquals(0, chest.count());
        assertEquals(5, chest.extracted());
    }

    @Test
    void mixedPlayerAndNearbySourcesConsumeExactlyTheRecipeTotal()
            throws Exception {
        FakeSource player = FakeSource.player(3);
        FakeSource chestA = FakeSource.chest(
                new BlockPos(1, 0, 0),
                4
        );
        FakeSource chestB = FakeSource.chest(
                new BlockPos(2, 0, 0),
                3
        );

        CraftingTransaction transaction = transaction(
                List.of(player, chestA, chestB),
                10
        );

        assertTrue(invokeBoolean(transaction, "plan"));
        assertTrue(invokeBoolean(transaction, "simulate"));
        assertEquals(10, total(player, chestA, chestB));
        assertTrue(invokeBoolean(transaction, "commit"));

        assertEquals(0, total(player, chestA, chestB));
        assertEquals(3, player.extracted());
        assertEquals(4, chestA.extracted());
        assertEquals(3, chestB.extracted());
    }

    @Test
    void sameIngredientSplitOverSources() throws Exception {
        FakeSource player = FakeSource.player(4);
        FakeSource chestA = FakeSource.chest(
                new BlockPos(1, 0, 0),
                6
        );

        CraftingTransaction transaction = transaction(
                List.of(player, chestA),
                10
        );

        assertTrue(invokeBoolean(transaction, "plan"));
        assertTrue(invokeBoolean(transaction, "simulate"));
        assertTrue(invokeBoolean(transaction, "commit"));

        assertEquals(0, player.count());
        assertEquals(0, chestA.count());
    }

    @Test
    void multipleOverlappingIngredientPredicatesReserveOnce()
            throws Exception {
        FakeSource source = FakeSource.chest(
                new BlockPos(1, 0, 0),
                7
        );
        CraftingTransaction transaction = transaction(
                List.of(source),
                new GunSmithTableIngredient(
                        Ingredient.of(Items.IRON_INGOT),
                        3
                ),
                new GunSmithTableIngredient(
                        Ingredient.of(Items.IRON_INGOT),
                        4
                )
        );

        assertTrue(invokeBoolean(transaction, "plan"));
        assertTrue(invokeBoolean(transaction, "simulate"));
        assertTrue(invokeBoolean(transaction, "commit"));

        assertEquals(0, source.count());
        assertEquals(7, source.extracted());
    }

    @Test
    void insufficientTotalConsumesNothing() throws Exception {
        FakeSource player = FakeSource.player(3);
        FakeSource chestA = FakeSource.chest(
                new BlockPos(1, 0, 0),
                4
        );
        FakeSource chestB = FakeSource.chest(
                new BlockPos(2, 0, 0),
                2
        );

        CraftingTransaction transaction = transaction(
                List.of(player, chestA, chestB),
                10
        );

        assertFalse(invokeBoolean(transaction, "plan"));
        assertEquals(9, total(player, chestA, chestB));
        assertEquals(0, player.extracted());
        assertEquals(0, chestA.extracted());
        assertEquals(0, chestB.extracted());
    }

    @Test
    void nbtTagIdentityIsPreservedThroughCommit() throws Exception {
        CompoundTag tag = new CompoundTag();
        tag.putString("source", "tagged");
        ItemStack tagged = new ItemStack(Items.IRON_INGOT, 5);
        tagged.setTag(tag);

        FakeSource source = FakeSource.chest(
                new BlockPos(1, 0, 0),
                tagged
        );
        CraftingTransaction transaction = transaction(
                List.of(source),
                5
        );

        assertTrue(invokeBoolean(transaction, "plan"));
        assertTrue(invokeBoolean(transaction, "simulate"));
        assertTrue(invokeBoolean(transaction, "commit"));

        assertNotNull(source.lastExtracted().getTag());
        assertEquals(
                "tagged",
                source.lastExtracted().getTag().getString("source")
        );
    }

    @Test
    void simulateThrowBeforeMutationFailsCleanly() throws Exception {
        FakeSource player = FakeSource.player(5);
        player.throwOnSimulateExtract = true;

        CraftingTransaction transaction = transaction(
                List.of(player),
                5
        );

        assertTrue(invokeBoolean(transaction, "plan"));
        InvocationTargetException exception = assertThrows(
                InvocationTargetException.class,
                () -> invokeBoolean(transaction, "simulate")
        );
        assertTrue(exception.getCause() instanceof RuntimeException);
        assertEquals(5, player.count());
        assertEquals(0, player.extracted());
    }

    @Test
    void simulateShortfallConsumesNothing() throws Exception {
        FakeSource player = FakeSource.player(5);
        player.shortSimulation = true;

        CraftingTransaction transaction = transaction(
                List.of(player),
                5
        );

        assertTrue(invokeBoolean(transaction, "plan"));
        assertFalse(invokeBoolean(transaction, "simulate"));
        assertEquals(5, player.count());
        assertEquals(0, player.extracted());
    }

    @Test
    void sourceChangeAfterSimulationConsumesNothing() throws Exception {
        FakeSource player = FakeSource.player(5);

        CraftingTransaction transaction = transaction(
                List.of(player),
                5
        );

        assertTrue(invokeBoolean(transaction, "plan"));
        assertTrue(invokeBoolean(transaction, "simulate"));
        player.setCount(1);

        assertFalse(invokeBoolean(transaction, "revalidate"));
        assertEquals(1, player.count());
        assertEquals(0, player.extracted());
    }

    @Test
    void secondCommitShortfallRollsBackFirstExtraction()
            throws Exception {
        FakeSource player = FakeSource.player(5);
        FakeSource chest = FakeSource.chest(
                new BlockPos(1, 0, 0),
                5
        );

        CraftingTransaction transaction = transaction(
                List.of(player, chest),
                10
        );

        assertTrue(invokeBoolean(transaction, "plan"));
        assertTrue(invokeBoolean(transaction, "simulate"));
        chest.setCount(1);

        assertFalse(invokeBoolean(transaction, "commit"));
        assertEquals(0, player.count());
        assertEquals(0, chest.count());

        assertEquals(
                CraftingTransaction.RollbackResult.FULLY_RESTORED,
                invokeRollback(transaction)
        );
        assertEquals(5, player.count());
        assertEquals(1, chest.count());
    }

    @Test
    void secondCommitThrowRollsBackFirstExtraction()
            throws Exception {
        FakeSource player = FakeSource.player(5);
        FakeSource chest = FakeSource.chest(
                new BlockPos(1, 0, 0),
                5
        );

        CraftingTransaction transaction = transaction(
                List.of(player, chest),
                10
        );

        assertTrue(invokeBoolean(transaction, "plan"));
        assertTrue(invokeBoolean(transaction, "simulate"));
        chest.throwOnRealExtract = true;

        InvocationTargetException exception = assertThrows(
                InvocationTargetException.class,
                () -> invokeBoolean(transaction, "commit")
        );
        assertTrue(exception.getCause() instanceof RuntimeException);
        assertEquals(0, player.count());
        assertEquals(5, chest.count());

        assertEquals(
                CraftingTransaction.RollbackResult.FULLY_RESTORED,
                invokeRollback(transaction)
        );
        assertEquals(5, player.count());
        assertEquals(5, chest.count());
    }

    @Test
    void rollbackTriesOtherSlotsWhenOriginalSlotRejects()
            throws Exception {
        FakeSource source = FakeSource.chest(
                new BlockPos(1, 0, 0),
                new ItemStack(Items.IRON_INGOT, 5),
                ItemStack.EMPTY
        );
        source.rejectedInsertSlot = 0;

        CraftingTransaction transaction = transaction(
                List.of(source),
                5
        );

        assertTrue(invokeBoolean(transaction, "plan"));
        assertTrue(invokeBoolean(transaction, "simulate"));
        assertTrue(invokeBoolean(transaction, "commit"));
        assertEquals(0, source.count());

        assertEquals(
                CraftingTransaction.RollbackResult.FULLY_RESTORED,
                invokeRollback(transaction)
        );
        assertEquals(0, source.stackAt(0).getCount());
        assertEquals(5, source.stackAt(1).getCount());
    }

    @Test
    void rollbackStopsWhenInsertMayHavePartiallyCommitted()
            throws Exception {
        assertUnknownMutationNeverDuplicates(false);
    }

    @Test
    void rollbackStopsWhenInsertLinkageFailsAfterMutation()
            throws Exception {
        assertUnknownMutationNeverDuplicates(true);
    }

    @Test
    void exactFeasibilityDoesNotDoubleCountOverlappingIngredients() {
        GunSmithTableRecipe recipe = new GunSmithTableRecipe(
                Objects.requireNonNull(
                        ResourceLocation.tryBuild(
                                "taczaddon",
                                "overlap_test"
                        )
                ),
                null,
                List.of(
                        new GunSmithTableIngredient(
                                Ingredient.of(
                                        Items.IRON_INGOT,
                                        Items.GOLD_INGOT
                                ),
                                1
                        ),
                        new GunSmithTableIngredient(
                                Ingredient.of(
                                        Items.IRON_INGOT
                                ),
                                1
                        )
                )
        );

        assertFalse(
                CraftingTransaction.canSatisfyStacks(
                        recipe,
                        List.of(
                                new ItemStack(
                                        Items.IRON_INGOT,
                                        1
                                )
                        )
                ),
                "one iron cannot satisfy two overlapping demands"
        );

        assertTrue(
                CraftingTransaction.canSatisfyStacks(
                        recipe,
                        List.of(
                                new ItemStack(
                                        Items.IRON_INGOT,
                                        1
                                ),
                                new ItemStack(
                                        Items.GOLD_INGOT,
                                        1
                                )
                        )
                )
        );
    }

    /*
     * Two sources each supply half of the recipe. The first rollback entry is
     * inserted back into a source whose handler commits part of the stack and
     * only then throws. The whole rollback must stop: the remaining entries,
     * the fallback slots and the drop path must never see the pre-call stack
     * again, or the already committed part would be duplicated.
     *
     * The unit transaction has no real player, so the drop path cannot run
     * here; the item total invariant below is the observable contract.
     */
    private static void assertUnknownMutationNeverDuplicates(
            boolean linkageError
    ) throws Exception {
        FakeSource failing = FakeSource.chest(
                new BlockPos(1, 0, 0),
                new ItemStack(
                        Items.IRON_INGOT,
                        5
                ),
                ItemStack.EMPTY
        );

        FakeSource intact = FakeSource.chest(
                new BlockPos(2, 0, 0),
                5
        );

        failing.insertBeforeThrow = 2;
        failing.linkageErrorAfterInsert =
                linkageError;

        /*
         * Commit order:
         *
         *     intact -> failing
         *
         * Rollback reverses that order:
         *
         *     failing -> UNKNOWN
         *     intact  -> MUST NOT BE REINSERTED
         *
         * This ordering is intentional. It proves that UNKNOWN stops the entire
         * remaining rollback, rather than merely happening on the last entry.
         */
        CraftingTransaction transaction =
                transaction(
                        List.of(
                                intact,
                                failing
                        ),
                        10
                );

        assertTrue(
                invokeBoolean(
                        transaction,
                        "plan"
                )
        );
        assertTrue(
                invokeBoolean(
                        transaction,
                        "simulate"
                )
        );
        assertTrue(
                invokeBoolean(
                        transaction,
                        "commit"
                )
        );

        assertEquals(
                0,
                total(
                        failing,
                        intact
                )
        );

        assertEquals(
                CraftingTransaction.RollbackResult
                        .UNKNOWN_MUTATION,
                invokeRollback(transaction)
        );

        assertEquals(
                2,
                failing.count(),
                "only the part already committed before the exception may appear again"
        );

        assertTrue(
                failing.stackAt(1).isEmpty(),
                "the failing source must not receive another fallback insertion"
        );

        assertEquals(
                0,
                intact.count(),
                "rollback must stop before processing later entries once mutation state is unknown"
        );

        assertTrue(
                total(
                        failing,
                        intact
                ) <= 10,
                "an unknown mutation may lose items but must never increase the total"
        );

        /*
         * Both sources were already mutated by the successful commit.
         * The untouched rollback entry must not be restored, but it still must
         * be synchronized so its authoritative consumed state is persisted.
         */
        assertEquals(
                1,
                failing.markChangedCalls()
        );
        assertEquals(
                1,
                failing.synchronizeCalls()
        );
        assertEquals(
                1,
                intact.markChangedCalls()
        );
        assertEquals(
                1,
                intact.synchronizeCalls()
        );
    }

    @Test
    void unrestorableRemainderIsReportedAsPartial() throws Exception {
        FakeSource source = FakeSource.chest(
                new BlockPos(1, 0, 0),
                5
        );
        source.alwaysRejectInsert = true;

        CraftingTransaction transaction = transaction(
                List.of(source),
                5
        );

        assertTrue(invokeBoolean(transaction, "plan"));
        assertTrue(invokeBoolean(transaction, "simulate"));
        assertTrue(invokeBoolean(transaction, "commit"));

        assertEquals(
                CraftingTransaction.RollbackResult.PARTIALLY_COMPENSATED,
                invokeRollback(transaction)
        );
    }

    private static CraftingTransaction transaction(
            List<CraftingItemSource> sources,
            int required
    ) throws Exception {
        return transaction(
                sources,
                new GunSmithTableIngredient(
                        Ingredient.of(Items.IRON_INGOT),
                        required
                )
        );
    }

    private static CraftingTransaction transaction(
            List<CraftingItemSource> sources,
            GunSmithTableIngredient... inputs
    ) throws Exception {
        GunSmithTableRecipe recipe = new GunSmithTableRecipe(
                Objects.requireNonNull(ResourceLocation.tryBuild(
                        "taczaddon",
                        "atomicity_test"
                )),
                null,
                List.of(inputs)
        );

        Constructor<CraftingTransaction> constructor =
                CraftingTransaction.class.getDeclaredConstructor(
                        ServerPlayer.class,
                        GunSmithCraftingSession.class,
                        GunSmithTableRecipe.class,
                        List.class
                );
        constructor.setAccessible(true);
        return constructor.newInstance(null, null, recipe, sources);
    }

    private static boolean invokeBoolean(
            CraftingTransaction transaction,
            String methodName
    ) throws Exception {
        Method method = CraftingTransaction.class
                .getDeclaredMethod(methodName);
        method.setAccessible(true);
        return (boolean) method.invoke(transaction);
    }

    private static CraftingTransaction.RollbackResult invokeRollback(
            CraftingTransaction transaction
    ) throws Exception {
        Method method = CraftingTransaction.class
                .getDeclaredMethod("rollback");
        method.setAccessible(true);
        return (CraftingTransaction.RollbackResult)
                method.invoke(transaction);
    }

    private static int total(FakeSource... sources) {
        int total = 0;
        for (FakeSource source : sources) {
            total += source.count();
        }
        return total;
    }

    private static final class FakeSource implements CraftingItemSource {
        private final CraftingSourceKey key;
        private final ArrayList<ItemStack> stacks = new ArrayList<>();
        private Object backendIdentity = this;
        private int extracted;
        private ItemStack lastExtracted = ItemStack.EMPTY;
        private boolean throwOnGetStack;
        private boolean throwOnSimulateExtract;
        private boolean throwOnRealExtract;
        private boolean throwOnInsert;
        private boolean throwOnSlotCount;
        private boolean shortSimulation;
        private int insertBeforeThrow;
        private boolean linkageErrorAfterInsert;
        private int rejectedInsertSlot = -1;
        private boolean alwaysRejectInsert;
        private int markChangedCalls;
        private int synchronizeCalls;

        private FakeSource(CraftingSourceKey key, ItemStack... slots) {
            this.key = key;
            for (ItemStack slot : slots) {
                this.stacks.add(slot.copy());
            }
        }

        static FakeSource player(int count) {
            return new FakeSource(
                    new CraftingSourceKey.PlayerInventory(
                            UUID.fromString(
                                    "00000000-0000-0000-0000-000000000001"
                            )
                    ),
                    new ItemStack(Items.IRON_INGOT, count)
            );
        }

        static FakeSource chest(BlockPos pos, int count) {
            return chest(
                    pos,
                    new ItemStack(Items.IRON_INGOT, count)
            );
        }

        static FakeSource chest(BlockPos pos, ItemStack... slots) {
            return new FakeSource(
                    new CraftingSourceKey.BlockEntity(
                            Level.OVERWORLD,
                            pos
                    ),
                    slots
            );
        }

        void setCount(int count) {
            this.stacks.set(
                    0,
                    this.stacks.get(0).copyWithCount(count)
            );
        }

        int count() {
            int total = 0;
            for (ItemStack stack : this.stacks) {
                total += stack.getCount();
            }
            return total;
        }

        int extracted() {
            return this.extracted;
        }

        ItemStack lastExtracted() {
            return this.lastExtracted.copy();
        }

        ItemStack stackAt(int slot) {
            return this.stacks.get(slot).copy();
        }
        int markChangedCalls() {
            return this.markChangedCalls;
        }

        int synchronizeCalls() {
            return this.synchronizeCalls;
        }

        @Override
        public CraftingSourceKey key() {
            return this.key;
        }

        @Override
        public Object backendIdentity() {
            return this.backendIdentity;
        }

        @Override
        public int slotCount() {
            if (this.throwOnSlotCount) {
                throw new IllegalStateException("slot count failed");
            }
            return this.stacks.size();
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            if (this.throwOnGetStack) {
                throw new IllegalStateException("get stack failed");
            }
            if (slot < 0 || slot >= this.stacks.size()) {
                return ItemStack.EMPTY;
            }
            return this.stacks.get(slot).copy();
        }

        @Override
        public ItemStack extractItem(
                int slot,
                int amount,
                boolean simulate
        ) {
            if (simulate && this.throwOnSimulateExtract) {
                throw new IllegalStateException("simulate failed");
            }
            if (!simulate && this.throwOnRealExtract) {
                throw new IllegalStateException("extract failed");
            }
            if (slot < 0 || slot >= this.stacks.size()) {
                return ItemStack.EMPTY;
            }

            ItemStack current = this.stacks.get(slot);
            if (current.isEmpty()) {
                return ItemStack.EMPTY;
            }

            int extractedCount = Math.min(amount, current.getCount());
            if (simulate && this.shortSimulation) {
                extractedCount = Math.max(0, extractedCount - 1);
            }
            if (extractedCount <= 0) {
                return ItemStack.EMPTY;
            }

            ItemStack result =
                    current.copyWithCount(extractedCount);
            if (!simulate) {
                current.shrink(extractedCount);
                if (current.isEmpty()) {
                    this.stacks.set(slot, ItemStack.EMPTY);
                }
                this.extracted += extractedCount;
                this.lastExtracted = result.copy();
            }
            return result;
        }

        @Override
        public ItemStack insertItem(
                int slot,
                ItemStack stack,
                boolean simulate
        ) {
            if (!simulate
                    && this.insertBeforeThrow > 0
                    && slot >= 0
                    && slot < this.stacks.size()) {
                /*
                 * Provoke the exact hazard this rollback must survive: the
                 * handler commits part of the stack and only then fails. The
                 * caller can no longer know how much was written.
                 */
                int inserted = Math.min(
                        this.insertBeforeThrow,
                        stack.getCount()
                );

                ItemStack current = this.stacks.get(slot);

                if (current.isEmpty()) {
                    this.stacks.set(
                            slot,
                            stack.copyWithCount(inserted)
                    );
                } else if (ItemStack.isSameItemSameTags(
                        current,
                        stack
                )) {
                    current.grow(inserted);
                } else {
                    this.stacks.set(
                            slot,
                            stack.copyWithCount(inserted)
                    );
                }

                this.insertBeforeThrow = 0;

                if (this.linkageErrorAfterInsert) {
                    throw new NoSuchMethodError(
                            "compat insert failed after mutation"
                    );
                }

                throw new IllegalStateException(
                        "insert failed after mutation"
                );
            }

            if (this.throwOnInsert) {
                throw new IllegalStateException("insert failed");
            }
            if (slot < 0
                    || slot >= this.stacks.size()
                    || this.alwaysRejectInsert
                    || slot == this.rejectedInsertSlot) {
                return stack.copy();
            }

            ItemStack current = this.stacks.get(slot);
            if (current.isEmpty()) {
                int inserted = Math.min(
                        stack.getMaxStackSize(),
                        stack.getCount()
                );
                if (!simulate) {
                    this.stacks.set(
                            slot,
                            stack.copyWithCount(inserted)
                    );
                }
                return remainder(stack, inserted);
            }

            if (!ItemStack.isSameItemSameTags(current, stack)) {
                return stack.copy();
            }

            int available = current.getMaxStackSize()
                    - current.getCount();
            int inserted = Math.min(available, stack.getCount());
            if (!simulate) {
                current.grow(inserted);
            }
            return remainder(stack, inserted);
        }

        @Override
        public boolean isValid(ServerPlayer player) {
            return true;
        }

        @Override
        public void markChanged() {
            this.markChangedCalls++;
        }

        @Override
        public void synchronize(
                ServerPlayer player
        ) {
            this.synchronizeCalls++;
        }

        private static ItemStack remainder(
                ItemStack original,
                int inserted
        ) {
            int remaining = original.getCount() - inserted;
            return remaining <= 0
                    ? ItemStack.EMPTY
                    : original.copyWithCount(remaining);
        }
    }
}
