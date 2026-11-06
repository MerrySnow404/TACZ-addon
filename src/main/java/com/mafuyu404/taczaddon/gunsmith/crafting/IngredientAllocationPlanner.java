package com.mafuyu404.taczaddon.gunsmith.crafting;

import com.mafuyu404.taczaddon.gunsmith.crafting.FlowNetwork.FlowEdge;
import com.tacz.guns.crafting.GunSmithTableIngredient;
import com.tacz.guns.crafting.GunSmithTableRecipe;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;

import java.util.*;

/** Computes a deterministic allocation without mutating any source. */
final class IngredientAllocationPlanner {
    private final GunSmithTableRecipe recipe;
    private final List<CraftingItemSource> sources;
    private final ArrayList<SlotPlan> slotPlans = new ArrayList<>();

    IngredientAllocationPlanner(GunSmithTableRecipe recipe, List<CraftingItemSource> sources) {
        this.recipe = recipe;
        this.sources = sources;
    }

    Optional<List<SlotPlan>> allocate() {
        return plan() ? Optional.of(List.copyOf(slotPlans)) : Optional.empty();
    }

    /**
     * Builds one deterministic global material allocation.
     *
     * <p>A simple ingredient-by-ingredient greedy allocator is not sufficient: overlapping
     * predicates can have a valid assignment that greedy ordering misses. Example: ingredient A
     * accepts {iron,gold}, ingredient B accepts {iron}, inventory has one iron and one gold. A
     * greedy A-first pass may reserve iron and incorrectly reject B.
     *
     * <p>Model the recipe as an integral max-flow problem instead:
     *
     * <p>source -> ingredient demand -> candidate slot -> sink
     *
     * <p>Slot order remains deterministic (player inventory first, then the already deterministic
     * external-source order). Max flow may reroute an earlier broad predicate only when that is
     * required to satisfy a narrower predicate.
     */
    private boolean plan() {
        this.slotPlans.clear();

        List<GunSmithTableIngredient> inputs = this.recipe.getInputs();
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

            demands.add(new IngredientDemand(ingredient, required));
            totalRequired += required;
        }

        if (totalRequired == 0L) {
            return true;
        }

        ArrayList<SlotCandidate> candidates = collectSlotCandidates(demands);
        if (candidates.isEmpty()) {
            return false;
        }

