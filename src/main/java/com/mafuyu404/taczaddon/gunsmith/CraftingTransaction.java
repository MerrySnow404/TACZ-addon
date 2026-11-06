package com.mafuyu404.taczaddon.gunsmith;

import com.mafuyu404.taczaddon.gunsmith.MaterialAllocation.IngredientDemand;
import com.mafuyu404.taczaddon.gunsmith.MaterialAllocation.SlotCandidate;
import com.mafuyu404.taczaddon.gunsmith.MaterialAllocation.SlotPlan;
import com.mafuyu404.taczaddon.storage.PlayerInventorySource;
import com.mafuyu404.taczaddon.storage.SafeSourceInsert;
import com.mafuyu404.taczaddon.storage.api.CraftingItemSource;

import com.mojang.logging.LogUtils;
import com.tacz.guns.crafting.GunSmithTableIngredient;
import com.tacz.guns.crafting.GunSmithTableRecipe;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import org.slf4j.Logger;

import java.util.*;
import javax.annotation.Nullable;

public final class CraftingTransaction {
    private static final Logger LOGGER = LogUtils.getLogger();

    private final ServerPlayer player;
    private final GunSmithCraftingSession session;
    private final GunSmithTableRecipe recipe;
    private final List<CraftingItemSource> sources;

    private final ArrayList<SlotPlan> slotPlans = new ArrayList<>();
    private final ArrayList<ExtractedEntry> extractedForRollback =
            new ArrayList<>();

    private TransactionPhase phase = TransactionPhase.PLANNING;
    private boolean mutationStarted;
    private boolean outputSpawned;

    private CraftingTransaction(
            ServerPlayer player,
            GunSmithCraftingSession session,
            GunSmithTableRecipe recipe,
            List<CraftingItemSource> sources
    ) {
        this.player = player;
        this.session = session;
        this.recipe = recipe;
        this.sources = List.copyOf(sources);
    }

    public static CraftResult execute(
            ServerPlayer player,
            GunSmithCraftingSession session,
            GunSmithTableRecipe recipe,
            List<CraftingItemSource> sources
    ) {
        CraftingTransaction transaction =
                new CraftingTransaction(
                        player,
                        session,
                        recipe,
                        sources
                );

        try {
            if (player.isCreative()) {
                ItemStack result = transaction.createResultStack();
                if (result.isEmpty()
                        || !transaction.spawnOutput(result)) {
                    return CraftResult.fail(
                            CraftFailure.TRANSACTION_FAILED
                    );
                }
                transaction.finishBestEffort();
                return CraftResult.success(result);
            }

            transaction.phase = TransactionPhase.PLANNING;
            if (!transaction.plan()) {
                return CraftResult.fail(
                        CraftFailure.INSUFFICIENT_MATERIALS
                );
            }

            transaction.phase = TransactionPhase.SIMULATING;
            if (!transaction.simulate()) {
                return CraftResult.fail(
                        CraftFailure.INSUFFICIENT_MATERIALS
                );
            }

            transaction.phase = TransactionPhase.REVALIDATING;
            if (!transaction.revalidate()) {
                return CraftResult.fail(
                        CraftFailure.SOURCE_CHANGED
                );
            }

            transaction.phase = TransactionPhase.COMMITTING;
            transaction.mutationStarted = true;
            if (!transaction.commit()) {
                transaction.rollbackSafely();
                return CraftResult.fail(
                        CraftFailure.TRANSACTION_FAILED
                );
            }

            transaction.phase = TransactionPhase.OUTPUT_CREATION;
            ItemStack result = transaction.createResultStack();
            if (result.isEmpty()
                    || !transaction.spawnOutput(result)) {
                transaction.rollbackSafely();
                return CraftResult.fail(
                        CraftFailure.TRANSACTION_FAILED
                );
            }

            transaction.phase = TransactionPhase.COMPLETE;
            transaction.finishBestEffort();
            return CraftResult.success(result);
        } catch (RuntimeException | LinkageError failure) {
            if (!transaction.outputSpawned
                    && (transaction.mutationStarted
                    || !transaction.extractedForRollback.isEmpty())) {
                transaction.rollbackSafely();
            }

            LOGGER.error(
                    "Gunsmith transaction failed for player {} recipe {} phase {}",
                    player.getGameProfile().getName(),
                    recipe.getId(),
                    transaction.phase,
                    failure
            );
            return CraftResult.fail(
                    CraftFailure.TRANSACTION_FAILED
            );
        }
    }

