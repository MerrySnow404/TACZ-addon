package com.mafuyu404.taczaddon.event;

import com.mafuyu404.taczaddon.ModConstants;
import com.mafuyu404.taczaddon.client.ammo.BackpackHudState;
import com.mafuyu404.taczaddon.client.gunsmith.GunSmithSourceScreenAccess;
import com.mafuyu404.taczaddon.client.input.GunSwitchInput;
import com.mafuyu404.taczaddon.client.refit.GunRefitScreenAccess;
import com.mafuyu404.taczaddon.compat.sophisticated.SophisticatedStorageClientCompat;

import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ContainerScreenEvent;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(
        modid = ModConstants.MODID,
        value = Dist.CLIENT,
        bus = Mod.EventBusSubscriber.Bus.FORGE
)
public final class ClientEvent {
    private ClientEvent() {
    }

    @SubscribeEvent
    public static void onScreenInitPost(
            ScreenEvent.Init.Post event
    ) {
        if (event.getScreen()
                instanceof GunSmithSourceScreenAccess sourceAccess) {
            sourceAccess.taczaddon$onScreenInit();
        }
        if (event.getScreen()
                instanceof GunRefitScreenAccess refitAccess) {
            refitAccess.taczaddon$onRefitScreenInit();
        }
    }

    @SubscribeEvent
    public static void tickRefitSourceRefresh(
            TickEvent.ClientTickEvent event
    ) {
        if (event.phase == TickEvent.Phase.END) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen
                instanceof GunRefitScreenAccess refitAccess) {
            refitAccess.taczaddon$tickRefitSourceRefresh();
        }
    }

    @SubscribeEvent
    public static void renderSophisticatedItemRelations(
            ContainerScreenEvent.Render.Foreground event
    ) {
        SophisticatedStorageClientCompat.renderItemRelations(event);
    }

    @SubscribeEvent
    public static void storeGunList(InputEvent.Key event) { GunSwitchInput.storeGunList(event); }

    @SubscribeEvent
    public static void switchGun(InputEvent.MouseScrollingEvent event) { GunSwitchInput.switchGun(event); }

    @SubscribeEvent
    public static void storageBackpack(TickEvent.ClientTickEvent event) { BackpackHudState.storageBackpack(event); }
}
