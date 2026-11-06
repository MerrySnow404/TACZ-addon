package com.mafuyu404.taczaddon.client.gunsmith;

import com.mafuyu404.taczaddon.network.GunSmithCraftRequestPacket;
import com.mafuyu404.taczaddon.network.NetworkHandler;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.function.LongSupplier;
import javax.annotation.Nullable;

/**
 * In-flight bookkeeping for the extended gunsmith crafting path.
 *
 * <p>Once an extended request has been sent the same operation is never
 * resent: a timeout only releases the waiting state so the player can start a
 * new operation, and a rejected or partially successful request never falls
 * back to a native re-send.
 */
@OnlyIn(Dist.CLIENT)
public final class GunSmithCraftBridgeState {
    private static final long PENDING_TIMEOUT_MS = 10_000L;

    private final LongSupplier clock;
    private final RequestSender sender;

    private long nextCraftRequestId;
    private long pendingCraftRequestId = -1L;
    private long pendingSinceMs;

    public GunSmithCraftBridgeState() {
        this(
                System::currentTimeMillis,
                (containerId, requestId, recipeId, requestedCount) ->
                        NetworkHandler.CHANNEL.sendToServer(
                                new GunSmithCraftRequestPacket(
                                        containerId,
                                        requestId,
                                        recipeId,
                                        requestedCount
                                )
                        )
        );
    }

    GunSmithCraftBridgeState(
            LongSupplier clock,
            RequestSender sender
    ) {
        this.clock = clock;
        this.sender = sender;
    }

    /** Test seam mirroring the production network send. */
    @FunctionalInterface
    interface RequestSender {
        void send(
                int containerId,
                long requestId,
                ResourceLocation recipeId,
                int requestedCount
        );
    }

    public enum RequestStatus {
        /** The request was sent; its result may still arrive. */
        SENT,
        /** The same operation is still in flight; nothing was sent again. */
        BUSY,
        /** No recipe is selected, so nothing can be routed. */
        NO_RECIPE
    }

    public boolean acceptCraftResult(
            int containerId,
            long requestId
    ) {
        if (requestId != this.pendingCraftRequestId) {
            return false;
        }
        this.clearPending();
        return true;
    }

    /**
     * Sends at most one extended request per user action.
     */
    public RequestStatus tryRequestCraft(
            int containerId,
            @Nullable ResourceLocation recipeId,
            boolean shiftDown,
            int batchMax
    ) {
        if (recipeId == null) {
            return RequestStatus.NO_RECIPE;
        }

        long now = this.clock.getAsLong();
        if (this.pendingCraftRequestId >= 0L) {
            if (!this.timedOut(now)) {
                /*
                 * The previous operation is still in flight. Never re-send it
                 * and never allocate a second request id for it.
                 */
                return RequestStatus.BUSY;
            }
            /*
             * The previous operation timed out. It is abandoned, not retried;
             * the caller is a fresh user action.
             */
            this.clearPending();
        }

        int requestedCount = shiftDown
                ? Math.max(1, batchMax)
                : 1;
        long requestId = ++this.nextCraftRequestId;
        this.pendingCraftRequestId = requestId;
        this.pendingSinceMs = now;

        this.sender.send(
                containerId,
                requestId,
                recipeId,
                requestedCount
        );
        return RequestStatus.SENT;
    }

    /**
     * Legacy public compatibility entry point used by Beyond Integration's
     * Mixin callback. The void descriptor is part of the external ABI and
     * must not change.
     */
    public void requestCraft(
            int containerId,
            @Nullable ResourceLocation recipeId,
            boolean shiftDown,
            int batchMax
    ) {
        tryRequestCraft(
                containerId,
                recipeId,
                shiftDown,
                batchMax
        );
    }

    /**
     * Releases a timed-out request without re-sending anything.
     *
     * @return true when a pending request timed out during this tick
     */
    public boolean tick() {
        if (this.pendingCraftRequestId < 0L) {
            return false;
        }
        if (!this.timedOut(this.clock.getAsLong())) {
            return false;
        }
        this.clearPending();
        return true;
    }

    public boolean isWaiting() {
        return this.pendingCraftRequestId >= 0L;
    }

    public long pendingRequestId() {
        return this.pendingCraftRequestId;
    }

    private boolean timedOut(long now) {
        return now - this.pendingSinceMs >= PENDING_TIMEOUT_MS;
    }

    private void clearPending() {
        this.pendingCraftRequestId = -1L;
        this.pendingSinceMs = 0L;
    }
}
