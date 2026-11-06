package com.mafuyu404.taczaddon.compat;

import static org.junit.jupiter.api.Assertions.*;

import net.fxnt.fxntstorage.backpack.inventory.BackpackSlotLayout;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemContainerContents;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;

class CreateStorageContentsTest {
    private ItemStack backpack() {
        var layout = BackpackSlotLayout.createLayout();
        var items = new ArrayList<ItemStack>();
        for (int i = 0; i < layout.getTotalSlots(); i++) items.add(ItemStack.EMPTY);
        items.set(layout.items().getStartIndex(), new ItemStack(Items.ARROW, 40));
        items.set(layout.tools().getStartIndex(), new ItemStack(Items.DIAMOND_PICKAXE));
        var item =
                net.minecraft.core.registries.BuiltInRegistries.ITEM.get(
                        net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(
                                "fxntstorage", "backpack"));
        assertInstanceOf(net.fxnt.fxntstorage.backpack.BackpackItem.class, item);
        var stack = new ItemStack(item);
        stack.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(items));
        stack.set(DataComponents.CUSTOM_NAME, Component.literal("keep name"));
        return stack;
    }

    @Test
    void queriesReadCopiesAndExcludeToolSlots() {
        var stack = backpack();
        var before = stack.copy();
        CreateStorageBackpacksCompatInner.visitContents(
                null,
                stack,
                handler -> {
                    assertEquals(
                            BackpackSlotLayout.createLayout().items().getCount(),
                            handler.getSlots());
                    handler.extractItem(
                            0, 4,
                            false); // Even a misbehaving query visitor cannot mutate the live item.
                    return false;
                },
                false);
        assertTrue(ItemStack.matches(before, stack));
    }

    @Test
    void consumptionPersistsOnlyContentsAndSurvivesAnException() {
        var stack = backpack();
        assertThrows(
                IllegalStateException.class,
                () ->
                        CreateStorageBackpacksCompatInner.visitContents(
                                null,
                                stack,
                                handler -> {
                                    assertEquals(4, handler.extractItem(0, 4, false).getCount());
                                    throw new IllegalStateException("later slot failed");
                                },
                                true));
        var contents = stack.get(DataComponents.CONTAINER);
        assertEquals(
                36,
                contents.getStackInSlot(BackpackSlotLayout.createLayout().items().getStartIndex())
                        .getCount());
        assertTrue(
                contents.getStackInSlot(BackpackSlotLayout.createLayout().tools().getStartIndex())
                        .is(Items.DIAMOND_PICKAXE));
        assertEquals("keep name", stack.getHoverName().getString());
    }
}
