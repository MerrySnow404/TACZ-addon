package com.mafuyu404.taczaddon.smoke;

import com.mafuyu404.taczaddon.compat.TaczOemCompat;
import com.mafuyu404.taczaddon.refit.VirtualAttachmentData;
import com.mojang.logging.LogUtils;

import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

import java.util.Arrays;
import java.util.Map;

/** Opt-in development source set, never included in the distributed addon jar. */
@EventBusSubscriber(modid = "taczaddon", value = Dist.CLIENT)
public final class TaczClientSmoke {
    private static boolean done;

    @SubscribeEvent
    public static void check(ClientTickEvent.Post event) throws Exception {
        if (done || Minecraft.getInstance().screen == null) return;
        done = true;
        boolean official = TaczOemCompat.isAvailable();
        if (official != Boolean.getBoolean("taczaddon.test.official"))
            throw new AssertionError("Wrong TACZ runtime");
        for (var entry :
                Map.ofEntries(
                                Map.entry(
                                        "client.input.AimKey",
                                        official
                                                ? "taczaddon$afterAimInput"
                                                : "taczaddon$afterAimPress"),
                                Map.entry("client.input.InteractKey", "onInteractBlock"),
                                Map.entry(
                                        "client.gui.GunRefitScreen",
                                        "taczaddon$addSafeAttachmentButtons"),
                                Map.entry(
                                        "client.gui.GunSmithTableScreen",
                                        "taczaddon$filterAttachmentProperty"),
                                Map.entry("client.gameplay.LocalPlayerDraw", "modifyDrawTime"),
                                Map.entry(
                                        "client.gameplay.LocalPlayerShoot", "taczaddon$afterShoot"),
                                Map.entry(
                                        "client.gameplay.LocalPlayerReload",
                                        "taczaddon$beforeReloadTrigger"),
                                Map.entry(
                                        "client.animation.statemachine.GunAnimationStateContext",
                                        "taczaddon$includeBackpackAndCuriosAmmo"),
                                Map.entry(
                                        "client.renderer.item.AnimateGeoItemRenderer",
                                        "taczaddon$fastSwapPutAwayTime"),
                                Map.entry(
                                        official
                                                ? "client.gameplay.ClientAmmoCount"
                                                : "client.gui.overlay.GunHudOverlay",
                                        official
                                                ? "taczaddon$appendExtraAmmo"
                                                : "taczaddon$appendBackpackAmmo"))
                        .entrySet()) {
            Class<?> target = Class.forName("com.tacz.guns." + entry.getKey());
            if (Arrays.stream(target.getDeclaredMethods())
                    .noneMatch(method -> method.getName().contains(entry.getValue()))) {
                throw new AssertionError("Missing applied mixin: " + entry);
            }
        }
        assertBridge(
                "com.tacz.guns.client.gui.GunSmithTableScreen",
                com.mafuyu404.taczaddon.gunsmith.client.GunSmithScreenController.Host.class);
        assertBridge(
                "com.tacz.guns.client.gui.GunRefitScreen",
                com.mafuyu404.taczaddon.refit.client.RefitScreenController.Host.class);
        assertBridge(
                "com.tacz.guns.inventory.GunSmithTableMenu",
                com.mafuyu404.taczaddon.gunsmith.GunSmithRecipeAccess.class);
        assertBridge(
                "net.minecraft.world.entity.player.Inventory",
                com.mafuyu404.taczaddon.inventory.InventoryContentsAccess.class);
        if (official) {
            var marked = VirtualAttachmentData.markVirtual(new ItemStack(Items.STONE));
            var method =
                    Class.forName("com.tacz.guns.util.VirtualOemAttachment")
                            .getMethod("isMarked", ItemStack.class);
            if (!(boolean) method.invoke(null, marked))
                throw new AssertionError("Native OEM code must recognize addon virtual items");
            if ((boolean) method.invoke(null, new ItemStack(Items.STONE)))
                throw new AssertionError("Physical items must remain physical");
        }
        LogUtils.getLogger()
                .info(
                        "TACZ_COMPAT_SMOKE_PASS runtime={}",
                        official ? "official-1.2.0" : "legacy-1.1.8-hotfix-r7");
        Minecraft.getInstance().stop();
    }

    private static void assertBridge(String target, Class<?> bridge) throws ClassNotFoundException {
        if (!bridge.isAssignableFrom(Class.forName(target))) {
            throw new AssertionError(
                    "Missing feature bridge " + bridge.getName() + " on " + target);
        }
    }
}
