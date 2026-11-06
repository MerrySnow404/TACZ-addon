package com.mafuyu404.taczaddon.event;

import com.mafuyu404.taczaddon.ModConstants;
import com.mafuyu404.taczaddon.client.ClientPacketHandlers;
import com.mafuyu404.taczaddon.compat.jei.JeiCompat;
import com.mafuyu404.taczaddon.network.ClientPacketDispatch;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

@Mod.EventBusSubscriber(value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD, modid = ModConstants.MODID)
public class SetupEvent {
    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        ClientPacketDispatch.bind(new ClientPacketHandlers());
        event.enqueueWork(JeiCompat::init);
    }
}
