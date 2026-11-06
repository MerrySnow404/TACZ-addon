package com.mafuyu404.taczaddon.refit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mafuyu404.taczaddon.inventory.ItemStackData;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;

import org.junit.jupiter.api.Test;

/**
 * Provenance marker tests. The game is bootstrapped by the NeoForge JUnit harness, so real
 * ItemStacks are used without mocks.
 */
class VirtualAttachmentDataTest {

    @Test
    void officialOemMarkerIsVirtualWithoutAddonMarker() {
        ItemStack stack = new ItemStack(Items.STONE);
        ItemStackData.updateCustomData(stack, tag -> tag.putBoolean("VirtualOemAttachment", true));
        assertTrue(VirtualAttachmentData.isVirtual(stack));
        assertTrue(VirtualAttachmentData.isVirtual(stack.copy()));
        ItemStackData.updateCustomData(stack, tag -> tag.putBoolean("VirtualOemAttachment", false));
        assertFalse(VirtualAttachmentData.isVirtual(stack));
    }

    @Test
    void emptyStackIsNotVirtual() {
        assertFalse(VirtualAttachmentData.isVirtual(ItemStack.EMPTY));
        assertFalse(VirtualAttachmentData.isVirtual(null));
    }

    @Test
    void unmarkedStackIsPhysical() {
        ItemStack stack = new ItemStack(Items.STONE);

        assertFalse(VirtualAttachmentData.isVirtual(stack));
    }

    @Test
    void markVirtualReturnsMarkedCopyAndPreservesOriginal() {
        ItemStack original = new ItemStack(Items.STONE);

        ItemStack marked = VirtualAttachmentData.markVirtual(original);

        assertTrue(VirtualAttachmentData.isVirtual(marked));
        assertFalse(VirtualAttachmentData.isVirtual(original));
    }

    @Test
    void markVirtualPreservesUnrelatedCustomData() {
        ItemStack original = new ItemStack(Items.STONE);

        ItemStackData.updateCustomData(
                original, tag -> tag.putString("some_other_mod_key", "value"));

        ItemStack marked = VirtualAttachmentData.markVirtual(original);

        assertTrue(VirtualAttachmentData.isVirtual(marked));

        CompoundTag markedData =
                marked.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();

        assertEquals("value", markedData.getString("some_other_mod_key"));
    }

    @Test
    void markVirtualPreservesExistingTaczaddonCompound() {
        ItemStack original = new ItemStack(Items.STONE);

        ItemStackData.updateCustomData(
                original,
                tag -> {
                    CompoundTag addon = new CompoundTag();
                    addon.putString("existing_field", "kept");
                    tag.put(VirtualAttachmentData.ADDON_ROOT_KEY, addon);
                });

        ItemStack marked = VirtualAttachmentData.markVirtual(original);

        assertTrue(VirtualAttachmentData.isVirtual(marked));

        CompoundTag markedData =
                marked.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();

        CompoundTag addon = markedData.getCompound(VirtualAttachmentData.ADDON_ROOT_KEY);

        assertEquals("kept", addon.getString("existing_field"));
        assertTrue(addon.getBoolean(VirtualAttachmentData.VIRTUAL_ATTACHMENT_KEY));
    }

    @Test
    void falseMarkerIsNotVirtual() {
        ItemStack stack = new ItemStack(Items.STONE);

        ItemStackData.updateCustomData(
                stack,
                tag -> {
                    CompoundTag addon = new CompoundTag();
                    addon.putBoolean(VirtualAttachmentData.VIRTUAL_ATTACHMENT_KEY, false);
                    tag.put(VirtualAttachmentData.ADDON_ROOT_KEY, addon);
                });

        assertFalse(VirtualAttachmentData.isVirtual(stack));
    }
}