    /**
     * Read-only feasibility check for the exact same allocation semantics the
     * server transaction uses.
     *
     * <p>Independent per-ingredient counts are not a capacity allocation: a
     * recipe whose inputs overlap can look satisfiable while the same physical
     * stack is being counted twice. This runs the identical integral max-flow
     * model over the supplied stacks without touching any live source.
     */
    public static boolean canSatisfyStacks(@Nullable GunSmithTableRecipe recipe, List<ItemStack> stacks) {
        return MaterialAllocation.canSatisfyStacks(recipe, stacks);
    }

    /**
     * Builds one deterministic global material allocation.
     *
     * A simple ingredient-by-ingredient greedy allocator is not sufficient:
     * overlapping predicates can have a valid assignment that greedy ordering
     * misses. Example: ingredient A accepts {iron,gold}, ingredient B accepts
     * {iron}, inventory has one iron and one gold. A greedy A-first pass may
     * reserve iron and incorrectly reject B.
     *
     * Model the recipe as an integral max-flow problem instead:
     *
     * source -> ingredient demand -> candidate slot -> sink
     *
     * Slot order remains deterministic (player inventory first, then the
     * already deterministic external-source order). Max flow may reroute an
     * earlier broad predicate only when that is required to satisfy a narrower
     * predicate.
     */
    private boolean plan() {
        this.slotPlans.clear();

        List<GunSmithTableIngredient> inputs =
                this.recipe.getInputs();
        if (inputs == null) {
            return false;
        }

        ArrayList<IngredientDemand> demands = new ArrayList<>();
        long totalRequired = 0L;

        for (GunSmithTableIngredient input : inputs) {
            if (input == null) {
                return false;
            }

            int required = input.getCount();
            if (required <= 0) {
                continue;
            }

            Ingredient ingredient = input.getIngredient();
            if (ingredient == null) {
                return false;
            }

            demands.add(new IngredientDemand(
                    ingredient,
                    required
            ));
            totalRequired += required;
        }

        if (totalRequired == 0L) {
            return true;
        }

        ArrayList<SlotCandidate> candidates =
                collectSlotCandidates(demands);
        if (candidates.isEmpty()) {
            return false;
        }

        return MaterialAllocation.allocateByMaxFlow(demands, candidates, totalRequired, this.slotPlans);
    }

    private ArrayList<SlotCandidate> collectSlotCandidates(
            List<IngredientDemand> demands
    ) {
        ArrayList<SlotCandidate> candidates =
                new ArrayList<>();

        for (int sourceIndex = 0;
             sourceIndex < this.sources.size();
             sourceIndex++) {
            CraftingItemSource source =
                    this.sources.get(sourceIndex);
            int slotCount = source.slotCount();
            if (slotCount <= 0) {
                continue;
            }

            for (int slot = 0; slot < slotCount; slot++) {
                ItemStack current =
                        source.getStackInSlot(slot);
                if (current == null
                        || current.isEmpty()
                        || current.getCount() <= 0) {
                    continue;
                }

                boolean matchesAny = false;
                for (IngredientDemand demand : demands) {
                    if (demand.ingredient().test(current)) {
                        matchesAny = true;
                        break;
                    }
                }

                if (!matchesAny) {
                    continue;
                }

                candidates.add(new SlotCandidate(
                        sourceIndex,
                        source.key(),
                        slot,
                        current.copyWithCount(1),
                        current.getCount()
                ));
            }
        }

        return candidates;
    }

    private boolean simulate() {
        for (SlotPlan plan : this.slotPlans) {
            CraftingItemSource source =
                    this.sources.get(plan.sourceIndex());
            ItemStack simulated = source.extractItem(
                    plan.slot(),
                    plan.amount(),
                    true
            );

            if (!matchesExtracted(simulated, plan)) {
                return false;
            }
        }

        return true;
    }

