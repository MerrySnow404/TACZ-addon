package com.mafuyu404.taczaddon.inventory;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandlerModifiable;

import java.util.*;

/** Masks duplicate physical slots without bypassing the original endpoint's permissions. */
final class PhysicalSourceFilter implements IItemHandlerModifiable {
    private final NearbyInventorySourceResolver.Source source;
    private final InventoryIntegrations.BlockAccess access;
    private final Set<Integer> allowed;

    private PhysicalSourceFilter(
            NearbyInventorySourceResolver.Source source,
            InventoryIntegrations.BlockAccess access,
            Set<Integer> allowed) {
        this.source = source;
        this.access = access;
        this.allowed = Set.copyOf(allowed);
    }

    static List<NearbyInventorySourceResolver.Source> filter(
            ServerPlayer player, List<NearbyInventorySourceResolver.Source> sources) {
        var result = new ArrayList<NearbyInventorySourceResolver.Source>();
        Set<InventoryIntegrations.PhysicalSlot> seen = new HashSet<>();
        for (var source : sources) {
            try {
                var block = player.serverLevel().getBlockEntity(source.pos());
                var access =
                        InventoryIntegrations.bind(
                                block,
                                source.backendIdentity()
                                                instanceof
                                                net.neoforged.neoforge.items.IItemHandler handler
                                        ? handler
                                        : source.handler());
                if (access == null) {
                    result.add(source);
                    continue;
                }
                if (access.slots().size() != source.handler().getSlots()) continue;
                Set<Integer> allowed = new HashSet<>();
                for (int slot = 0; slot < access.slots().size(); slot++) {
                    var physical = access.slots().get(slot);
                    if (physical.slot() < 0
                            || source.handler().getStackInSlot(slot).isEmpty()
                            || source.handler().extractItem(slot, 1, true).isEmpty()) continue;
                    if (seen.add(physical)) allowed.add(slot);
                }
                if (!allowed.isEmpty())
                    result.add(
                            new NearbyInventorySourceResolver.Source(
                                    source.pos(),
                                    source.kind(),
                                    new PhysicalSourceFilter(source, access, allowed)));
            } catch (RuntimeException failure) {
                com.mojang.logging.LogUtils.getLogger()
                        .warn("Skipping stale Create Storage source {}", source.pos(), failure);
            }
        }
        return List.copyOf(result);
    }

    boolean isValid() {
        return source.isValid() && access.valid().getAsBoolean();
    }

    Object backendIdentity() {
        return source.backendIdentity();
    }

    void markChanged() {
        source.markChanged();
    }

    private boolean accepts(int slot) {
        return allowed.contains(slot) && isValid();
    }

    public int getSlots() {
        return source.handler().getSlots();
    }

    public ItemStack getStackInSlot(int slot) {
        return accepts(slot) ? source.handler().getStackInSlot(slot) : ItemStack.EMPTY;
    }

    public ItemStack extractItem(int slot, int amount, boolean simulate) {
        return accepts(slot)
                ? source.handler().extractItem(slot, amount, simulate)
                : ItemStack.EMPTY;
    }

    public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
        return accepts(slot) ? source.handler().insertItem(slot, stack, simulate) : stack;
    }

    public int getSlotLimit(int slot) {
        return accepts(slot) ? source.handler().getSlotLimit(slot) : 0;
    }

    public boolean isItemValid(int slot, ItemStack stack) {
        return accepts(slot) && source.handler().isItemValid(slot, stack);
    }

    public void setStackInSlot(int slot, ItemStack stack) {
        if (!accepts(slot))
            throw new IllegalStateException("Physical source changed before restoration");
        ((IItemHandlerModifiable) source.handler()).setStackInSlot(slot, stack);
    }
}
