package com.mafuyu404.taczaddon.compat.sophisticated;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.p3pp3rf1y.sophisticatedbackpacks.backpack.wrapper.BackpackLinkedStorageHostWrapper;
import net.p3pp3rf1y.sophisticatedbackpacks.backpack.wrapper.IBackpackWrapper;
import net.p3pp3rf1y.sophisticatedbackpacks.init.ModItems;
import net.p3pp3rf1y.sophisticatedcore.linkedstorage.ILinkedStorageContents;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LinkedInventoryBehaviorTest {
    @org.junit.jupiter.api.BeforeAll
    static void loadUpstreamDefaults() {
        net.neoforged.fml.config.ConfigTracker.INSTANCE.loadDefaultServerConfigs();
    }

    @org.junit.jupiter.api.AfterAll
    static void unloadUpstreamDefaults() {
        net.neoforged.fml.config.ConfigTracker.INSTANCE.unloadConfigs(
                net.neoforged.fml.config.ModConfig.Type.SERVER);
    }

    private static final UUID ID = new UUID(1, 2);

    private static IBackpackWrapper linked(UUID id) {
        CompoundTag contents = new CompoundTag();
        var source = (ILinkedStorageContents) Proxy.newProxyInstance(
                ILinkedStorageContents.class.getClassLoader(), new Class<?>[]{ILinkedStorageContents.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "groupId" -> id;
                    case "contents" -> contents;
                    default -> null;
                });
        return new BackpackLinkedStorageHostWrapper(source, new ItemStack(ModItems.BACKPACK.get()));
    }

    private static IBackpackWrapper ordinary(UUID id) {
        return (IBackpackWrapper) Proxy.newProxyInstance(IBackpackWrapper.class.getClassLoader(),
                new Class<?>[]{IBackpackWrapper.class}, (proxy, method, args) -> {
                    if (method.getName().equals("getContentsUuid")) return Optional.of(id);
                    throw new AssertionError("Unexpected ordinary wrapper access: " + method);
                });
    }

    @Test void sharedGroupCountsOnceButIndependentStorageRemainsVisible() {
        var visits = new SophisticatedBackpacksIntegrationImpl.InventoryVisits();
        int count = 0;
        for (var wrapper : List.of(linked(ID), linked(ID), ordinary(ID))) {
            if (visits.first(wrapper)) count += 64;
        }
        assertEquals(128, count, "one shared group plus one independent ordinary inventory");
        assertTrue(new SophisticatedBackpacksIntegrationImpl.InventoryVisits().first(linked(ID)),
                "deduplication must not survive into the next traversal");
    }

    @Test void linkedMutationNeverReadsOrdinaryStorageAndUsesGroupIdentity() {
        List<UUID> snapshots = new ArrayList<>();
        SophisticatedBackpacksIntegrationImpl.routeContentsSync(linked(ID), snapshots::add,
                id -> fail("Linked mutation must not read BackpackStorage or send BackpackContentsPayload"));
        assertEquals(List.of(ID), snapshots);
    }

    @Test void ordinaryMutationKeepsOrdinaryProtocol() {
        List<UUID> snapshots = new ArrayList<>();
        SophisticatedBackpacksIntegrationImpl.routeContentsSync(ordinary(ID),
                id -> fail("Ordinary storage must not be treated as a linked group"), snapshots::add);
        assertEquals(List.of(ID), snapshots);
    }
}
