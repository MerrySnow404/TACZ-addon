package com.mafuyu404.taczaddon.gunsmith;

import com.mafuyu404.taczaddon.storage.api.CraftingSourceKey;

import com.tacz.guns.crafting.GunSmithTableIngredient;
import com.tacz.guns.crafting.GunSmithTableRecipe;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;

import java.util.*;
import javax.annotation.Nullable;

/** Pure ingredient allocation; never extracts, inserts or rolls back items. */
final class MaterialAllocation {
    private MaterialAllocation() {}
    static boolean canSatisfyStacks(
            @Nullable GunSmithTableRecipe recipe,
            List<ItemStack> stacks
    ) {
        if (recipe == null || stacks == null) {
            return false;
        }

        List<GunSmithTableIngredient> inputs =
                recipe.getInputs();

        if (inputs == null) {
            return false;
        }

        ArrayList<IngredientDemand> demands =
                new ArrayList<>();

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

            demands.add(
                    new IngredientDemand(
                            ingredient,
                            required
                    )
            );
            totalRequired += required;
        }

        if (totalRequired == 0L) {
            return true;
        }

        ArrayList<ItemStack> candidates =
                new ArrayList<>();

        for (ItemStack stack : stacks) {
            if (stack == null
                    || stack.isEmpty()
                    || stack.getCount() <= 0) {
                continue;
            }

            boolean useful = false;
            for (IngredientDemand demand : demands) {
                if (demand.ingredient().test(stack)) {
                    useful = true;
                    break;
                }
            }

            if (useful) {
                candidates.add(stack);
            }
        }

        if (candidates.isEmpty()) {
            return false;
        }

        int demandCount = demands.size();
        int candidateCount = candidates.size();

        int sourceNode = 0;
        int demandNodeBase = 1;
        int candidateNodeBase =
                demandNodeBase + demandCount;
        int sinkNode =
                candidateNodeBase + candidateCount;

        FlowNetwork network =
                new FlowNetwork(sinkNode + 1);

        for (int demandIndex = 0;
             demandIndex < demandCount;
             demandIndex++) {
            IngredientDemand demand =
                    demands.get(demandIndex);

            network.addEdge(
                    sourceNode,
                    demandNodeBase + demandIndex,
                    demand.amount()
            );
        }

        for (int candidateIndex = 0;
             candidateIndex < candidateCount;
             candidateIndex++) {
            ItemStack candidate =
                    candidates.get(candidateIndex);

            network.addEdge(
                    candidateNodeBase + candidateIndex,
                    sinkNode,
                    candidate.getCount()
            );
        }

        for (int demandIndex = 0;
             demandIndex < demandCount;
             demandIndex++) {
            IngredientDemand demand =
                    demands.get(demandIndex);

            for (int candidateIndex = 0;
                 candidateIndex < candidateCount;
                 candidateIndex++) {
                ItemStack candidate =
                        candidates.get(candidateIndex);

                if (!demand.ingredient().test(candidate)) {
                    continue;
                }

                network.addEdge(
                        demandNodeBase + demandIndex,
                        candidateNodeBase + candidateIndex,
                        Math.min(
                                (long) demand.amount(),
                                (long) candidate.getCount()
                        )
                );
            }
        }