    private boolean revalidate() {
        if (this.session == null
                || !this.session.validate(
                this.player,
                this.session.getContainerId()
        )) {
            return false;
        }

        for (SlotPlan plan : this.slotPlans) {
            CraftingItemSource source =
                    this.sources.get(plan.sourceIndex());

            if (!source.key().equals(plan.sourceKey())
                    || !source.isValid(this.player)) {
                return false;
            }

            ItemStack current =
                    source.getStackInSlot(plan.slot());
            if (current.isEmpty()
                    || current.getCount() < plan.amount()
                    || !sameIdentity(
                    current,
                    plan.expectedStack()
            )
                    || !matchesPredicates(
                    current,
                    plan.predicates()
            )) {
                return false;
            }

            ItemStack simulated = source.extractItem(
                    plan.slot(),
                    plan.amount(),
                    true
            );
            if (!matchesExtracted(simulated, plan)) {
                return false;
            }
        }

        return true;
    }

    private boolean commit() {
        for (SlotPlan plan : this.slotPlans) {
            CraftingItemSource source =
                    this.sources.get(plan.sourceIndex());

            ItemStack extracted = source.extractItem(
                    plan.slot(),
                    plan.amount(),
                    false
            );

            if (!extracted.isEmpty()) {
                this.extractedForRollback.add(
                        new ExtractedEntry(
                                source,
                                plan.slot(),
                                extracted.copy()
                        )
                );
            }

            if (!matchesExtracted(extracted, plan)) {
                return false;
            }
        }

        return true;
    }

    private RollbackResult rollback() {
        boolean fullyRestored = true;
        LinkedHashSet<CraftingItemSource> affectedSources =
                new LinkedHashSet<>();

        ArrayList<ExtractedEntry> reversed =
                new ArrayList<>(this.extractedForRollback);
        Collections.reverse(reversed);

        PlayerInventorySource playerFallback =
                this.player == null
                        ? null
                        : new PlayerInventorySource(this.player);

        for (ExtractedEntry entry : reversed) {
            ItemStack remainder = entry.stack().copy();
            affectedSources.add(entry.source());

            SafeSourceInsert.Result insertion =
                    insertItemSafely(
                            entry.source(),
                            entry.slot(),
                            remainder,
                            "original-slot"
                    );

            if (!insertion.known()) {
                return abortUnknownRollback(
                        affectedSources,
                        entry,
                        "original-slot"
                );
            }

            remainder = insertion.remainder();

            if (!remainder.isEmpty()) {
                insertion = insertIntoOtherSlots(
                        entry.source(),
                        entry.slot(),
                        remainder
                );

                if (!insertion.known()) {
                    return abortUnknownRollback(
                            affectedSources,
                            entry,
                            "source-fallback"
                    );
                }

                remainder = insertion.remainder();
            }

            if (!remainder.isEmpty() && playerFallback != null) {
                affectedSources.add(playerFallback);

                insertion = insertIntoOtherSlots(
                        playerFallback,
                        -1,
                        remainder
                );

                if (!insertion.known()) {
                    return abortUnknownRollback(
                            affectedSources,
                            entry,
                            "player-fallback"
                    );
                }

                remainder = insertion.remainder();
            }

            if (!remainder.isEmpty()) {
                fullyRestored = false;
                LOGGER.error(
                        "Gunsmith transaction rollback could not restore "
                                + "{} x{}; player {} source {} slot {} "
                                + "phase {} item {} amount {}; dropping "
                                + "remainder",
                        remainder.getHoverName().getString(),
                        remainder.getCount(),
                        playerName(),
                        entry.source().key(),
                        entry.slot(),
                        this.phase,
                        remainder.getHoverName().getString(),
                        remainder.getCount()
                );
                dropRemainder(entry, remainder);
            }
        }

        synchronizeSources(affectedSources);
        this.extractedForRollback.clear();
        return fullyRestored
                ? RollbackResult.FULLY_RESTORED
                : RollbackResult.PARTIALLY_COMPENSATED;
    }

    private RollbackResult abortUnknownRollback(
            Set<CraftingItemSource> affectedSources,
            ExtractedEntry entry,
            String operation
    ) {
        LOGGER.error(
                "Gunsmith rollback mutation state became unknown for player {} "
                        + "source {} slot {} during {}; refusing further "
                        + "reinsertion or drop to avoid item duplication",
                playerName(),
                entry.source().key(),
                entry.slot(),
                operation
        );

        /*
         * Every entry in extractedForRollback has already passed the real
         * extraction phase. Once one rollback mutation becomes unknown we must
         * never replay any remaining entry, but every already-mutated source must
         * still be marked dirty and synchronized.
         *
         * Synchronization is not compensation: it only exposes/persists the
         * authoritative partial state.
         */
        for (ExtractedEntry extracted
                : this.extractedForRollback) {
            affectedSources.add(
                    extracted.source()
            );
        }

        synchronizeSources(
                affectedSources
        );

        /*
         * Clearing prevents any later code from attempting to compensate these
         * entries a second time.
         */
        this.extractedForRollback.clear();

        return RollbackResult.UNKNOWN_MUTATION;
    }

