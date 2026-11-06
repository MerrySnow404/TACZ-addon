package com.mafuyu404.taczaddon.event;

import com.mafuyu404.taczaddon.ModConstants;
import com.mafuyu404.taczaddon.client.ammo.BackpackHudState;
import com.mafuyu404.taczaddon.client.config.ClientSyncedConfig;
import com.mafuyu404.taczaddon.client.gunsmith.BetterGunSmithTable;
import com.mafuyu404.taczaddon.client.input.GunSwitchInput;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(
        modid = ModConstants.MODID,
        value = Dist.CLIENT,
        bus = Mod.EventBusSubscriber.Bus.FORGE
)
public final class ClientSyncedConfigEvents {
    private ClientSyncedConfigEvents() {
    }

    @SubscribeEvent
    public static void onLoggingOut(
            ClientPlayerNetworkEvent.LoggingOut event
    ) {
        ClientSyncedConfig.resetToSafeDefaults();
        BetterGunSmithTable.clearAllBrowseStates();
        GunSwitchInput.reset();
        BackpackHudState.reset();
    }
}