package com.mafuyu404.taczaddon.client.gunsmith;

import com.mafuyu404.taczaddon.client.gunsmith.GunSmithSourceScreenAccess;
import com.mafuyu404.taczaddon.network.GunSmithSourceRefreshRequestPacket;
import com.mafuyu404.taczaddon.network.NetworkHandler;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import javax.annotation.Nullable;

@OnlyIn(Dist.CLIENT)
public final class GunSmithExternalSourceState {
    private static final int REFRESH_INTERVAL_TICKS = 30;
    private static final int REFRESH_TIMEOUT_TICKS = 100;

    private final RefreshSender refreshSender;

    private long nextRefreshRequestId;
    private int trackedContainerId = Integer.MIN_VALUE;
    private int ticksUntilRefresh = REFRESH_INTERVAL_TICKS;

    private long pendingRefreshRequestId = -1L;
    private long latestAcceptedRefreshRequestId = -1L;
    private boolean refreshInFlight;
    private int pendingRefreshAgeTicks;

    private boolean hasAcceptedSnapshot;
    private long latestAcceptedSourceRevision = -1L;

    private List<ItemStack> externalDisplayStacks = List.of();
    private boolean externalSourcesAuthorized;
    private boolean displayTruncated;

    private int[] aggregateCounts = new int[0];

    /**
     * Recipe currently selected by the client UI.
     *
     * This is the recipe all automatic refreshes must target.
     */
    @Nullable
    private ResourceLocation desiredRecipeId;

    /**
     * Recipe associated with the currently outstanding request.
     *
     * requestId + pendingRecipeId form one logical request identity.
     */
    @Nullable
    private ResourceLocation pendingRecipeId;

    /**
     * Recipe for which aggregateCounts was calculated.
     *
     * aggregateCounts must never be consumed for any other recipe,
     * even if both recipes happen to have the same number of inputs.
     */
    @Nullable
    private ResourceLocation acceptedAggregateRecipeId;

    @FunctionalInterface
    public interface RefreshSender {
        void send(
                int containerId,
                long requestId,
                @Nullable ResourceLocation recipeId
        );
    }

    public GunSmithExternalSourceState() {
        this((containerId, requestId, recipeId) ->
                NetworkHandler.CHANNEL.sendToServer(
                        new GunSmithSourceRefreshRequestPacket(
                                containerId,
                                requestId,
                                recipeId
                        )
                )
        );
    }

    GunSmithExternalSourceState(
            RefreshSender refreshSender
    ) {
        this.refreshSender = Objects.requireNonNull(
                refreshSender,
                "refreshSender"
        );
    }

    public GunSmithSourceScreenAccess.AcceptResult
    acceptSourceSnapshot(
            int containerId,
            long requestId,
            long sourceRevision,
            List<ItemStack> externalStacks
    ) {
        return acceptSourceSnapshot(
                containerId,
                requestId,
                sourceRevision,
                false,
                false,
                externalStacks,
                new int[0]
        );
    }