    private RollbackResult rollbackSafely() {
        try {
            RollbackResult result = rollback();

            if (result == RollbackResult.PARTIALLY_COMPENSATED) {
                LOGGER.warn(
                        "Gunsmith transaction was only partially compensated "
                                + "for player {} phase {}",
                        playerName(),
                        this.phase
                );
            } else if (result == RollbackResult.UNKNOWN_MUTATION) {
                LOGGER.error(
                        "Gunsmith rollback stopped because mutation state is "
                                + "unknown for player {} phase {}",
                        playerName(),
                        this.phase
                );
            }

            return result;
        } catch (RuntimeException | LinkageError failure) {
            /*
             * Individual source operations are already guarded inside
             * rollback(). This outer boundary is a final containment layer:
             * rollback must never escape and crash the packet handler.
             *
             * Do not attempt a second blanket rollback here because some
             * entries may already have been restored; replaying them could
             * duplicate materials.
             */
            LOGGER.error(
                    "Unexpected gunsmith rollback failure for player {} "
                            + "recipe {} phase {}; mutation state is unknown",
                    playerName(),
                    this.recipe.getId(),
                    this.phase,
                    failure
            );
            return RollbackResult.UNKNOWN_MUTATION;
        }
    }

    private SafeSourceInsert.Result insertItemSafely(
            CraftingItemSource source,
            int slot,
            ItemStack stack,
            String operation
    ) {
        SafeSourceInsert.Result result =
                SafeSourceInsert.commit(
                        source,
                        slot,
                        stack
                );

        if (!result.known()) {
            logRollbackOperation(
                    operation,
                    source,
                    slot,
                    stack,
                    result.failure()
            );
        }

        return result;
    }

    private SafeSourceInsert.Result insertIntoOtherSlots(
            CraftingItemSource source,
            int excludedSlot,
            ItemStack stack
    ) {
        ItemStack remainder = stack.copy();

        int slots;
        try {
            slots = source.slotCount();
        } catch (RuntimeException | LinkageError failure) {
            /*
             * slotCount is read-only. No insertion happened, so the exact
             * remainder is still known and a higher-level fallback is safe.
             */
            logRollbackOperation(
                    "slot-count",
                    source,
                    -1,
                    stack,
                    failure
            );
            return SafeSourceInsert.Result.known(remainder);
        }

        for (int slot = 0;
             slot < slots && !remainder.isEmpty();
             slot++) {
            if (slot == excludedSlot) {
                continue;
            }

            SafeSourceInsert.Result result =
                    insertItemSafely(
                            source,
                            slot,
                            remainder,
                            "other-slot"
                    );

            if (!result.known()) {
                return result;
            }

            remainder = result.remainder();
        }

        return SafeSourceInsert.Result.known(remainder);
    }

    private void synchronizePlannedSources() {
        LinkedHashSet<CraftingItemSource> used =
                new LinkedHashSet<>();
        for (SlotPlan plan : this.slotPlans) {
            used.add(this.sources.get(plan.sourceIndex()));
        }
        synchronizeSources(used);
    }

    private void finishBestEffort() {
        synchronizePlannedSources();
        try {
            this.player.inventoryMenu.broadcastFullState();
        } catch (RuntimeException exception) {
            logSynchronizationFailure(
                    "player-inventory-broadcast",
                    null,
                    exception
            );
        }
    }

    private void synchronizeSources(
            Set<CraftingItemSource> sourcesToSynchronize
    ) {
        for (CraftingItemSource source
                : sourcesToSynchronize) {
            try {
                source.markChanged();
            } catch (RuntimeException | LinkageError failure) {
                logSynchronizationFailure(
                        "mark-changed",
                        source,
                        failure
                );
            }

            try {
                source.synchronize(this.player);
            } catch (RuntimeException | LinkageError failure) {
                logSynchronizationFailure(
                        "synchronize",
                        source,
                        failure
                );
            }
        }
    }

    private ItemStack createResultStack() {
        ItemStack result = this.recipe
                .getResultItem(
                        this.player.level().registryAccess()
                )
                .copy();
        return result;
    }

