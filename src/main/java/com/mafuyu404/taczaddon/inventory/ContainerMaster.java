package com.mafuyu404.taczaddon.inventory;

import net.minecraft.core.BlockPos;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.wrapper.InvWrapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class ContainerMaster {
    private ContainerMaster() {}

    public static List<ItemStack> readContainerFromPos(Level level, BlockPos pos) {
        List<ItemStack> items = new ArrayList<>();

        Optional<IItemHandler> handler = getContainerHandler(level, pos);
        if (handler.isEmpty()) {
            return items;
        }

        IItemHandler itemHandler = handler.get();
        for (int slot = 0; slot < itemHandler.getSlots(); slot++) {
            ItemStack stack = itemHandler.getStackInSlot(slot);
            if (!stack.isEmpty()) {
                items.add(stack.copy());
            }
        }

        return items;
    }

    public static Optional<IItemHandler> getContainerHandler(Level level, BlockPos pos) {
        if (!level.isLoaded(pos)) {
            return Optional.empty();
        }

        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity instanceof Container container) {
            return Optional.of(new InvWrapper(container));
        }

        return Optional.empty();
    }
}
