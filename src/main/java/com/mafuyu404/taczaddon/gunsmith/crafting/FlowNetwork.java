package com.mafuyu404.taczaddon.gunsmith.crafting;

import java.util.*;

final class FlowNetwork {
    private final List<FlowEdge>[] graph;
    private final int[] level;
    private final int[] nextEdge;

    @SuppressWarnings("unchecked")
    FlowNetwork(int nodeCount) {
        this.graph = new List[nodeCount];
        for (int node = 0; node < nodeCount; node++) {
            this.graph[node] = new ArrayList<>();
        }
        this.level = new int[nodeCount];
        this.nextEdge = new int[nodeCount];
    }

    FlowEdge addEdge(int from, int to, long capacity) {
        if (capacity < 0L) {
            throw new IllegalArgumentException("Negative flow capacity");
        }

        FlowEdge forward = new FlowEdge(to, this.graph[to].size(), capacity, capacity);
        FlowEdge reverse = new FlowEdge(from, this.graph[from].size(), 0L, 0L);

        this.graph[from].add(forward);
        this.graph[to].add(reverse);
        return forward;
    }

    long maxFlow(int source, int sink, long target) {
        long flow = 0L;

        while (flow < target && buildLevels(source, sink)) {
            Arrays.fill(this.nextEdge, 0);

            while (flow < target) {
                long pushed = push(source, sink, target - flow);
                if (pushed <= 0L) {
                    break;
                }
                flow += pushed;
            }
        }

        return flow;
    }

    private boolean buildLevels(int source, int sink) {
        Arrays.fill(this.level, -1);
        ArrayDeque<Integer> queue = new ArrayDeque<>();
        this.level[source] = 0;
        queue.add(source);

        while (!queue.isEmpty()) {
            int node = queue.removeFirst();

            for (FlowEdge edge : this.graph[node]) {
                if (edge.residualCapacity <= 0L || this.level[edge.to] >= 0) {
                    continue;
                }

                this.level[edge.to] = this.level[node] + 1;
                if (edge.to == sink) {
                    return true;
                }
                queue.addLast(edge.to);
            }
        }

        return this.level[sink] >= 0;
    }

    private long push(int node, int sink, long limit) {
        if (node == sink) {
            return limit;
        }

        for (; this.nextEdge[node] < this.graph[node].size(); this.nextEdge[node]++) {
            FlowEdge edge = this.graph[node].get(this.nextEdge[node]);

            if (edge.residualCapacity <= 0L || this.level[edge.to] != this.level[node] + 1) {
                continue;
            }

            long pushed = push(edge.to, sink, Math.min(limit, edge.residualCapacity));
            if (pushed <= 0L) {
                continue;
            }

            edge.residualCapacity -= pushed;
            FlowEdge reverse = this.graph[edge.to].get(edge.reverseIndex);
            reverse.residualCapacity += pushed;
            return pushed;
        }

        return 0L;
    }

    static final class FlowEdge {
        private final int to;
        private final int reverseIndex;
        private final long originalCapacity;
        private long residualCapacity;

        private FlowEdge(int to, int reverseIndex, long residualCapacity, long originalCapacity) {
            this.to = to;
            this.reverseIndex = reverseIndex;
            this.residualCapacity = residualCapacity;
            this.originalCapacity = originalCapacity;
        }

        long flow() {
            return this.originalCapacity - this.residualCapacity;
        }
    }
}