    private boolean spawnOutput(ItemStack result) {
        ItemEntity itemEntity = new ItemEntity(
                this.player.level(),
                this.player.getX(),
                this.player.getY() + 0.5D,
                this.player.getZ(),
                result.copy()
        );
        itemEntity.setPickUpDelay(0);
        boolean added = this.player.level().addFreshEntity(itemEntity);
        if (added) {
            this.outputSpawned = true;
        }
        return added;
    }

    private void dropRemainder(
            ExtractedEntry entry,
            ItemStack remainder
    ) {
        if (this.player == null) {
            return;
        }

        try {
            this.player.drop(remainder.copy(), false);
        } catch (RuntimeException exception) {
            logRollbackOperation(
                    "drop-remainder",
                    entry.source(),
                    entry.slot(),
                    remainder,
                    exception
            );
        }
    }

    private void logRollbackOperation(
            String operation,
            CraftingItemSource source,
            int slot,
            ItemStack stack,
            @Nullable Throwable failure
    ) {
        LOGGER.error(
                "Gunsmith rollback {} entered unknown mutation state for "
                        + "player {} source {} slot {} item {} amount {} "
                        + "phase {}",
                operation,
                playerName(),
                source.key(),
                slot,
                stack.getHoverName().getString(),
                stack.getCount(),
                this.phase,
                failure
        );
    }

    private void logSynchronizationFailure(
            String operation,
            @Nullable CraftingItemSource source,
            Throwable failure
    ) {
        LOGGER.error(
                "Gunsmith post-commit {} failed for player {} source {} "
                        + "phase {}",
                operation,
                playerName(),
                source == null
                        ? "player-inventory"
                        : source.key(),
                this.phase,
                failure
        );
    }

    private String playerName() {
        return this.player == null
                ? "unknown"
                : this.player.getGameProfile().getName();
    }

    private static boolean matchesExtracted(
            ItemStack extracted,
            SlotPlan plan
    ) {
        return !extracted.isEmpty()
                && extracted.getCount() == plan.amount()
                && sameIdentity(
                extracted,
                plan.expectedStack()
        )
                && matchesPredicates(
                extracted,
                plan.predicates()
        );
    }

    private static boolean matchesPredicates(
            ItemStack stack,
            List<Ingredient> predicates
    ) {
        for (Ingredient predicate : predicates) {
            if (!predicate.test(stack)) {
                return false;
            }
        }
        return true;
    }

    private static boolean sameIdentity(
            ItemStack first,
            ItemStack second
    ) {
        return ItemStack.isSameItemSameTags(first, second);
    }

    public record ExtractedEntry(
            CraftingItemSource source,
            int slot,
            ItemStack stack
    ) {
    }

    private enum TransactionPhase {
        PLANNING,
        SIMULATING,
        REVALIDATING,
        COMMITTING,
        OUTPUT_CREATION,
        COMPLETE
    }

    enum RollbackResult {
        FULLY_RESTORED,

        /**
         * Every mutation result was known, but one or more remainders could
         * only be preserved through a fallback such as dropping them.
         */
        PARTIALLY_COMPENSATED,

        /**
         * At least one insertion threw or returned an invalid remainder after
         * a possible mutation. Retrying that stack would risk duplication.
         */
        UNKNOWN_MUTATION
    }

    public enum CraftFailure {
        INVALID_MENU,
        INVALID_SESSION,
        INVALID_RECIPE,
        DUPLICATE_REQUEST,
        SOURCE_CHANGED,
        INSUFFICIENT_MATERIALS,
        TRANSACTION_FAILED
    }

    public static final class CraftResult {
        private final boolean success;
        private final ItemStack output;
        @Nullable
        private final CraftFailure failureReason;

        private CraftResult(
                boolean success,
                ItemStack output,
                @Nullable CraftFailure failureReason
        ) {
            this.success = success;
            this.output = output;
            this.failureReason = failureReason;
        }

        public static CraftResult success(ItemStack output) {
            return new CraftResult(
                    true,
                    output.copy(),
                    null
            );
        }

        public static CraftResult fail(CraftFailure reason) {
            return new CraftResult(
                    false,
                    ItemStack.EMPTY,
                    reason
            );
        }

        public boolean success() {
            return this.success;
        }

        public ItemStack output() {
            return this.output.copy();
        }

        @Nullable
        public CraftFailure failureReason() {
            return this.failureReason;
        }
    }
}
