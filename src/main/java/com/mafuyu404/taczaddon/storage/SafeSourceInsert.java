package com.mafuyu404.taczaddon.storage;

import com.mafuyu404.taczaddon.storage.api.CraftingItemSource;

import net.minecraft.world.item.ItemStack;

import java.util.Objects;
import javax.annotation.Nullable;

/**
 * Fail-closed wrapper around a real CraftingItemSource insertion.
 *
 * <p>If an external handler throws while inserting, the caller cannot know
 * whether zero, some, or all items were already committed. In that case the
 * original stack must never be retried elsewhere.
 */
public final class SafeSourceInsert {

    public record Result(
            boolean known,
            ItemStack remainder,
            @Nullable Throwable failure
    ) {
        public Result {
            remainder = remainder == null
                    ? ItemStack.EMPTY
                    : remainder.copy();
        }

        public static Result known(ItemStack remainder) {
            return new Result(
                    true,
                    remainder,
                    null
            );
        }

        public static Result unknown(
                @Nullable Throwable failure
        ) {
            return new Result(
                    false,
                    ItemStack.EMPTY,
                    failure
            );
        }
    }

    private SafeSourceInsert() {
    }

    public static Result commit(
            CraftingItemSource source,
            int slot,
            ItemStack stack
    ) {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(stack, "stack");

        if (stack.isEmpty()) {
            return Result.known(ItemStack.EMPTY);
        }

        /*
         * Keep one immutable-by-convention snapshot and hand a different copy
         * to the handler. A broken handler is therefore unable to mutate the
         * object used for post-call validation.
         */
        ItemStack offered = stack.copy();

        try {
            ItemStack remainder = source.insertItem(
                    slot,
                    offered.copy(),
                    false
            );

            if (!isValidRemainder(offered, remainder)) {
                /*
                 * The call returned, but the returned value is not a valid
                 * description of how much remained. We cannot safely retry.
                 */
                return Result.unknown(null);
            }

            return Result.known(remainder);
        } catch (RuntimeException | LinkageError failure) {
            /*
             * The handler may have committed a partial insertion before
             * throwing. Never assume that the whole offered stack remains.
             */
            return Result.unknown(failure);
        }
    }

    static boolean isValidRemainder(
            ItemStack offered,
            @Nullable ItemStack remainder
    ) {
        if (remainder == null) {
            return false;
        }

        if (remainder.isEmpty()) {
            return true;
        }

        return remainder.getCount() >= 0
                && remainder.getCount() <= offered.getCount()
                && ItemStack.isSameItemSameTags(
                        offered,
                        remainder
                );
    }
}
