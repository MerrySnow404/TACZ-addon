package com.mafuyu404.taczaddon.bootstrap.client;

import com.mafuyu404.taczaddon.TACZaddon;
import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.KeyMapping;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.client.settings.KeyModifier;

import org.lwjgl.glfw.GLFW;

@EventBusSubscriber(value = Dist.CLIENT, modid = TACZaddon.MODID)
public class KeyBindings {
    public static final KeyMapping SWITCH_GUN_KEY =
            new KeyMapping(
                    "key.taczaddon.switch_gun.desc",
                    KeyConflictContext.IN_GAME,
                    KeyModifier.NONE,
                    InputConstants.Type.KEYSYM,
                    GLFW.GLFW_KEY_TAB,
                    "key.categories.taczaddon");

    @SubscribeEvent
    public static void registerKeyMapping(RegisterKeyMappingsEvent event) {
        event.register(KeyBindings.SWITCH_GUN_KEY);
    }
}
