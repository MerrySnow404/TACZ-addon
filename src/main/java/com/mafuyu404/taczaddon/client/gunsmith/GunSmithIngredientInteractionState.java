package com.mafuyu404.taczaddon.client.gunsmith;

import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Pure frame-local hit-target management for the GunSmith ingredient
 * slots. Targets live only for the current render frame: they are cleared
 * at the start of every frame and repopulated from the ItemStacks TaCZ
 * actually draws, so a release event can never hit a stale recipe page.
 */
public final class GunSmithIngredientInteractionState {
    public record HitTarget(
            ItemStack stack,
            int x,
            int y,
            int width,
            int height
    ) {
        public boolean contains(
                double mouseX,
                double mouseY
        ) {
            return mouseX >= x
                    && mouseX < x + width
                    && mouseY >= y
                    && mouseY < y + height;
        }
    }

    private final List<HitTarget> targets = new ArrayList<>();

    public void beginFrame() {
        targets.clear();
    }

    public void register(
            ItemStack stack,
            int x,
            int y
    ) {
        if (stack == null || stack.isEmpty()) {
            return;
        }

        targets.add(new HitTarget(
                stack.copy(),
                x,
                y,
                16,
                16
        ));
    }

    public Optional<ItemStack> find(
            double mouseX,
            double mouseY
    ) {
        for (HitTarget target : targets) {
            if (target.contains(mouseX, mouseY)) {
                return Optional.of(target.stack());
            }
        }
        return Optional.empty();
    }
}
