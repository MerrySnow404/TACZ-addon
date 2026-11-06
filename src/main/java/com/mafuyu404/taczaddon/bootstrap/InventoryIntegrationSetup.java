package com.mafuyu404.taczaddon.bootstrap;

import com.mafuyu404.taczaddon.compat.CreateStorageCompat;
import com.mafuyu404.taczaddon.compat.SophisticatedBackpacksCompat;
import com.mafuyu404.taczaddon.inventory.InventoryIntegrations;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.items.IItemHandler;

import java.util.function.Consumer;

public final class InventoryIntegrationSetup {
    private InventoryIntegrationSetup() {}

    public static void register() {
        InventoryIntegrations.configure(
                new InventoryIntegrations.BlockBackpacks() {
                    public boolean isBackpackBlock(Level level, BlockPos pos) {
                        return SophisticatedBackpacksCompat.isBackpackBlock(level, pos);
                    }

                    public void forEachBlockBackpackHandler(
                            Player player, BlockPos pos, Consumer<IItemHandler> visitor) {
                        SophisticatedBackpacksCompat.forEachBlockBackpackHandler(
                                player, pos, visitor);
                    }
                },
                CreateStorageCompat::bind);
    }
}
