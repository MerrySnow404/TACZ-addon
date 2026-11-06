package com.mafuyu404.taczaddon.bootstrap;

import com.mafuyu404.taczaddon.TACZaddon;
import com.mafuyu404.taczaddon.config.network.ConfigSyncPacket;
import com.mafuyu404.taczaddon.gunsmith.GunSmithCraftingSessionManager;
import com.mafuyu404.taczaddon.inventory.ContainerReaderState;
import com.mafuyu404.taczaddon.network.NetworkHandler;
import com.mafuyu404.taczaddon.refit.VirtualAttachmentRules;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

@EventBusSubscriber(modid = TACZaddon.MODID)
public class ServerEvent {
    @SubscribeEvent
    public static void onPlayerLogin(EntityJoinLevelEvent event) {
        if (event.getEntity().level().isClientSide()) return;
        if (!(event.getEntity() instanceof Player player)) return;
        if (!(player instanceof ServerPlayer serverPlayer)) return;
        VirtualAttachmentRules.syncRuleWhenLogin(serverPlayer);
        NetworkHandler.sendToClient(serverPlayer, ConfigSyncPacket.fromServerConfig());
    }

    @SubscribeEvent(priority = net.neoforged.bus.api.EventPriority.LOWEST)
    public static void onTableInteraction(
            net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer player)
                || !player.level().isLoaded(event.getPos())) return;
        var state = player.level().getBlockState(event.getPos());
        if (!(state.getBlock()
                instanceof com.tacz.guns.block.AbstractGunSmithTableBlock tableBlock)) return;
        var rootPos = tableBlock.getRootPos(event.getPos(), state);
        if (player.level().isLoaded(rootPos)
                && player.level().getBlockEntity(rootPos)
                        instanceof com.tacz.guns.block.entity.GunSmithTableBlockEntity table) {
            GunSmithCraftingSessionManager.rememberTableInteraction(player, rootPos, table);
        }
    }

    @SubscribeEvent
    public static void onMenuOpen(
            net.neoforged.neoforge.event.entity.player.PlayerContainerEvent.Open event) {
        if (event.getEntity() instanceof ServerPlayer player
                && event.getContainer() instanceof com.tacz.guns.inventory.GunSmithTableMenu menu) {
            GunSmithCraftingSessionManager.createSessionFromPending(player, menu);
        } else {
            GunSmithCraftingSessionManager.clearPendingInteraction(event.getEntity().getUUID());
        }
    }

    @SubscribeEvent
    public static void onMenuClose(
            net.neoforged.neoforge.event.entity.player.PlayerContainerEvent.Close event) {
        GunSmithCraftingSessionManager.removeSession(
                event.getEntity().getUUID(), event.getContainer().containerId);
        ContainerReaderState.clear(event.getEntity());
    }

    @SubscribeEvent
    public static void onDimensionChange(PlayerEvent.PlayerChangedDimensionEvent event) {
        GunSmithCraftingSessionManager.clearPlayerState(event.getEntity().getUUID());
        ContainerReaderState.clear(event.getEntity());
    }

    @SubscribeEvent
    public static void onServerStopped(
            net.neoforged.neoforge.event.server.ServerStoppedEvent event) {
        GunSmithCraftingSessionManager.removeAll();
    }

    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        ContainerReaderState.clear(event.getEntity());
        GunSmithCraftingSessionManager.clearPlayerState(event.getEntity().getUUID());
    }
}
