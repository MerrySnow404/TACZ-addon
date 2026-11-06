package com.mafuyu404.taczaddon.bootstrap.client;

import com.mafuyu404.taczaddon.TACZaddon;
import com.mafuyu404.taczaddon.combat.client.GunSwitchState;
import com.mafuyu404.taczaddon.config.ClientRuleState;
import com.mafuyu404.taczaddon.config.ClientSyncedConfig;
import com.mafuyu404.taczaddon.gunsmith.client.GunSmithClientState;
import com.mafuyu404.taczaddon.gunsmith.client.GunSmithCraftBridgeState;
import com.mafuyu404.taczaddon.inventory.client.BackpackInventoryCache;
import com.mafuyu404.taczaddon.refit.client.RefitExternalSourceState;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/** Client lifecycle coordination; feature state is owned by its module. */
@EventBusSubscriber(modid = TACZaddon.MODID, value = Dist.CLIENT)
public final class ClientEvent {
    private ClientEvent() {}

    @SubscribeEvent
    public static void onTableInteraction(PlayerInteractEvent.RightClickBlock event) {
        GunSmithClientState.setLastGunSmithInteractPos(event.getHitVec().getBlockPos());
    }

    @SubscribeEvent
    public static void refreshRefitSources(ClientTickEvent.Pre event) {
        RefitExternalSourceState.tick();
    }

    @SubscribeEvent
    public static void onClientLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        BackpackInventoryCache.clearClientCaches();
        GunSmithClientState.clear();
        GunSwitchState.clear();
        ClientRuleState.clear();
        ClientSyncedConfig.reset();
        GunSmithCraftBridgeState.reset();
        RefitExternalSourceState.clear();
    }
}
