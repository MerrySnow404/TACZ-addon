package com.mafuyu404.taczaddon.compat.api;

/**
 * Shared runtime ABI status for optional integrations whose supported shape is
 * a single verified generation.
 *
 * <p>Deliberately separate from "the mod is loaded":
 *
 * <ul>
 *     <li>{@link #ABSENT} - the optional API classes are not visible;</li>
 *     <li>{@link #SUPPORTED} - the exact classes and member descriptors the
 *     backend links against were verified;</li>
 *     <li>{@link #UNKNOWN} - something is installed, but its shape does not
 *     match any generation this addon knows how to call.</li>
 * </ul>
 */
public enum OptionalAbiStatus {
    ABSENT,
    SUPPORTED,
    UNKNOWN;

    /**
     * Resolves the status from a verified structural contract.
     *
     * @param visible   whether the integration's root class is visible at all
     * @param satisfied whether the verified contract holds
     */
    public static OptionalAbiStatus of(
            boolean visible,
            boolean satisfied
    ) {
        if (!visible) {
            return ABSENT;
        }
        return satisfied ? SUPPORTED : UNKNOWN;
    }

    public boolean supported() {
        return this == SUPPORTED;
    }
}
