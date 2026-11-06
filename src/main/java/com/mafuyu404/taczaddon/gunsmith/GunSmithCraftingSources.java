package com.mafuyu404.taczaddon.gunsmith;

import com.mafuyu404.taczaddon.config.CommonConfig;
import com.mafuyu404.taczaddon.gunsmith.crafting.CraftingItemSource;
import com.mafuyu404.taczaddon.gunsmith.crafting.CraftingSourceKey;
import com.mafuyu404.taczaddon.gunsmith.crafting.PlayerInventorySource;
import com.mafuyu404.taczaddon.inventory.NearbyInventorySourceResolver;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandlerModifiable;

import java.util.*;

public final class GunSmithCraftingSources {
    private GunSmithCraftingSources() {}

    public static List<CraftingItemSource> resolve(
            ServerPlayer player, GunSmithCraftingSessionManager.GunSmithCraftingSession session) {
        List<CraftingItemSource> sources = new ArrayList<>();
        sources.add(new PlayerInventorySource(player));
        if (CommonConfig.enableNearbyContainerSources()) {
            for (var source :
                    NearbyInventorySourceResolver.resolve(
                            player,
                            session.getTablePos(),
                            CommonConfig.getContainerScanRadius(),
                            1)) {
                try {
                    if (source.isValid() && source.handler().getSlots() > 0)
                        sources.add(new NearbySource(player, session, source));
                } catch (RuntimeException exception) {
                    com.mojang.logging.LogUtils.getLogger()
                            .warn(
                                    "Skipping unavailable gunsmith source {}",
                                    source.pos(),
                                    exception);
                }
            }
        }
        session.updateSourceKeys(sources.stream().map(CraftingItemSource::key).toList());
        return List.copyOf(sources);
    }

    private record NearbySource(
            ServerPlayer owner,
            GunSmithCraftingSessionManager.GunSmithCraftingSession session,
            NearbyInventorySourceResolver.Source source)
            implements CraftingItemSource {
        public CraftingSourceKey key() {
            return new CraftingSourceKey.BlockEntity(session.getDimension(), source.pos());
        }

        public Object backendIdentity() {
            return source.backendIdentity();
        }

        public int slotCount() {
            return source.handler().getSlots();
        }

        public ItemStack getStackInSlot(int slot) {
            return source.handler().getStackInSlot(slot);
        }

        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            return source.handler().extractItem(slot, amount, simulate);
        }

        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            return source.handler().insertItem(slot, stack, simulate);
        }

        public void restoreSlot(int slot, ItemStack snapshot) {
            ((IItemHandlerModifiable) source.handler()).setStackInSlot(slot, snapshot.copy());
        }

        public boolean isValid(ServerPlayer player) {
            return owner == player
                    && CommonConfig.enableNearbyContainerSources()
                    && session.validate(player, session.getContainerId())
                    && NearbyInventorySourceResolver.inRange(
                            session.getTablePos(),
                            source.pos(),
                            CommonConfig.getContainerScanRadius(),
                            1)
                    && source.isValid();
        }

        public void markChanged() {
            source.markChanged();
        }

        public void synchronize(ServerPlayer player) {
            source.synchronize(player);
        }
    }
}
