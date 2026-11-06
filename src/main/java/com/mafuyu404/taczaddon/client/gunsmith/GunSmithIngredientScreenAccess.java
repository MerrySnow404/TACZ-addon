package com.mafuyu404.taczaddon.client.gunsmith;

/**
 * Client-side screen access surface for the GunSmith ingredient
 * interaction feature. The generic AbstractContainerScreen mouse release
 * mixin talks to this interface; the version-specific TaCZ mixin owns the
 * implementation, so no TaCZ internals leak into the generic layer.
 */
public interface GunSmithIngredientScreenAccess {
    /**
     * @return true if the release was consumed by opening JEI recipes
     *         for the ingredient under the cursor.
     */
    boolean taczaddon$handleIngredientMouseRelease(
            double mouseX,
            double mouseY,
            int button
    );
}