    public GunSmithSourceScreenAccess.AcceptResult
    acceptSourceSnapshot(
            int containerId,
            long requestId,
            long sourceRevision,
            boolean externalSourcesAuthorized,
            boolean displayTruncated,
            List<ItemStack> externalStacks,
            int[] aggregateCounts
    ) {
        if (containerId != this.trackedContainerId) {
            return GunSmithSourceScreenAccess.AcceptResult.REJECTED;
        }

        if (!this.refreshInFlight) {
            return GunSmithSourceScreenAccess.AcceptResult.REJECTED;
        }

        if (requestId != this.pendingRefreshRequestId) {
            return GunSmithSourceScreenAccess.AcceptResult.REJECTED;
        }

        if (requestId <= this.latestAcceptedRefreshRequestId) {
            this.clearPendingRefresh();
            return GunSmithSourceScreenAccess.AcceptResult.REJECTED;
        }

        /*
         * Capture request identity before clearPendingRefresh().
         */
        ResourceLocation responseRecipeId =
                this.pendingRecipeId;

        List<ItemStack> normalizedSnapshot =
                immutableSnapshot(externalStacks);

        int[] normalizedAggregate =
                aggregateCounts == null
                        ? new int[0]
                        : aggregateCounts.clone();

        /*
         * Recipe identity is part of the snapshot identity.
         *
         * Two recipes may legitimately produce identical aggregate arrays.
         * Such a response must still be accepted as UPDATED when it belongs
         * to a different recipe.
         */
        boolean changed =
                !this.hasAcceptedSnapshot
                        || sourceRevision
                        != this.latestAcceptedSourceRevision
                        || externalSourcesAuthorized
                        != this.externalSourcesAuthorized
                        || displayTruncated
                        != this.displayTruncated
                        || !Objects.equals(
                        responseRecipeId,
                        this.acceptedAggregateRecipeId
                )
                        || !java.util.Arrays.equals(
                        normalizedAggregate,
                        this.aggregateCounts
                )
                        || !sameSnapshot(
                        this.externalDisplayStacks,
                        normalizedSnapshot
                );

        this.latestAcceptedRefreshRequestId =
                requestId;

        this.clearPendingRefresh();
        this.ticksUntilRefresh =
                REFRESH_INTERVAL_TICKS;

        if (!changed) {
            return GunSmithSourceScreenAccess.AcceptResult.UNCHANGED;
        }

        this.externalDisplayStacks =
                normalizedSnapshot;
        this.latestAcceptedSourceRevision =
                sourceRevision;
        this.externalSourcesAuthorized =
                externalSourcesAuthorized;
        this.displayTruncated =
                displayTruncated;
        this.aggregateCounts =
                normalizedAggregate;

        this.acceptedAggregateRecipeId =
                responseRecipeId;

        this.hasAcceptedSnapshot = true;

        return GunSmithSourceScreenAccess.AcceptResult.UPDATED;
    }

    /**
     * Force a refresh for the recipe currently tracked by this screen.
     */
    public void requestSourceRefresh(
            int containerId
    ) {
        requestSourceRefresh(
                containerId,
                this.desiredRecipeId
        );
    }

    /**
     * Force a refresh and make recipeId the authoritative current recipe.
     */
    public void requestSourceRefresh(
            int containerId,
            @Nullable ResourceLocation recipeId
    ) {
        if (containerId != this.trackedContainerId) {
            this.resetForContainer(containerId);
        }

        this.updateDesiredRecipe(recipeId);

        long requestId =
                ++this.nextRefreshRequestId;

        this.pendingRefreshRequestId =
                requestId;
        this.pendingRecipeId =
                recipeId;
        this.refreshInFlight =
                true;
        this.pendingRefreshAgeTicks =
                0;

        this.refreshSender.send(
                containerId,
                requestId,
                recipeId
        );
    }

    public void tickSourceRefresh(
            int containerId
    ) {
        if (containerId != this.trackedContainerId) {
            return;
        }

        if (this.refreshInFlight) {
            this.pendingRefreshAgeTicks++;

            if (this.pendingRefreshAgeTicks
                    >= REFRESH_TIMEOUT_TICKS) {
                this.clearPendingRefresh();

                this.requestSourceRefresh(
                        containerId,
                        this.desiredRecipeId
                );
            }
            return;
        }

        if (--this.ticksUntilRefresh > 0) {
            return;
        }

        this.ticksUntilRefresh =
                REFRESH_INTERVAL_TICKS;

        this.requestSourceRefresh(
                containerId,
                this.desiredRecipeId
        );
    }

    /**
     * Called after the screen has completed one init pass.
     *
     * The constructor may already have observed the selected recipe through
     * getPlayerIngredientCount(), but that early call must not send packets.
     */
    public void onScreenInit(
            int containerId,
            @Nullable ResourceLocation recipeId
    ) {
        if (containerId != this.trackedContainerId) {
            this.resetForContainer(containerId);
        }

        this.updateDesiredRecipe(recipeId);

        this.ticksUntilRefresh =
                REFRESH_INTERVAL_TICKS;

        /*
         * Recipe selection commonly causes TaCZ to call init() again.
         * If observeRecipe() already sent the exact same request, do not
         * immediately supersede it with a duplicate request.
         */
        if (this.refreshInFlight
                && Objects.equals(
                this.pendingRecipeId,
                recipeId
        )) {
            return;
        }

        this.requestSourceRefresh(
                containerId,
                recipeId
        );
    }

