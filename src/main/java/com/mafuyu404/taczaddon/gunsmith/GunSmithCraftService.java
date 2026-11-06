package com.mafuyu404.taczaddon.gunsmith;

import com.mafuyu404.taczaddon.config.CommonConfig;
import com.mafuyu404.taczaddon.gunsmith.crafting.CraftingTransaction;
import com.mafuyu404.taczaddon.gunsmith.crafting.CraftingTransaction.CraftFailure;
import com.tacz.guns.inventory.GunSmithTableMenu;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/** Server-authoritative crafting workflow, independent of payload encoding. */
public final class GunSmithCraftService {
    private GunSmithCraftService() {}

    public record Request(
            int containerId, long requestId, ResourceLocation recipeId, int requestedCount) {}

    public record Result(int craftedExecutions, ItemStack outputPerCraft, CraftFailure failure) {
        public boolean success() {
            return craftedExecutions > 0;
        }
    }

    public static Result craft(ServerPlayer player, Request request) {
        int craftedExecutions = 0;
        ItemStack outputPerCraft = ItemStack.EMPTY;
        CraftFailure failure = null;
        var session = GunSmithCraftingSessionManager.getSession(player.getUUID());
        if (!(player.containerMenu instanceof GunSmithTableMenu menu)
                || menu.containerId != request.containerId) {
            failure = CraftFailure.INVALID_MENU;
        } else if (session == null || !session.validate(player, request.containerId)) {
            failure = CraftFailure.INVALID_SESSION;
        } else if (!session.acceptCraftRequestId(request.requestId)) {
            failure = CraftFailure.DUPLICATE_REQUEST;
        } else {
            try {
                var recipe =
                        ((GunSmithRecipeAccess) menu)
                                .taczaddon$invokeGetRecipe(
                                        request.recipeId, player.level().getRecipeManager());
                if (recipe == null) {
                    failure = CraftFailure.INVALID_RECIPE;
                } else {
                    int requestedCount =
                            Math.max(
                                    1,
                                    Math.min(
                                            request.requestedCount,
                                            CommonConfig.getBatchCraftMax()));
                    Result batch =
                            craftBatch(
                                    requestedCount,
                                    () -> session.validate(player, request.containerId),
                                    () ->
                                            CraftingTransaction.execute(
                                                    player,
                                                    session,
                                                    recipe,
                                                    GunSmithCraftingSources.resolve(
                                                            player, session)));
                    craftedExecutions = batch.craftedExecutions();
                    outputPerCraft = batch.outputPerCraft();
                    failure = batch.failure();
                }
            } catch (RuntimeException exception) {
                com.mojang.logging.LogUtils.getLogger()
                        .error("Gunsmith request failed for {}", player.getUUID(), exception);
                failure = CraftFailure.TRANSACTION_FAILED;
            }
        }
        if (craftedExecutions > 0) session.markSourcesChanged();
        return new Result(craftedExecutions, outputPerCraft, failure);
    }

    static Result craftBatch(
            int requestedCount,
            java.util.function.BooleanSupplier sessionValid,
            java.util.function.Supplier<CraftingTransaction.CraftResult> execute) {
        int completed = 0;
        ItemStack output = ItemStack.EMPTY;
        CraftFailure failure = null;
        try {
            for (int i = 0; i < requestedCount; i++) {
                if (!sessionValid.getAsBoolean()) {
                    failure = CraftFailure.INVALID_SESSION;
                    break;
                }
                var result = execute.get();
                if (!result.success()) {
                    failure = result.failureReason();
                    break;
                }
                completed++;
                if (output.isEmpty()) output = result.output().copy();
            }
        } catch (RuntimeException exception) {
            com.mojang.logging.LogUtils.getLogger()
                    .error("Gunsmith batch failed after {} executions", completed, exception);
            failure = CraftFailure.TRANSACTION_FAILED;
        }
        return new Result(completed, output, failure);
    }
}
