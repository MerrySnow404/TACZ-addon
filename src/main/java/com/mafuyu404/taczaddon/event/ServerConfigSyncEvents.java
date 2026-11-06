package com.mafuyu404.taczaddon.event;

import com.mafuyu404.taczaddon.ModConstants;
import com.mafuyu404.taczaddon.config.CommonConfig;
import com.mafuyu404.taczaddon.network.ServerPolicySync;

import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.config.ModConfigEvent;
import net.minecraftforge.server.ServerLifecycleHooks;

@Mod.EventBusSubscriber(
        modid = ModConstants.MODID,
        bus = Mod.EventBusSubscriber.Bus.MOD
)
public final class ServerConfigSyncEvents {
    private ServerConfigSyncEvents() {
    }

    @SubscribeEvent
    public static void onCommonConfigReloaded(
            ModConfigEvent.Reloading event
    ) {
        if (event.getConfig().getSpec() != CommonConfig.SPEC) {
            return;
        }

        var server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            return;
        }

        server.getPlayerList()
                .getPlayers()
                .forEach(ServerPolicySync::sendServerConfig);
    }
}