    /**
     * Observe TaCZ's current selected recipe.
     *
     * Before screen init this only records the desired recipe. After screen
     * init, changing the recipe immediately invalidates the old aggregate and
     * requests a new one.
     */
    public void observeRecipe(
            int containerId,
            @Nullable ResourceLocation recipeId
    ) {
        boolean changed =
                this.updateDesiredRecipe(
                        recipeId
                );

        if (!changed) {
            return;
        }

        if (this.trackedContainerId
                != containerId) {
            /*
             * Constructor-time observation:
             * remember the recipe, but Screen.Init.Post owns the first send.
             */
            return;
        }

        if (recipeId == null) {
            return;
        }

        this.requestSourceRefresh(
                containerId,
                recipeId
        );
    }

    public List<ItemStack>
    getExternalDisplayStacks() {
        return this.externalDisplayStacks;
    }

    public boolean externalSourcesAuthorized() {
        return this.externalSourcesAuthorized;
    }

    public boolean displayTruncated() {
        return this.displayTruncated;
    }

    /**
     * Return aggregate counts only when they were computed for recipeId.
     *
     * A raw aggregate array must never be exposed without recipe identity.
     */
    @Nullable
    public int[] aggregateCountsFor(
            @Nullable ResourceLocation recipeId
    ) {
        if (!this.hasAcceptedSnapshot
                || recipeId == null
                || !Objects.equals(
                recipeId,
                this.acceptedAggregateRecipeId
        )) {
            return null;
        }

        return this.aggregateCounts.clone();
    }

    private boolean updateDesiredRecipe(
            @Nullable ResourceLocation recipeId
    ) {
        if (Objects.equals(
                this.desiredRecipeId,
                recipeId
        )) {
            return false;
        }

        this.desiredRecipeId =
                recipeId;

        this.invalidateRecipeAggregate();

        return true;
    }

    private void invalidateRecipeAggregate() {
        this.acceptedAggregateRecipeId = null;
        this.aggregateCounts = new int[0];
    }

    private void resetForContainer(
            int containerId
    ) {
        this.clearPendingRefresh();

        this.trackedContainerId =
                containerId;
        this.ticksUntilRefresh =
                REFRESH_INTERVAL_TICKS;

        this.latestAcceptedRefreshRequestId =
                -1L;
        this.latestAcceptedSourceRevision =
                -1L;

        this.hasAcceptedSnapshot =
                false;

        this.externalDisplayStacks =
                List.of();
        this.externalSourcesAuthorized =
                false;
        this.displayTruncated =
                false;

        this.aggregateCounts =
                new int[0];

        this.desiredRecipeId =
                null;
        this.acceptedAggregateRecipeId =
                null;
    }

    private void clearPendingRefresh() {
        this.pendingRefreshRequestId =
                -1L;
        this.pendingRecipeId =
                null;
        this.refreshInFlight =
                false;
        this.pendingRefreshAgeTicks =
                0;
    }

    private static List<ItemStack> immutableSnapshot(
            List<ItemStack> stacks
    ) {
        if (stacks == null || stacks.isEmpty()) {
            return List.of();
        }

        ArrayList<ItemStack> copies =
                new ArrayList<>(
                        stacks.size()
                );

        for (ItemStack stack : stacks) {
            if (stack != null
                    && !stack.isEmpty()) {
                copies.add(
                        stack.copy()
                );
            }
        }

        if (copies.isEmpty()) {
            return List.of();
        }

        return Collections.unmodifiableList(
                copies
        );
    }

    static boolean sameSnapshot(
            List<ItemStack> left,
            List<ItemStack> right
    ) {
        if (left == right) {
            return true;
        }

        if (left == null
                || right == null
                || left.size() != right.size()) {
            return false;
        }

        for (int index = 0;
             index < left.size();
             index++) {
            if (!ItemStack.matches(
                    left.get(index),
                    right.get(index)
            )) {
                return false;
            }
        }

        return true;
    }
}