package com.mafuyu404.taczaddon.compat.sophisticated;

/**
 * Independently degradable pieces of the Sophisticated Backpacks + Core integration.
 *
 * <p>A compatibility failure inside one capability must only disable that capability. For example a
 * broken carried-backpack contract must not take block-backpack support or the client payload cache
 * refresh down with it.
 */
public enum SophisticatedCapability {
    CARRIED_BACKPACK,
    BLOCK_BACKPACK,
    CLIENT_SYNC
}
