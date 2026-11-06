package com.mafuyu404.taczaddon.compat.sophisticated;

/** Lifecycle state of a single {@link SophisticatedCapability}. */
public enum SophisticatedCapabilityState {
    /** The capability has not been probed yet. */
    UNINITIALIZED,

    /** Sophisticated Backpacks itself is not installed. */
    ABSENT,

    /** The required binary contract for the capability is usable. */
    READY,

    /**
     * The dependency group is installed, but the capability failed its compatibility probe or later
     * hit a binary linkage incompatibility. The capability is never retried and keeps returning its
     * neutral fallback for the rest of the session.
     */
    BROKEN
}
