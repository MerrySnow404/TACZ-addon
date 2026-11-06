package com.mafuyu404.taczaddon.inventory;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.items.IItemHandler;

import java.util.List;
import java.util.function.BiFunction;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/** Optional inventory capabilities installed once by mod bootstrap. */
public final class InventoryIntegrations {
    private InventoryIntegrations() {}

    public interface BlockBackpacks {
        boolean isBackpackBlock(Level level, BlockPos pos);

        void forEachBlockBackpackHandler(
                Player player, BlockPos pos, Consumer<IItemHandler> visitor);
    }

    public record PhysicalSlot(BlockEntity owner, int slot) {}

    public record BlockAccess(List<PhysicalSlot> slots, BooleanSupplier valid) {
        public BlockAccess {
            slots = List.copyOf(slots);
        }
    }

    private static BlockBackpacks backpacks;
    private static BiFunction<BlockEntity, IItemHandler, BlockAccess> physicalSlots;

    public static void configure(
            BlockBackpacks blockBackpacks,
            BiFunction<BlockEntity, IItemHandler, BlockAccess> slotBinding) {
        backpacks = blockBackpacks;
        physicalSlots = slotBinding;
    }

    static boolean isBackpackBlock(Level level, BlockPos pos) {
        return backpacks.isBackpackBlock(level, pos);
    }

    static void forEachBlockBackpackHandler(
            Player player, BlockPos pos, Consumer<IItemHandler> visitor) {
        backpacks.forEachBlockBackpackHandler(player, pos, visitor);
    }

    static BlockAccess bind(BlockEntity block, IItemHandler handler) {
        return physicalSlots.apply(block, handler);
    }
}
