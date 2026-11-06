package com.mafuyu404.taczaddon.client.refit;

import com.mafuyu404.taczaddon.client.gunsmith.GunSmithSourceScreenAccess;
import com.mafuyu404.taczaddon.network.NetworkHandler;
import com.mafuyu404.taczaddon.network.RefitSourceRefreshRequestPacket;
import com.mafuyu404.taczaddon.refit.RefitSourceResolver;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@OnlyIn(Dist.CLIENT)
public final class RefitExternalSourceState {
    private static final int REFRESH_INTERVAL_TICKS = 30;
    private static final int REFRESH_TIMEOUT_TICKS = 100;

    private final RefreshSender refreshSender;

    private long nextRefreshRequestId;
    private int ticksUntilRefresh = REFRESH_INTERVAL_TICKS;

    private long pendingRefreshRequestId = -1L;
    private long latestAcceptedRefreshRequestId = -1L;
    private boolean refreshInFlight;
    private int pendingRefreshAgeTicks;

    private boolean hasAcceptedSnapshot;
    private List<RefitSourceResolver.RefitExternalCandidate>
            externalCandidates = List.of();

    @FunctionalInterface
    public interface RefreshSender {
        void send(long requestId);
    }

    public RefitExternalSourceState() {
        this(requestId ->
                NetworkHandler.CHANNEL.sendToServer(
                        new RefitSourceRefreshRequestPacket(requestId)
                )
        );
    }

    public RefitExternalSourceState(RefreshSender refreshSender) {
        this.refreshSender = refreshSender;
    }

    public GunSmithSourceScreenAccess.AcceptResult acceptSnapshot(
            long requestId,
            List<RefitSourceResolver.RefitExternalCandidate> candidates
    ) {
        if (!this.refreshInFlight
                || requestId != this.pendingRefreshRequestId) {
            return GunSmithSourceScreenAccess.AcceptResult.REJECTED;
        }

        if (requestId <= this.latestAcceptedRefreshRequestId) {
            this.clearPendingRefresh();
            return GunSmithSourceScreenAccess.AcceptResult.REJECTED;
        }

        List<RefitSourceResolver.RefitExternalCandidate> normalized =
                immutableSnapshot(candidates);
        boolean changed = !this.hasAcceptedSnapshot
                || !sameSnapshot(
                this.externalCandidates,
                normalized
        );

        this.latestAcceptedRefreshRequestId = requestId;
        this.clearPendingRefresh();
        this.ticksUntilRefresh = REFRESH_INTERVAL_TICKS;

        if (!changed) {
            return GunSmithSourceScreenAccess.AcceptResult.UNCHANGED;
        }

        this.externalCandidates = normalized;
        this.hasAcceptedSnapshot = true;
        return GunSmithSourceScreenAccess.AcceptResult.UPDATED;
    }

    public void requestSourceRefresh() {
        long requestId = ++this.nextRefreshRequestId;
        this.pendingRefreshRequestId = requestId;
        this.refreshInFlight = true;
        this.pendingRefreshAgeTicks = 0;
        this.refreshSender.send(requestId);
    }

    public void tickSourceRefresh() {
        if (this.refreshInFlight) {
            this.pendingRefreshAgeTicks++;
            if (this.pendingRefreshAgeTicks
                    >= REFRESH_TIMEOUT_TICKS) {
                this.clearPendingRefresh();
                this.requestSourceRefresh();
            }
            return;
        }

        if (--this.ticksUntilRefresh > 0) {
            return;
        }

        this.ticksUntilRefresh = REFRESH_INTERVAL_TICKS;
        this.requestSourceRefresh();
    }

    public void onScreenInit() {
        if (!this.refreshInFlight) {
            this.requestSourceRefresh();
        }
    }

    public List<RefitSourceResolver.RefitExternalCandidate>
    getExternalCandidates() {
        return this.externalCandidates;
    }

    public long getLatestAcceptedRefreshRequestId() {
        return this.latestAcceptedRefreshRequestId;
    }

    private void clearPendingRefresh() {
        this.pendingRefreshRequestId = -1L;
        this.refreshInFlight = false;
        this.pendingRefreshAgeTicks = 0;
    }

    private static List<RefitSourceResolver.RefitExternalCandidate>
    immutableSnapshot(
            List<RefitSourceResolver.RefitExternalCandidate> candidates
    ) {
        if (candidates == null || candidates.isEmpty()) {
            return List.of();
        }

        ArrayList<RefitSourceResolver.RefitExternalCandidate> copies =
                new ArrayList<>(candidates.size());
        for (RefitSourceResolver.RefitExternalCandidate candidate
                : candidates) {
            if (candidate != null
                    && candidate.displayStack() != null
                    && !candidate.displayStack().isEmpty()) {
                copies.add(candidate.copy());
            }
        }

        if (copies.isEmpty()) {
            return List.of();
        }
        return Collections.unmodifiableList(copies);
    }

    static boolean sameSnapshot(
            List<RefitSourceResolver.RefitExternalCandidate> left,
            List<RefitSourceResolver.RefitExternalCandidate> right
    ) {
        if (left == right) {
            return true;
        }
        if (left == null || right == null
                || left.size() != right.size()) {
            return false;
        }

        for (int index = 0; index < left.size(); index++) {
            RefitSourceResolver.RefitExternalCandidate a = left.get(index);
            RefitSourceResolver.RefitExternalCandidate b = right.get(index);
            if (!a.attachmentId().equals(b.attachmentId())
                    || a.type() != b.type()
                    || !a.locator().equals(b.locator())
                    || !net.minecraft.world.item.ItemStack.matches(
                    a.displayStack(),
                    b.displayStack()
            )) {
                return false;
            }
        }
        return true;
    }
}
