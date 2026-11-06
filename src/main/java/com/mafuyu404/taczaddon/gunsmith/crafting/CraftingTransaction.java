package com.mafuyu404.taczaddon.gunsmith.crafting;

import com.mafuyu404.taczaddon.gunsmith.GunSmithCraftingSessionManager;
import com.mafuyu404.taczaddon.gunsmith.crafting.IngredientAllocationPlanner.SlotPlan;
import com.mojang.logging.LogUtils;
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
    private final GunSmithCraftingSessionManager.GunSmithCraftingSession session;
    private final GunSmithTableRecipe recipe;
    private final List<CraftingItemSource> sources;

    private final ArrayList<SlotPlan> slotPlans = new ArrayList<>();
    private final ArrayList<ExtractedEntry> extractedForRollback = new ArrayList<>();

    private TransactionPhase phase = TransactionPhase.PLANNING;
    private boolean mutationStarted;
    private boolean outputSpawned;

    private CraftingTransaction(
            ServerPlayer player,
            GunSmithCraftingSessionManager.GunSmithCraftingSession session,
            GunSmithTableRecipe recipe,
            List<CraftingItemSource> sources) {
        this.player = player;
        this.session = session;
        this.recipe = recipe;
        this.sources = List.copyOf(sources);
    }

    public static CraftResult execute(
            ServerPlayer player,
            GunSmithCraftingSessionManager.GunSmithCraftingSession session,
            GunSmithTableRecipe recipe,
            List<CraftingItemSource> sources) {
        CraftingTransaction transaction = new CraftingTransaction(player, session, recipe, sources);

        try {
            if (session == null || !session.validate(player, session.getContainerId())) {
                return CraftResult.fail(CraftFailure.INVALID_SESSION);
            }
            if (player.isCreative()) {
                ItemStack result = transaction.createResultStack();
                if (result.isEmpty() || !transaction.spawnOutput(result)) {
                    return CraftResult.fail(CraftFailure.TRANSACTION_FAILED);
                }
                transaction.finishBestEffort();
                return CraftResult.success(result);
            }

            transaction.phase = TransactionPhase.PLANNING;
            if (!transaction.plan()) {
                return CraftResult.fail(CraftFailure.INSUFFICIENT_MATERIALS);
            }

            transaction.phase = TransactionPhase.SIMULATING;
            if (!transaction.simulate()) {
                return CraftResult.fail(CraftFailure.INSUFFICIENT_MATERIALS);
            }

            transaction.phase = TransactionPhase.REVALIDATING;
            if (!transaction.revalidate()) {
                return CraftResult.fail(CraftFailure.SOURCE_CHANGED);
            }

            transaction.phase = TransactionPhase.COMMITTING;
            transaction.mutationStarted = true;
            if (!transaction.commit()) {
                transaction.rollbackSafely();
                return CraftResult.fail(CraftFailure.TRANSACTION_FAILED);
            }

            transaction.phase = TransactionPhase.OUTPUT_CREATION;
            ItemStack result = transaction.createResultStack();
            if (result.isEmpty() || !transaction.spawnOutput(result)) {
                transaction.rollbackSafely();
                return CraftResult.fail(CraftFailure.TRANSACTION_FAILED);
            }

            transaction.phase = TransactionPhase.COMPLETE;
            transaction.finishBestEffort();
            return CraftResult.success(result);
        } catch (RuntimeException exception) {
            if (!transaction.outputSpawned
                    && (transaction.mutationStarted
                            || !transaction.extractedForRollback.isEmpty())) {
                transaction.rollbackSafely();
            }

            LOGGER.error(
                    "Gunsmith transaction failed for player {} recipe {} phase {}",
                    player.getGameProfile().getName(),
                    recipe.getOutput(),
                    transaction.phase,
                    exception);
            return CraftResult.fail(CraftFailure.TRANSACTION_FAILED);
        }
    }

    private boolean plan() {
        var allocation = new IngredientAllocationPlanner(recipe, sources).allocate();
        slotPlans.clear();
        allocation.ifPresent(slotPlans::addAll);
        return allocation.isPresent();
    }

    private boolean simulate() {
        for (SlotPlan plan : this.slotPlans) {
            CraftingItemSource source = this.sources.get(plan.sourceIndex());
            ItemStack simulated = source.extractItem(plan.slot(), plan.amount(), true);

            if (!matchesExtracted(simulated, plan)) {
                return false;
            }
        }

        return true;
    }

    private boolean revalidate() {
        if (this.session == null
                || !this.session.validate(this.player, this.session.getContainerId())) {
            return false;
        }

        for (SlotPlan plan : this.slotPlans) {
            CraftingItemSource source = this.sources.get(plan.sourceIndex());

            if (!source.key().equals(plan.sourceKey()) || !source.isValid(this.player)) {
                return false;
            }

            ItemStack current = source.getStackInSlot(plan.slot());
            if (current.isEmpty()
                    || current.getCount() < plan.amount()
                    || !sameIdentity(current, plan.expectedStack())
                    || !matchesPredicates(current, plan.predicates())) {
                return false;
            }

            ItemStack simulated = source.extractItem(plan.slot(), plan.amount(), true);
            if (!matchesExtracted(simulated, plan)) {
                return false;
            }
        }

        return true;
    }

    private boolean commit() {
        for (SlotPlan plan : this.slotPlans) {
            CraftingItemSource source = this.sources.get(plan.sourceIndex());
            // Capture BEFORE extraction so mutation-then-throw is also recoverable.
            ItemStack snapshot = source.getStackInSlot(plan.slot()).copy();
            this.extractedForRollback.add(new ExtractedEntry(source, plan.slot(), snapshot));
            ItemStack extracted = source.extractItem(plan.slot(), plan.amount(), false);
            if (!matchesExtracted(extracted, plan)
                    || !ItemStack.matches(
                            snapshot.copyWithCount(snapshot.getCount() - plan.amount()),
                            source.getStackInSlot(plan.slot()))) return false;
        }
        return true;
    }

    private RollbackResult rollbackSafely() {
        boolean restored = true;
        for (int i = this.extractedForRollback.size() - 1; i >= 0; i--) {
            ExtractedEntry entry = this.extractedForRollback.get(i);
            try {
                entry.source().restoreSlot(entry.slot(), entry.stack());
                entry.source().markChanged();
                entry.source().synchronize(this.player);
            } catch (RuntimeException exception) {
                restored = false;
                LOGGER.error(
                        "CRITICAL: could not restore crafting source {} slot {} snapshot {}",
                        entry.source().key(),
                        entry.slot(),
                        entry.stack(),
                        exception);
            }
        }
        this.extractedForRollback.clear();
        return restored ? RollbackResult.FULLY_RESTORED : RollbackResult.PARTIALLY_COMPENSATED;
    }

    private void synchronizePlannedSources() {
        LinkedHashSet<CraftingItemSource> used = new LinkedHashSet<>();
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
            logSynchronizationFailure("player-inventory-broadcast", null, exception);
        }
    }

    private void synchronizeSources(Set<CraftingItemSource> sourcesToSynchronize) {
        for (CraftingItemSource source : sourcesToSynchronize) {
            try {
                source.markChanged();
            } catch (RuntimeException exception) {
                logSynchronizationFailure("mark-changed", source, exception);
            }

            try {
                source.synchronize(this.player);
            } catch (RuntimeException exception) {
                logSynchronizationFailure("synchronize", source, exception);
            }
        }
    }

    private ItemStack createResultStack() {
        ItemStack result = this.recipe.getResultItem(this.player.level().registryAccess()).copy();
        return result;
    }

    private boolean spawnOutput(ItemStack result) {
        ItemEntity itemEntity =
                new ItemEntity(
                        this.player.level(),
                        this.player.getX(),
                        this.player.getY() + 0.5D,
                        this.player.getZ(),
                        result.copy());
        itemEntity.setPickUpDelay(0);
        boolean added = this.player.level().addFreshEntity(itemEntity);
        if (added) {
            this.outputSpawned = true;
        }
        return added;
    }

    private void logSynchronizationFailure(
            String operation, @Nullable CraftingItemSource source, RuntimeException exception) {
        LOGGER.error(
                "Gunsmith post-commit {} failed for player {} source {} " + "phase {}",
                operation,
                playerName(),
                source == null ? "player-inventory" : source.key(),
                this.phase,
                exception);
    }

    private String playerName() {
        return this.player == null ? "unknown" : this.player.getGameProfile().getName();
    }

    private static boolean matchesExtracted(ItemStack extracted, SlotPlan plan) {
        return !extracted.isEmpty()
                && extracted.getCount() == plan.amount()
                && sameIdentity(extracted, plan.expectedStack())
                && matchesPredicates(extracted, plan.predicates());
    }

    private static boolean matchesPredicates(ItemStack stack, List<Ingredient> predicates) {
        for (Ingredient predicate : predicates) {
            if (!predicate.test(stack)) {
                return false;
            }
        }
        return true;
    }

    private static boolean sameIdentity(ItemStack first, ItemStack second) {
        return ItemStack.isSameItemSameComponents(first, second);
    }

    public record ExtractedEntry(CraftingItemSource source, int slot, ItemStack stack) {}

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
        PARTIALLY_COMPENSATED
    }

    public enum CraftFailure {
        INVENTORY_FULL,
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
        @Nullable private final CraftFailure failureReason;

        private CraftResult(
                boolean success, ItemStack output, @Nullable CraftFailure failureReason) {
            this.success = success;
            this.output = output;
            this.failureReason = failureReason;
        }

        public static CraftResult success(ItemStack output) {
            return new CraftResult(true, output.copy(), null);
        }

        public static CraftResult fail(CraftFailure reason) {
            return new CraftResult(false, ItemStack.EMPTY, reason);
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
