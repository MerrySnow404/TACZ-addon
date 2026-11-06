package com.mafuyu404.taczaddon.combat.client;

import com.mafuyu404.taczaddon.TACZaddon;
import com.mafuyu404.taczaddon.bootstrap.client.KeyBindings;
import com.mafuyu404.taczaddon.combat.network.SwitchGunPacket;
import com.mafuyu404.taczaddon.inventory.ItemStackData;
import com.mafuyu404.taczaddon.network.NetworkHandler;
import com.tacz.guns.api.item.IGun;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.InputEvent;

import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@EventBusSubscriber(modid = TACZaddon.MODID, value = Dist.CLIENT)
public final class GunSwitchController {
    @SubscribeEvent
    public static void storeGunList(InputEvent.Key event) {
        Minecraft minecraft = Minecraft.getInstance();

        if (minecraft.screen != null) {
            return;
        }

        if (event.getKey() != KeyBindings.SWITCH_GUN_KEY.getKey().getValue()) {
            return;
        }

        if (event.getAction() != GLFW.GLFW_PRESS) {
            return;
        }

        LocalPlayer player = minecraft.player;
        if (player == null) {
            return;
        }

        ItemStack heldGun = player.getMainHandItem();
        if (IGun.getIGunOrNull(heldGun) == null) {
            return;
        }

        List<String> gunList = new ArrayList<>();
        Inventory inventory = player.getInventory();

        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);

            if (IGun.getIGunOrNull(stack) != null) {
                taczaddon$getGunId(stack).ifPresent(gunList::add);
            }
        }

        if (gunList.size() <= 1) {
            return;
        }

        GunSwitchState.setGunSwitchList(gunList);
    }

    @SubscribeEvent
    public static void switchGun(InputEvent.MouseScrollingEvent event) {
        Minecraft minecraft = Minecraft.getInstance();

        if (minecraft.screen != null) {
            return;
        }

        if (!KeyBindings.SWITCH_GUN_KEY.isDown()) {
            return;
        }

        LocalPlayer player = minecraft.player;
        if (player == null) {
            return;
        }

        ItemStack heldGun = player.getMainHandItem();
        if (IGun.getIGunOrNull(heldGun) == null) {
            return;
        }

        String currentGunId = taczaddon$getGunId(heldGun).orElse(null);

        if (currentGunId == null) {
            return;
        }

        List<String> gunList = GunSwitchState.getGunSwitchList();

        if (gunList.size() <= 1) {
            return;
        }

        int currentIndex = gunList.lastIndexOf(currentGunId);
        if (currentIndex < 0) {
            return;
        }

        double scrollDelta = event.getScrollDeltaY();

        int targetGunIndex;
        if (scrollDelta < 0) {
            targetGunIndex = currentIndex == gunList.size() - 1 ? 0 : currentIndex + 1;
        } else {
            targetGunIndex = currentIndex == 0 ? gunList.size() - 1 : currentIndex - 1;
        }

        String targetGunId = gunList.get(targetGunIndex);
        Inventory inventory = player.getInventory();

        int targetSlot = findGunSlot(inventory, targetGunId, scrollDelta < 0);

        if (targetSlot < 0) {
            return;
        }

        NetworkHandler.sendToServer(new SwitchGunPacket(targetSlot));

        event.setCanceled(true);
    }

    private static int findGunSlot(Inventory inventory, String targetGunId, boolean searchForward) {
        for (int offset = 0; offset < inventory.getContainerSize(); offset++) {
            int slot = searchForward ? offset : inventory.getContainerSize() - 1 - offset;

            ItemStack stack = inventory.getItem(slot);

            if (taczaddon$getGunId(stack).filter(targetGunId::equals).isPresent()) {
                return slot;
            }
        }

        return -1;
    }

    private static Optional<String> taczaddon$getGunId(ItemStack itemStack) {
        if (itemStack.isEmpty()) {
            return Optional.empty();
        }

        String gunId = ItemStackData.getCustomDataCopy(itemStack).getString("GunId");

        return gunId.isEmpty() ? Optional.empty() : Optional.of(gunId);
    }
}
