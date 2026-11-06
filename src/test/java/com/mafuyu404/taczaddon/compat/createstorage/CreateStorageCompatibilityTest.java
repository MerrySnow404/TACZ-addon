package com.mafuyu404.taczaddon.compat.createstorage;

import com.mafuyu404.taczaddon.compat.api.ApiShapeProbe;

import com.mafuyu404.taczaddon.testutil.CompatibilityFixtures;
import com.mafuyu404.taczaddon.testutil.MinecraftTestBootstrap;
import net.fxnt.fxntstorage.backpack.inventory.BackpackContainer;
import net.fxnt.fxntstorage.backpack.inventory.BackpackSlotLayout;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.*;

import java.io.UncheckedIOException;
import java.util.List;
import java.util.jar.JarFile;

import static org.junit.jupiter.api.Assertions.*;

class CreateStorageCompatibilityTest {
    @BeforeAll
    static void bootstrap() throws Exception { MinecraftTestBootstrap.prepare(); }

    @Test
    void onlySelectedCreateStorageReleaseIsSupported() {
        assertTrue(CreateStorageCompat.supportsVersion("1.2.7"));
        for (String version : List.of("1.2.6", "1.2.1", "1.2.8", "1.3.4", "1.2.7-beta")) {
            assertFalse(CreateStorageCompat.supportsVersion(version));
        }
        assertFalse(CreateStorageCompat.supportsVersion(null));
    }

    @Test
    void publishedJarSatisfiesBridgeAndEveryDirectUpstreamReference() throws Exception {
        var path = CompatibilityFixtures.jar("createstorage", "1.2.7", "storage");
        CompatibilityFixtures.requireVersion(path, "1.2.7", "Create Storage");
        try (var jar = new JarFile(path.toFile())) {
            ApiShapeProbe.ClassBytes source = name -> {
                String resource = name.replace('.', '/') + ".class";
                var entry = jar.getJarEntry(resource);
                try (var input = entry != null ? jar.getInputStream(entry)
                        : name.startsWith("net.fxnt.") ? null
                        : getClass().getClassLoader().getResourceAsStream(resource)) {
                    return input == null ? null : input.readAllBytes();
                } catch (java.io.IOException error) { throw new UncheckedIOException(error); }
            };
            assertTrue(CreateStorageCompat.matches(source));
            assertFalse(CreateStorageCompat.matches(name -> null));
            assertFalse(CreateStorageCompat.matches(name -> name.endsWith("$FilteredItemHandler") ? null : source.read(name)));
            for (String name : List.of("CreateStorageBackpacksCompatInner", "CreateStorageBlocksCompatInner")) {
                try (var input = getClass().getResourceAsStream(name + ".class")) {
                    assertNotNull(input);
                    var node = new ClassNode();
                    new ClassReader(input).accept(node, 0);
                    for (var method : node.methods) for (var instruction : method.instructions) {
                        if (instruction instanceof MethodInsnNode call && call.owner.startsWith("net/fxnt/")) {
                            assertTrue(ApiShapeProbe.satisfies(source, call.owner.replace('/', '.'),
                                    List.of(ApiShapeProbe.method(call.name, call.desc))), call.owner + "." + call.name + call.desc);
                        }
                        if (instruction instanceof FieldInsnNode field && field.owner.startsWith("net/fxnt/")) {
                            assertTrue(ApiShapeProbe.satisfies(source, field.owner.replace('/', '.'),
                                    List.of(ApiShapeProbe.field(field.name, field.desc))), field.owner + "." + field.name);
                        }
                    }
                }
            }
        }
    }

    @Test
    void queryIsDetachedAndDoesNotCountGhostFilterSlots() {
        ItemStack backpack = contents();
        var before = backpack.getTag().copy();
        CreateStorageBackpacksCompatInner.visitContents(null, backpack, handler -> {
            assertEquals(BackpackSlotLayout.createLayout().items().getCount(), handler.getSlots());
            int total = 0;
            for (int i = 0; i < handler.getSlots(); i++) total += handler.getStackInSlot(i).getCount();
            assertEquals(120, total);
            handler.extractItem(0, 5, false);
            return true;
        }, false);
        assertEquals(before, backpack.getTag());
    }

    @Test
    void extractionAndAmmoBoxTagsPersistWithoutLosingOtherSettings() {
        ItemStack backpack = contents();
        CreateStorageBackpacksCompatInner.visitContents(null, backpack, handler -> {
            assertEquals(7, handler.extractItem(0, 7, false).getCount());
            handler.getStackInSlot(0).getOrCreateTag().putInt("AmmoCount", 9);
            return true;
        }, true);
        var reloaded = new BackpackContainer(null, backpack).getItemHandler();
        assertEquals(113, reloaded.getStackInSlot(0).getCount());
        assertEquals(9, reloaded.getStackInSlot(0).getTag().getInt("AmmoCount"));
        assertEquals("keep", backpack.getTagElement("BlockEntityTag").getString("OtherSetting"));
        assertEquals(64, reloaded.getStackInSlot(BackpackSlotLayout.createLayout().magnetFilter().getStartIndex()).getCount());
    }

    @Test
    void partialMutationIsSavedBeforeFailurePropagates() {
        ItemStack backpack = contents();
        assertThrows(IllegalStateException.class, () -> CreateStorageBackpacksCompatInner.visitContents(null, backpack, handler -> {
            handler.extractItem(0, 4, false);
            throw new IllegalStateException("later slot failed");
        }, true));
        assertEquals(116, new BackpackContainer(null, backpack).getItemHandler().getStackInSlot(0).getCount());
    }

    private static ItemStack contents() {
        // The storage serializer accepts any carrier; this avoids registering upstream blocks in a unit JVM.
        ItemStack backpack = new ItemStack(Items.CHEST);
        var container = new BackpackContainer(null, backpack);
        container.getItemHandler().setStackInSlot(0, new ItemStack(Items.IRON_NUGGET, 120));
        container.getItemHandler().setStackInSlot(BackpackSlotLayout.createLayout().magnetFilter().getStartIndex(),
                new ItemStack(Items.IRON_NUGGET, 64));
        backpack.addTagElement("BlockEntityTag", container.saveItemsToStack());
        backpack.getTagElement("BlockEntityTag").putString("OtherSetting", "keep");
        return backpack;
    }
}
