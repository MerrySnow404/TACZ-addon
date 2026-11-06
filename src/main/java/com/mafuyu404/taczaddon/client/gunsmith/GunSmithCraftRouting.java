package com.mafuyu404.taczaddon.client.gunsmith;

import com.mafuyu404.taczaddon.gunsmith.CraftingTransaction;

import com.tacz.guns.crafting.GunSmithTableRecipe;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import javax.annotation.Nullable;

/**
 * Chooses the single crafting path for one button press.
 *
 * <p>The client decision is routing only: TaCZ's native callback re-validates
 * the recipe and the player inventory, and the extended request is validated
 * again on the server against the live menu, the session and the real sources.
 */
@OnlyIn(Dist.CLIENT)
public final class GunSmithCraftRouting {

    public enum Route {
        /** TaCZ's own craft button callback, exactly once. */
        NATIVE,
        /** The addon transaction, only when the server authorized extension. */
        EXTENDED
    }

    private GunSmithCraftRouting() {
    }

    /**
     * @param shiftDown            batch request
     * @param playerHasMaterials   the real player inventory satisfies the
     *                             recipe without any external source
     * @param extendedAuthorized   the server confirmed this menu supports the
     *                             extended path
     */
    public static Route decide(
            boolean shiftDown,
            boolean playerHasMaterials,
            boolean extendedAuthorized
    ) {
        if (!extendedAuthorized) {
            /*
             * No server-confirmed anchor: keep exactly one native operation.
             * A batch limit is never bypassed by simulating repeated sends.
             */
            return Route.NATIVE;
        }
        if (shiftDown) {
            return Route.EXTENDED;
        }
        return playerHasMaterials
                ? Route.NATIVE
                : Route.EXTENDED;
    }

    /**
     * True when the player's real inventory alone satisfies every input.
     *
     * <p>External display entries are deliberately excluded: this decides
     * whether TaCZ's default single craft can run natively.
     */
    public static boolean playerInventorySatisfies(
            @Nullable Player player,
            @Nullable GunSmithTableRecipe recipe
    ) {
        if (player == null || recipe == null) {
            return false;
        }

        /*
         * TaCZ native crafting already treats creative as material-exempt.
         * Routing a creative player into the addon transaction only because the
         * inventory is empty unnecessarily bypasses the native callback.
         */
        if (player.isCreative()) {
            return true;
        }

        return CraftingTransaction.canSatisfyStacks(
                recipe,
                player.getInventory().items
        );
    }
}
