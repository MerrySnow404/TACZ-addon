package com.mafuyu404.taczaddon.network;

import io.netty.handler.codec.DecoderException;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class GunSmithSourceSnapshotPacket {
    public static final int MAX_EXTERNAL_STACKS = 256;

    /**
     * Defensive wire bound only.
     *
     * <p>This is not a TaCZ recipe semantic limit. It is deliberately much
     * larger than ordinary recipes so addon/datapack recipes with more than
     * 32 inputs do not silently lose aggregate counts.
     */
    public static final int MAX_AGGREGATE_INPUTS = 1024;

    private final int containerId;
    private final long requestId;
    private final long sourceRevision;
    private final boolean externalSourcesAuthorized;
    private final boolean displayTruncated;
    private final List<ItemStack> externalStacks;
    private final int[] aggregateCounts;

    public GunSmithSourceSnapshotPacket(
            int containerId,
            long requestId,
            long sourceRevision,
            List<ItemStack> externalStacks
    ) {
        this(
                containerId,
                requestId,
                sourceRevision,
                false,
                false,
                externalStacks,
                new int[0]
        );
    }

    public GunSmithSourceSnapshotPacket(
            int containerId,
            long requestId,
            long sourceRevision,
            boolean externalSourcesAuthorized,
            boolean displayTruncated,
            List<ItemStack> externalStacks,
            int[] aggregateCounts
    ) {
        this.containerId = containerId;
        this.requestId = requestId;
        this.sourceRevision = sourceRevision;
        this.externalSourcesAuthorized = externalSourcesAuthorized;
        this.displayTruncated = displayTruncated;

        ArrayList<ItemStack> copies = new ArrayList<>();
        if (externalStacks != null) {
            int size = Math.min(
                    externalStacks.size(),
                    MAX_EXTERNAL_STACKS
            );
            for (int index = 0; index < size; index++) {
                ItemStack stack = externalStacks.get(index);
                if (stack != null && !stack.isEmpty()) {
                    copies.add(stack.copy());
                }
            }
        }
        this.externalStacks =
                Collections.unmodifiableList(copies);

        if (aggregateCounts == null
                || aggregateCounts.length > MAX_AGGREGATE_INPUTS) {
            this.aggregateCounts = new int[0];
        } else {
            this.aggregateCounts = aggregateCounts.clone();
        }
    }

    public static void encode(
            GunSmithSourceSnapshotPacket message,
            FriendlyByteBuf buffer
    ) {
        buffer.writeInt(message.containerId);
        buffer.writeLong(message.requestId);
        buffer.writeLong(message.sourceRevision);
        buffer.writeBoolean(message.externalSourcesAuthorized);
        buffer.writeBoolean(message.displayTruncated);
        buffer.writeInt(message.externalStacks.size());

        for (ItemStack stack : message.externalStacks) {
            buffer.writeItemStack(stack, true);
        }

        buffer.writeInt(message.aggregateCounts.length);
        for (int count : message.aggregateCounts) {
            buffer.writeInt(count);
        }
    }

    public static GunSmithSourceSnapshotPacket decode(
            FriendlyByteBuf buffer
    ) {
        int containerId = buffer.readInt();
        long requestId = buffer.readLong();
        long sourceRevision = buffer.readLong();
        boolean externalSourcesAuthorized = buffer.readBoolean();
        boolean displayTruncated = buffer.readBoolean();
        int declaredSize = buffer.readInt();

        if (declaredSize < 0
                || declaredSize > MAX_EXTERNAL_STACKS) {
            throw new DecoderException(
                    "Invalid gunsmith source snapshot size: "
                            + declaredSize
            );
        }

        ArrayList<ItemStack> stacks =
                new ArrayList<>(declaredSize);
        for (int index = 0; index < declaredSize; index++) {
            stacks.add(buffer.readItem());
        }

        int aggregateSize = buffer.readInt();
        if (aggregateSize < 0
                || aggregateSize > MAX_AGGREGATE_INPUTS) {
            throw new DecoderException(
                    "Invalid gunsmith aggregate count size: "
                            + aggregateSize
            );
        }
        int[] aggregateCounts = new int[aggregateSize];
        for (int index = 0; index < aggregateSize; index++) {
            int count = buffer.readInt();
            if (count < 0) {
                throw new DecoderException(
                        "Invalid gunsmith aggregate count: " + count
                );
            }
            aggregateCounts[index] = count;
        }

        return new GunSmithSourceSnapshotPacket(
                containerId,
                requestId,
                sourceRevision,
                externalSourcesAuthorized,
                displayTruncated,
                stacks,
                aggregateCounts
        );
    }

    public int containerId() {
        return this.containerId;
    }

    public long requestId() {
        return this.requestId;
    }

    public long sourceRevision() {
        return this.sourceRevision;
    }

    public List<ItemStack> externalStacks() {
        ArrayList<ItemStack> copies =
                new ArrayList<>(this.externalStacks.size());
        for (ItemStack stack : this.externalStacks) {
            copies.add(stack.copy());
        }
        return Collections.unmodifiableList(copies);
    }

    /**
     * True only when the authoritative server resolved a trustworthy
     * workbench anchor for this menu, which is what authorizes external
     * material sources. A legitimate menu without an anchor keeps the native
     * crafting path and never reads external sources.
     */
    public boolean externalSourcesAuthorized() {
        return this.externalSourcesAuthorized;
    }

    /**
     * True when the display entry list had to be trimmed by the display
     * budget. Aggregate counts stay complete either way.
     */
    public boolean displayTruncated() {
        return this.displayTruncated;
    }

    /** Per-input aggregated material counts, independent of the display budget. */
    public int[] aggregateCounts() {
        return this.aggregateCounts.clone();
    }
}
