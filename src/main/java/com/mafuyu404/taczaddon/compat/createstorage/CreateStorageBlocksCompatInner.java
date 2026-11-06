package com.mafuyu404.taczaddon.compat.createstorage;

import com.mafuyu404.taczaddon.storage.api.BlockAccess;
import com.mafuyu404.taczaddon.storage.api.PhysicalSlot;

import net.fxnt.fxntstorage.backpack.BackpackEntity;
import net.fxnt.fxntstorage.backpack.inventory.BackpackSlotLayout;
import net.fxnt.fxntstorage.controller.StorageControllerEntity;
import net.fxnt.fxntstorage.controller.StorageInterfaceEntity;
import net.fxnt.fxntstorage.storage_network.StorageNetwork;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.items.IItemHandler;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

final class CreateStorageBlocksCompatInner {
    private CreateStorageBlocksCompatInner() {}

    static BlockAccess bind(BlockEntity block, IItemHandler handler) {
        StorageControllerEntity controller = controller(block);
        if (controller == null) {
            if (block instanceof StorageInterfaceEntity) throw new IllegalStateException("Disconnected storage interface");
            var slots = new ArrayList<PhysicalSlot>();
            var layout = block instanceof BackpackEntity ? BackpackSlotLayout.createLayout() : null;
            for (int slot = 0; slot < handler.getSlots(); slot++) {
                boolean real = layout == null || layout.items().contains(slot) || layout.tools().contains(slot);
                slots.add(new PhysicalSlot(block, real ? slot : -1));
            }
            return new BlockAccess(slots, () -> true);
        }
        StorageNetwork network = controller.getConnectedNetwork();
        if (network == null) throw new IllegalStateException("Storage network is not ready");
        var boxes = List.copyOf(network.getBoxes());
        List<Integer> indices = indices(handler, network);
        var slots = new ArrayList<PhysicalSlot>();
        for (int index : indices) {
            slots.add(new PhysicalSlot(boxes.get(index).simpleStorageBoxEntity, 0));
        }
        return new BlockAccess(slots, () -> {
            if (controller(block) != controller || !loaded(controller)
                    || controller.getConnectedNetwork() != network) return false;
            var current = network.getBoxes();
            if (current.size() != boxes.size()) return false;
            for (int i = 0; i < boxes.size(); i++) {
                var box = boxes.get(i).simpleStorageBoxEntity;
                if (current.get(i).simpleStorageBoxEntity != box || !loaded(box)) return false;
            }
            return true;
        });
    }

    private static boolean loaded(BlockEntity block) {
        var level = block.getLevel();
        return level != null && !block.isRemoved() && level.isLoaded(block.getBlockPos())
                && level.getBlockEntity(block.getBlockPos()) == block;
    }

    private static StorageControllerEntity controller(BlockEntity block) {
        if (block instanceof StorageControllerEntity controller) return controller;
        return block instanceof StorageInterfaceEntity endpoint ? endpoint.controller : null;
    }

    private static List<Integer> indices(IItemHandler handler, StorageNetwork network) {
        if (handler == network.getItemHandler()) {
            var indices = new ArrayList<Integer>();
            for (int i = 0; i < handler.getSlots(); i++) indices.add(i);
            return indices;
        }
        // Upstream's filtered endpoint compacts slot numbers. Preserve its actual mapping,
        // not item equality (two boxes may hold identical stacks). ABI checked before linking.
        if (!handler.getClass().getName().equals(
                "net.fxnt.fxntstorage.controller.StorageInterfaceFilteredEntity$FilteredItemHandler")) {
            throw new IllegalStateException("Unrecognized storage network handler");
        }
        try {
            Field source = handler.getClass().getDeclaredField("source");
            Field mapping = handler.getClass().getDeclaredField("filteredSlots");
            source.setAccessible(true);
            mapping.setAccessible(true);
            if (source.get(handler) != network.getItemHandler()) {
                throw new IllegalStateException("Storage interface still references an old network");
            }
            var values = (List<?>) mapping.get(handler);
            var indices = new ArrayList<Integer>();
            for (Object value : values) indices.add((Integer) value);
            if (indices.size() != handler.getSlots()) throw new IllegalStateException("Invalid filtered slots");
            return List.copyOf(indices);
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException("Cannot resolve storage network slots", error);
        }
    }
}