        return allocateByMaxFlow(demands, candidates, totalRequired);
    }

    private ArrayList<SlotCandidate> collectSlotCandidates(List<IngredientDemand> demands) {
        ArrayList<SlotCandidate> candidates = new ArrayList<>();

        for (int sourceIndex = 0; sourceIndex < this.sources.size(); sourceIndex++) {
            CraftingItemSource source = this.sources.get(sourceIndex);
            int slotCount = source.slotCount();
            if (slotCount <= 0) {
                continue;
            }

            for (int slot = 0; slot < slotCount; slot++) {
                ItemStack current = source.getStackInSlot(slot);
                if (current == null || current.isEmpty() || current.getCount() <= 0) {
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

                candidates.add(
                        new SlotCandidate(
                                sourceIndex,
                                source.key(),
                                slot,
                                current.copyWithCount(1),
                                current.getCount()));
            }
        }

        return candidates;
    }

    private boolean allocateByMaxFlow(
            List<IngredientDemand> demands, List<SlotCandidate> candidates, long totalRequired) {
        int demandCount = demands.size();
        int candidateCount = candidates.size();

        int sourceNode = 0;
        int demandNodeBase = 1;
        int candidateNodeBase = demandNodeBase + demandCount;
        int sinkNode = candidateNodeBase + candidateCount;

        FlowNetwork network = new FlowNetwork(sinkNode + 1);
        ArrayList<AllocationArc> allocationArcs = new ArrayList<>();

        for (int demandIndex = 0; demandIndex < demandCount; demandIndex++) {
            IngredientDemand demand = demands.get(demandIndex);
            network.addEdge(sourceNode, demandNodeBase + demandIndex, demand.amount());
        }

        for (int candidateIndex = 0; candidateIndex < candidateCount; candidateIndex++) {
            SlotCandidate candidate = candidates.get(candidateIndex);
            network.addEdge(candidateNodeBase + candidateIndex, sinkNode, candidate.available());
        }

        /*
         * Add demand->slot edges in deterministic recipe/slot order. Dinic's
         * residual graph can reroute earlier choices when necessary, while
         * preserving deterministic results for an unchanged source snapshot.
         */
        for (int demandIndex = 0; demandIndex < demandCount; demandIndex++) {
            IngredientDemand demand = demands.get(demandIndex);

            for (int candidateIndex = 0; candidateIndex < candidateCount; candidateIndex++) {
                SlotCandidate candidate = candidates.get(candidateIndex);

                if (!demand.ingredient().test(candidate.expectedStack())) {
                    continue;
                }

                long capacity = Math.min((long) demand.amount(), (long) candidate.available());
                FlowEdge edge =
                        network.addEdge(
                                demandNodeBase + demandIndex,
                                candidateNodeBase + candidateIndex,
                                capacity);
                allocationArcs.add(new AllocationArc(demandIndex, candidateIndex, edge));
            }
        }

        long allocated = network.maxFlow(sourceNode, sinkNode, totalRequired);
        if (allocated != totalRequired) {
            return false;
        }

        MutableSlotPlan[] plansByCandidate = new MutableSlotPlan[candidateCount];

        for (AllocationArc arc : allocationArcs) {
            long assignedLong = arc.edge().flow();
            if (assignedLong <= 0L) {
                continue;
            }

            int assigned = Math.toIntExact(assignedLong);
            SlotCandidate candidate = candidates.get(arc.candidateIndex());
            IngredientDemand demand = demands.get(arc.demandIndex());

            MutableSlotPlan mutable = plansByCandidate[arc.candidateIndex()];
            if (mutable == null) {
                mutable =
                        new MutableSlotPlan(
                                candidate.sourceIndex(),
                                candidate.sourceKey(),
                                candidate.slot(),
                                candidate.expectedStack());
                plansByCandidate[arc.candidateIndex()] = mutable;
            }

            mutable.amount += assigned;
            mutable.predicates.add(demand.ingredient());
        }

        /*
         * Freeze in candidate order, which is source order then slot order.
         * This keeps commit/rollback deterministic.
         */
        for (MutableSlotPlan mutable : plansByCandidate) {
            if (mutable != null && mutable.amount > 0) {
                this.slotPlans.add(mutable.freeze());
            }
        }

        return true;
    }

    private record IngredientDemand(Ingredient ingredient, int amount) {}

    private record SlotCandidate(
            int sourceIndex,
            CraftingSourceKey sourceKey,
            int slot,
            ItemStack expectedStack,
            int available) {}

    private record AllocationArc(int demandIndex, int candidateIndex, FlowEdge edge) {}

    private static final class MutableSlotPlan {
        private final int sourceIndex;
        private final CraftingSourceKey sourceKey;
        private final int slot;
        private final ItemStack expectedStack;
        private final ArrayList<Ingredient> predicates = new ArrayList<>();
        private int amount;

        private MutableSlotPlan(
                int sourceIndex, CraftingSourceKey sourceKey, int slot, ItemStack expectedStack) {
            this.sourceIndex = sourceIndex;
            this.sourceKey = sourceKey;
            this.slot = slot;
            this.expectedStack = expectedStack;
        }

        private SlotPlan freeze() {
            return new SlotPlan(
                    this.sourceIndex,
                    this.sourceKey,
                    this.slot,
                    this.expectedStack.copy(),
                    this.amount,
                    List.copyOf(this.predicates));
        }
    }

    record SlotPlan(
            int sourceIndex,
            CraftingSourceKey sourceKey,
            int slot,
            ItemStack expectedStack,
            int amount,
            List<Ingredient> predicates) {}
}