        return network.maxFlow(
                sourceNode,
                sinkNode,
                totalRequired
        ) == totalRequired;
    }
    static boolean allocateByMaxFlow(
            List<IngredientDemand> demands,
            List<SlotCandidate> candidates,
            long totalRequired, List<SlotPlan> slotPlans
    ) {
        int demandCount = demands.size();
        int candidateCount = candidates.size();

        int sourceNode = 0;
        int demandNodeBase = 1;
        int candidateNodeBase =
                demandNodeBase + demandCount;
        int sinkNode =
                candidateNodeBase + candidateCount;

        FlowNetwork network =
                new FlowNetwork(sinkNode + 1);
        ArrayList<AllocationArc> allocationArcs =
                new ArrayList<>();

        for (int demandIndex = 0;
             demandIndex < demandCount;
             demandIndex++) {
            IngredientDemand demand =
                    demands.get(demandIndex);
            network.addEdge(
                    sourceNode,
                    demandNodeBase + demandIndex,
                    demand.amount()
            );
        }

        for (int candidateIndex = 0;
             candidateIndex < candidateCount;
             candidateIndex++) {
            SlotCandidate candidate =
                    candidates.get(candidateIndex);
            network.addEdge(
                    candidateNodeBase + candidateIndex,
                    sinkNode,
                    candidate.available()
            );
        }

        /*
         * Add demand->slot edges in deterministic recipe/slot order. Dinic's
         * residual graph can reroute earlier choices when necessary, while
         * preserving deterministic results for an unchanged source snapshot.
         */
        for (int demandIndex = 0;
             demandIndex < demandCount;
             demandIndex++) {
            IngredientDemand demand =
                    demands.get(demandIndex);

            for (int candidateIndex = 0;
                 candidateIndex < candidateCount;
                 candidateIndex++) {
                SlotCandidate candidate =
                        candidates.get(candidateIndex);

                if (!demand.ingredient().test(
                        candidate.expectedStack()
                )) {
                    continue;
                }

                long capacity = Math.min(
                        (long) demand.amount(),
                        (long) candidate.available()
                );
                FlowEdge edge = network.addEdge(
                        demandNodeBase + demandIndex,
                        candidateNodeBase + candidateIndex,
                        capacity
                );
                allocationArcs.add(new AllocationArc(
                        demandIndex,
                        candidateIndex,
                        edge
                ));
            }
        }

        long allocated = network.maxFlow(
                sourceNode,
                sinkNode,
                totalRequired
        );
        if (allocated != totalRequired) {
            return false;
        }

        MutableSlotPlan[] plansByCandidate =
                new MutableSlotPlan[candidateCount];

        for (AllocationArc arc : allocationArcs) {
            long assignedLong = arc.edge().flow();
            if (assignedLong <= 0L) {
                continue;
            }

            int assigned = Math.toIntExact(assignedLong);
            SlotCandidate candidate =
                    candidates.get(arc.candidateIndex());
            IngredientDemand demand =
                    demands.get(arc.demandIndex());

            MutableSlotPlan mutable =
                    plansByCandidate[arc.candidateIndex()];
            if (mutable == null) {
                mutable = new MutableSlotPlan(
                        candidate.sourceIndex(),
                        candidate.sourceKey(),
                        candidate.slot(),
                        candidate.expectedStack()
                );
                plansByCandidate[arc.candidateIndex()] =
                        mutable;
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
                slotPlans.add(mutable.freeze());
            }
        }

        return true;
    }
    record IngredientDemand(
            Ingredient ingredient,
            int amount
    ) {
    }

    record SlotCandidate(
            int sourceIndex,
            CraftingSourceKey sourceKey,
            int slot,
            ItemStack expectedStack,
            int available
    ) {
    }

    private record AllocationArc(
            int demandIndex,
            int candidateIndex,
            FlowEdge edge
    ) {
    }

    private static final class FlowNetwork {
        private final List<FlowEdge>[] graph;
        private final int[] level;
        private final int[] nextEdge;

        @SuppressWarnings("unchecked")
        private FlowNetwork(int nodeCount) {
            this.graph = new List[nodeCount];
            for (int node = 0; node < nodeCount; node++) {
                this.graph[node] = new ArrayList<>();
            }
            this.level = new int[nodeCount];
            this.nextEdge = new int[nodeCount];
        }

        private FlowEdge addEdge(
                int from,
                int to,
                long capacity
        ) {
            if (capacity < 0L) {
                throw new IllegalArgumentException(
                        "Negative flow capacity"
                );
            }

            FlowEdge forward = new FlowEdge(
                    to,
                    this.graph[to].size(),
                    capacity,
                    capacity
            );
            FlowEdge reverse = new FlowEdge(
                    from,
                    this.graph[from].size(),
                    0L,
                    0L
            );

            this.graph[from].add(forward);
            this.graph[to].add(reverse);
            return forward;
        }

        private long maxFlow(
                int source,
                int sink,
                long target
        ) {
            long flow = 0L;

            while (flow < target
                    && buildLevels(source, sink)) {
                Arrays.fill(this.nextEdge, 0);

                while (flow < target) {
                    long pushed = push(
                            source,
                            sink,
                            target - flow
                    );
                    if (pushed <= 0L) {
                        break;
                    }
                    flow += pushed;
                }
            }

            return flow;
        }

        private boolean buildLevels(
                int source,
                int sink
        ) {
            Arrays.fill(this.level, -1);
            ArrayDeque<Integer> queue =
                    new ArrayDeque<>();
            this.level[source] = 0;
            queue.add(source);

            while (!queue.isEmpty()) {
                int node = queue.removeFirst();

                for (FlowEdge edge : this.graph[node]) {
                    if (edge.residualCapacity <= 0L
                            || this.level[edge.to] >= 0) {
                        continue;
                    }

                    this.level[edge.to] =
                            this.level[node] + 1;
                    if (edge.to == sink) {
                        return true;
                    }
                    queue.addLast(edge.to);
                }
            }

            return this.level[sink] >= 0;
        }

        private long push(
                int node,
                int sink,
                long limit
        ) {
            if (node == sink) {
                return limit;
            }

            for (;
                 this.nextEdge[node] < this.graph[node].size();
                 this.nextEdge[node]++) {
                FlowEdge edge = this.graph[node]
                        .get(this.nextEdge[node]);

                if (edge.residualCapacity <= 0L
                        || this.level[edge.to]
                        != this.level[node] + 1) {
                    continue;
                }

                long pushed = push(
                        edge.to,
                        sink,
                        Math.min(
                                limit,
                                edge.residualCapacity
                        )
                );
                if (pushed <= 0L) {
                    continue;
                }

                edge.residualCapacity -= pushed;
                FlowEdge reverse =
                        this.graph[edge.to]
                                .get(edge.reverseIndex);
                reverse.residualCapacity += pushed;
                return pushed;
            }

            return 0L;
        }
    }

    private static final class FlowEdge {
        private final int to;
        private final int reverseIndex;
        private final long originalCapacity;
        private long residualCapacity;

        private FlowEdge(
                int to,
                int reverseIndex,
                long residualCapacity,
                long originalCapacity
        ) {
            this.to = to;
            this.reverseIndex = reverseIndex;
            this.residualCapacity = residualCapacity;
            this.originalCapacity = originalCapacity;
        }

        private long flow() {
            return this.originalCapacity
                    - this.residualCapacity;
        }
    }

    private static final class MutableSlotPlan {
        private final int sourceIndex;
        private final CraftingSourceKey sourceKey;
        private final int slot;
        private final ItemStack expectedStack;
        private final ArrayList<Ingredient> predicates =
                new ArrayList<>();
        private int amount;

        private MutableSlotPlan(
                int sourceIndex,
                CraftingSourceKey sourceKey,
                int slot,
                ItemStack expectedStack
        ) {
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
                    List.copyOf(this.predicates)
            );
        }
    }

    record SlotPlan(
            int sourceIndex,
            CraftingSourceKey sourceKey,
            int slot,
            ItemStack expectedStack,
            int amount,
            List<Ingredient> predicates
    ) {
    }

}
