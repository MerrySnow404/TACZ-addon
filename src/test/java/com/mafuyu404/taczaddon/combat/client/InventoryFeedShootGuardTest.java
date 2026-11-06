package com.mafuyu404.taczaddon.combat.client;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import java.util.function.BooleanSupplier;

class InventoryFeedShootGuardTest {
    private static final BooleanSupplier UNUSED =
            () -> {
                throw new AssertionError("This ammo source must not be queried");
            };

    @Test
    void emptyInventoryAndUnconfirmedNetworkCannotStartShot() {
        assertTrue(InventoryFeedShootGuard.shouldBlock(false, 0, false, () -> false, () -> false));
    }

    @Test
    void physicalAmmoWorksWhileNetworkIsUnknown() {
        assertFalse(InventoryFeedShootGuard.shouldBlock(false, 0, false, () -> true, UNUSED));
    }

    @Test
    void confirmedNetworkAmmoCanFeedGun() {
        assertFalse(InventoryFeedShootGuard.shouldBlock(false, 0, false, () -> false, () -> true));
    }

    @Test
    void lastChamberedRoundDoesNotRequireReserveAmmo() {
        assertFalse(InventoryFeedShootGuard.shouldBlock(false, 0, true, UNUSED, UNUSED));
    }

    @Test
    void dummyAmmoDoesNotConsultPhysicalOrNetworkSources() {
        assertFalse(InventoryFeedShootGuard.shouldBlock(true, 1, false, UNUSED, UNUSED));
        assertTrue(InventoryFeedShootGuard.shouldBlock(true, 0, false, UNUSED, UNUSED));
    }
}
