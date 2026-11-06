package com.mafuyu404.taczaddon.compat;

import static org.junit.jupiter.api.Assertions.*;

import net.fxnt.fxntstorage.storage_network.StorageNetwork;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import org.junit.jupiter.api.Test;

import java.util.List;

/** Checks private mapping surfaces and actual transformed TaCZ hooks in the selected binaries. */
class EcosystemContractsTest {
    @Test
    void createPhysicalSlotsIncludeBoxAndTierAndAreReflectivelyAccessible() throws Exception {
        var slots = StorageNetwork.class.getDeclaredField("slotMappings");
        assertEquals(List.class, slots.getType());
        assertTrue(slots.trySetAccessible());
        var mapping =
                Class.forName("net.fxnt.fxntstorage.storage_network.StorageNetwork$SlotMapping");
        var constructor = mapping.getDeclaredConstructor(int.class, int.class);
        assertTrue(constructor.trySetAccessible());
        var entry = constructor.newInstance(3, 2);
        for (var expected : java.util.Map.of("boxIndex", 3, "tierSlot", 2).entrySet()) {
            var method = mapping.getDeclaredMethod(expected.getKey());
            assertTrue(method.trySetAccessible());
            assertEquals(expected.getValue(), method.invoke(entry));
        }
        var filtered =
                Class.forName(
                        "net.fxnt.fxntstorage.controller.StorageInterfaceFilteredEntity$FilteredItemHandler");
        assertTrue(filtered.getDeclaredField("source").trySetAccessible());
        assertTrue(filtered.getDeclaredField("filteredSlots").trySetAccessible());
    }

    @Test
    void beyondNetworkApiAndBothScriptMixinsArePresent() throws Exception {
        var extractor =
                Class.forName(
                        "com.solr98.beyondintegration.handler.TaczAmmoExtractor",
                        false,
                        getClass().getClassLoader());
        assertEquals(
                int.class,
                extractor
                        .getMethod(
                                "tryConsumeFromAll", ServerPlayer.class, ItemStack.class, int.class)
                        .getReturnType());
        var script = Class.forName("com.tacz.guns.item.ModernKineticGunScriptAPI");
        var names =
                java.util.Arrays.stream(script.getDeclaredMethods())
                        .map(java.lang.reflect.Method::getName)
                        .toList();
        assertTrue(names.stream().anyMatch(name -> name.contains("taczaddon$consumeBackpackAmmo")));
        assertTrue(
                names.stream().anyMatch(name -> name.contains("beyond$onConsumeAmmoFromPlayer")));
    }
}
