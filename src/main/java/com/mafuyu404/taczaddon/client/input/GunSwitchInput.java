package com.mafuyu404.taczaddon.client.input;

import com.mafuyu404.taczaddon.client.input.KeyBindings;
import com.mafuyu404.taczaddon.network.NetworkHandler;
import com.mafuyu404.taczaddon.network.SwitchGunPacket;

import com.tacz.guns.api.item.IGun;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.event.InputEvent;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class GunSwitchInput {
    private GunSwitchInput() {}
    private static List<String> storedGunIds = List.of();
    public static void reset() { storedGunIds = List.of(); }
    public static void storeGunList(InputEvent.Key event) {
        Minecraft minecraft = Minecraft.getInstance();

        if (minecraft.screen != null
                || event.getKey()
                != KeyBindings.SWITCH_GUN_KEY.getKey().getValue()
                || event.getAction() != GLFW.GLFW_PRESS
                || minecraft.player == null) {
            return;
        }

        ItemStack heldGun =
                minecraft.player.getMainHandItem();
        if (IGun.getIGunOrNull(heldGun) == null) {
            return;
        }

        ArrayList<String> gunIds = new ArrayList<>();
        Inventory inventory =
                minecraft.player.getInventory();

        for (int slot = 0;
             slot < inventory.getContainerSize();
             slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (IGun.getIGunOrNull(stack) != null) {
                taczaddon$getGunId(stack).ifPresent(gunIds::add);
            }
        }

        if (gunIds.size() > 1) {
            storedGunIds = List.copyOf(gunIds);
        }
    }

    public static void switchGun(
            InputEvent.MouseScrollingEvent event
    ) {
        Minecraft minecraft = Minecraft.getInstance();

        if (minecraft.screen != null
                || !KeyBindings.SWITCH_GUN_KEY.isDown()) {
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

        String currentGunId =
                taczaddon$getGunId(heldGun).orElse(null);
        if (currentGunId == null) {
            return;
        }

        List<String> gunIds = storedGunIds;

        if (gunIds.size() <= 1) {
            return;
        }

        int targetIndex = gunIds.lastIndexOf(currentGunId);
        if (targetIndex < 0) {
            return;
        }

        boolean forward = event.getScrollDelta() < 0.0D;
        targetIndex = forward
                ? (targetIndex + 1) % gunIds.size()
                : (targetIndex - 1 + gunIds.size()) % gunIds.size();

        int targetSlot = -1;
        Inventory inventory = player.getInventory();

        for (int index = 0;
             index < inventory.getContainerSize();
             index++) {
            int slot = forward
                    ? index
                    : inventory.getContainerSize() - 1 - index;

            if (taczaddon$getGunId(inventory.getItem(slot))
                    .filter(gunIds.get(targetIndex)::equals)
                    .isPresent()) {
                targetSlot = slot;
                break;
            }
        }

        if (targetSlot < 0) {
            return;
        }

        NetworkHandler.CHANNEL.sendToServer(
                new SwitchGunPacket(targetSlot)
        );
        event.setCanceled(true);
    }

    private static Optional<String> taczaddon$getGunId(
            ItemStack itemStack
    ) {
        if (itemStack.isEmpty()) {
            return Optional.empty();
        }

        CompoundTag tag = itemStack.getTag();
        if (tag == null) {
            return Optional.empty();
        }

        String gunId = tag.getString("GunId");
        return gunId.isEmpty()
                ? Optional.empty()
                : Optional.of(gunId);
    }
}
