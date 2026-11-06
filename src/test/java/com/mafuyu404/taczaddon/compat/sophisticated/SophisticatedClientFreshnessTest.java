package com.mafuyu404.taczaddon.compat.sophisticated;

import com.mafuyu404.taczaddon.testutil.MinecraftTestBootstrap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SophisticatedClientFreshnessTest {
    private static final Path PROJECT_ROOT =
            Path.of("").toAbsolutePath().normalize();

    @BeforeAll
    static void bootstrap() throws Exception {
        MinecraftTestBootstrap.prepare();
    }

    @Test
    void equalSynchronizedAndCachedInventoryDoesNotRefresh() {
        CompoundTag synced = inventoryWithAmmoCount(120);
        CompoundTag cached = synced.copy();

        assertFalse(
                SophisticatedBackpacksCompatInner
                        .needsClientInventoryRefresh(
                                synced,
                                cached
                        )
        );
    }

    @Test
    void ammoBoxNbtDifferenceRequiresRefresh() {
        CompoundTag synced = inventoryWithAmmoCount(90);
        CompoundTag cached = inventoryWithAmmoCount(120);

        assertTrue(
                SophisticatedBackpacksCompatInner
                        .needsClientInventoryRefresh(
                                synced,
                                cached
                        )
        );
    }

    @Test
    void ordinaryAmmoCountDifferenceRequiresRefresh() {
        CompoundTag synced = inventoryWithAmmoStackCount(64);
        CompoundTag cached = inventoryWithAmmoStackCount(32);

        assertTrue(
                SophisticatedBackpacksCompatInner
                        .needsClientInventoryRefresh(
                                synced,
                                cached
                        )
        );
    }

    @Test
    void sameInventoryItemsInDifferentSlotOrderDoesNotRefresh() {
        CompoundTag synced = inventoryWithSlots(2, 0);
        CompoundTag cached = inventoryWithSlots(0, 2);

        assertFalse(
                SophisticatedBackpacksCompatInner
                        .needsClientInventoryRefresh(
                                synced,
                                cached
                        )
        );
    }

    @Test
    void missingSynchronizedInventoryTagDoesNotForceRefresh() {
        assertFalse(
                SophisticatedBackpacksCompatInner
                        .hasSynchronizedInventoryTag(
                                new CompoundTag()
                        )
        );
    }

    @Test
    void presentSynchronizedInventoryTagIsDetected() {
        CompoundTag contents = new CompoundTag();
        contents.put(
                "inventory",
                inventoryWithAmmoCount(90)
        );

        assertTrue(
                SophisticatedBackpacksCompatInner
                        .hasSynchronizedInventoryTag(
                                contents
                        )
        );
    }

    @Test
    void initialClientSyncTickNoLongerRebuildsImmediately()
            throws IOException {
        String clientEvent = read(
                "src/main/java/com/mafuyu404/taczaddon/client/ammo/BackpackHudState.java"
        ).replaceAll("\\s+", "");
        int start = clientEvent.indexOf(
                "if(!taczaddon$hudInitialSyncRequested)"
        );
        int end = clientEvent.indexOf(
                "if(--taczaddon$hudTicksUntilRefresh>0)"
        );
        String initialBlock = clientEvent.substring(start, end);

        assertTrue(initialBlock.contains(
                "SophisticatedBackpacksCompat.syncAllBackpack(player)"
        ));
        assertFalse(initialBlock.contains(
                "rebuildBackpackHudInventory(player)"
        ));
        assertTrue(initialBlock.contains(
                "_virtualInventory=null"
        ));
    }

    @Test
    void mutationPathDoesNotUseClientFreshnessInvalidation()
            throws IOException {
        String inner = read(
                "src/main/java/com/mafuyu404/taczaddon/compat/sophisticated/SophisticatedBackpacksCompatInner.java"
        );
        int visitStart = inner.indexOf(
                "public static boolean visitInventoryBackpacks"
        );
        int mutateStart = inner.indexOf(
                "public static boolean mutateInventoryBackpacks"
        );
        int syncStart = inner.indexOf(
                "public static void syncAllBackpack"
        );

        String visitBlock = inner.substring(
                visitStart,
                mutateStart
        );
        String mutateBlock = inner.substring(
                mutateStart,
                syncStart
        );

        assertTrue(visitBlock.contains(
                "getFreshInventoryHandler"
        ));
        assertTrue(visitBlock.contains(
                "onContentsNbtUpdated"
        ));
        assertFalse(mutateBlock.contains(
                "getFreshInventoryHandler"
        ));
        assertFalse(mutateBlock.contains(
                "onContentsNbtUpdated"
        ));
    }

    private static CompoundTag inventoryWithAmmoCount(int count) {
        return inventoryWithItem(
                0,
                "tacz:ammo_box",
                item -> item.getCompound("tag")
                        .putInt("AmmoCount", count)
        );
    }

    private static CompoundTag inventoryWithAmmoStackCount(int count) {
        return inventoryWithItem(
                0,
                "tacz:ammo",
                item -> {
                    item.putInt("Count", count);
                    item.putInt("realCount", count);
                }
        );
    }

    private static CompoundTag inventoryWithSlots(int... slots) {
        CompoundTag inventory = new CompoundTag();
        inventory.putInt("Size", 2);
        ListTag items = new ListTag();
        for (int slot : slots) {
            CompoundTag item = new CompoundTag();
            item.putInt("Slot", slot);
            item.putString("id", "tacz:ammo");
            item.putInt("Count", 1);
            item.putInt("realCount", 1);
            items.add(item);
        }
        inventory.put("Items", items);
        return inventory;
    }

    private static CompoundTag inventoryWithItem(
            int slot,
            String itemId,
            java.util.function.Consumer<CompoundTag> itemMutator
    ) {
        CompoundTag inventory = new CompoundTag();
        inventory.putInt("Size", 1);
        CompoundTag item = new CompoundTag();
        item.putInt("Slot", slot);
        item.putString("id", itemId);
        item.putInt("Count", 1);
        item.putInt("realCount", 1);
        item.put("tag", new CompoundTag());
        itemMutator.accept(item);
        ListTag items = new ListTag();
        items.add(item);
        inventory.put("Items", items);
        return inventory;
    }

    private static String read(String relativePath)
            throws IOException {
        return Files.readString(
                PROJECT_ROOT.resolve(relativePath),
                StandardCharsets.UTF_8
        );
    }
}
