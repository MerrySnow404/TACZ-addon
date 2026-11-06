package com.mafuyu404.taczaddon.combat;

import static org.junit.jupiter.api.Assertions.*;

import com.tacz.guns.resource.pojo.data.gun.Bolt;

import org.junit.jupiter.api.Test;

class ShootWhileReloadServiceTest {
    @Test
    void onlyLoadedOrChamberedAmmunitionQualifies() {
        assertTrue(ShootWhileReloadService.hasLoadedAmmo(1, false, Bolt.OPEN_BOLT));
        assertTrue(ShootWhileReloadService.hasLoadedAmmo(0, true, Bolt.CLOSED_BOLT));
        assertFalse(ShootWhileReloadService.hasLoadedAmmo(0, true, Bolt.OPEN_BOLT));
        assertFalse(ShootWhileReloadService.hasLoadedAmmo(0, false, Bolt.CLOSED_BOLT));
        // Reserve ammunition has no input to this decision and cannot turn an empty gun into a
        // candidate.
        assertFalse(ShootWhileReloadService.hasLoadedAmmo(0, false, Bolt.MANUAL_ACTION));
        assertTrue(
                ShootWhileReloadService.hasLoadedAmmo(Integer.MAX_VALUE, true, Bolt.CLOSED_BOLT));
    }
}
