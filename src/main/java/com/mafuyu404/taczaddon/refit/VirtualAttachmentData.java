package com.mafuyu404.taczaddon.refit;

import com.mafuyu404.taczaddon.inventory.ItemStackData;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;

/**
 * Provenance marker for "virtual" attachments.
 *
 * <p>A virtual attachment is an entitlement granted by the liberateAttachment gamerule (or by an
 * unlocked GunSmithing result), not a real item the player owns. The marker lives inside the
 * attachment ItemStack's own {@code DataComponents.CUSTOM_DATA}, so TaCZ's {@code
 * IGun.installAttachment(...)} serializes it together with the whole stack, and {@code
 * IGun.getAttachment(...)} restores it afterwards.
 *
 * <p>Marker structure:
 *
 * <pre>
 * CUSTOM_DATA: {
 *     taczaddon: {
 *         virtual_attachment: true
 *     }
 * }
 * </pre>
 *
 * <p>Only the server writes this marker while constructing a virtual attachment. The marker is
 * "installation-time provenance", not a live permission flag: changing the gamerule must never
 * rewrite markers already stored inside a gun.
 *
 * <p>Stacks without an explicit {@code true} marker are treated as physical (migration policy: no
 * marker == PHYSICAL).
 */
public final class VirtualAttachmentData {

    /** Root key inside the attachment's own CUSTOM_DATA. */
    public static final String ADDON_ROOT_KEY = "taczaddon";

    /** Boolean provenance flag. */
    public static final String VIRTUAL_ATTACHMENT_KEY = "virtual_attachment";

    /** NBT id of a compound tag ({@code 10}). */
    private static final int TAG_COMPOUND = 10;

    private VirtualAttachmentData() {}

    /**
     * Returns whether the stack carries an explicit virtual provenance marker. A missing marker, an
     * empty stack, or a non-true marker all resolve to {@code false} (physical).
     */
    public static boolean isVirtual(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }

        CompoundTag customData = ItemStackData.getCustomDataCopy(stack);

        // Official 1.2.0 OEM attachments use this marker. Preserve it on old runtimes too.
        if (customData.contains("VirtualOemAttachment", CompoundTag.TAG_BYTE)
                && customData.getBoolean("VirtualOemAttachment")) {
            return true;
        }

        if (!customData.contains(ADDON_ROOT_KEY, TAG_COMPOUND)) {
            return false;
        }

        return customData.getCompound(ADDON_ROOT_KEY).getBoolean(VIRTUAL_ATTACHMENT_KEY);
    }

    /**
     * Returns a marked copy of the given stack. The input stack is never mutated, which makes this
     * safe for stacks that the caller does not own.
     */
    public static ItemStack markVirtual(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return stack;
        }

        ItemStack copy = stack.copy();
        markVirtualInPlace(copy);
        return copy;
    }

    /**
     * Marks the stack in place. Only use this when the caller owns the stack (for example a
     * defensive copy created server-side).
     */
    public static void markVirtualInPlace(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return;
        }

        ItemStackData.updateCustomData(
                stack,
                tag -> {
                    CompoundTag addon;

                    if (tag.contains(ADDON_ROOT_KEY, TAG_COMPOUND)) {
                        addon = tag.getCompound(ADDON_ROOT_KEY);
                    } else {
                        addon = new CompoundTag();
                    }

                    addon.putBoolean(VIRTUAL_ATTACHMENT_KEY, true);
                    tag.put(ADDON_ROOT_KEY, addon);
                });
    }
}
