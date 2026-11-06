package com.mafuyu404.taczaddon.refit.client;

import com.mafuyu404.taczaddon.network.NetworkHandler;
import com.mafuyu404.taczaddon.refit.RefitSourceResolver.ExternalCandidate;
import com.mafuyu404.taczaddon.refit.VirtualAttachmentRules;
import com.mafuyu404.taczaddon.refit.network.RefitSourceRefreshRequestPacket;
import com.mafuyu404.taczaddon.refit.network.RefitSourceSnapshotPacket;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.client.gui.GunRefitScreen;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import java.util.*;

public final class RefitExternalSourceState {
    private static List<ExternalCandidate> candidates = List.of();
    private static Object screenIdentity;
    private static Object levelIdentity;
    private static ResourceLocation gunId;
    private static int gunSlot = -1;
    private static long nextRequestId;
    private static long pendingId = -1;
    private static long lastRequestTick = Long.MIN_VALUE;
    private static boolean applying;

    private RefitExternalSourceState() {}

    public static void tick() {
        requestIfNeeded();
    }

    public static void requestIfNeeded() {
        if (applying) return;
        var mc = Minecraft.getInstance();
        var player = mc.player;
        if (player == null
                || !(mc.screen instanceof GunRefitScreen)
                || VirtualAttachmentRules.isLiberated(player)) {
            clear();
            return;
        }
        IGun gun = IGun.getIGunOrNull(player.getMainHandItem());
        if (gun == null) {
            clear();
            return;
        }
        ResourceLocation id = gun.getGunId(player.getMainHandItem());
        if (screenIdentity != mc.screen
                || levelIdentity != player.level()
                || gunSlot != player.getInventory().selected
                || !Objects.equals(gunId, id)) {
            clear();
            screenIdentity = mc.screen;
            levelIdentity = player.level();
            gunId = id;
            gunSlot = player.getInventory().selected;
        }
        long tick = player.level().getGameTime();
        if (lastRequestTick != Long.MIN_VALUE
                && tick >= lastRequestTick
                && tick - lastRequestTick < 10) return;
        lastRequestTick = tick;
        pendingId = ++nextRequestId;
        NetworkHandler.sendToServer(new RefitSourceRefreshRequestPacket(pendingId));
    }

    public static void accept(RefitSourceSnapshotPacket packet) {
        var mc = Minecraft.getInstance();
        if (packet.requestId() != pendingId
                || mc.player == null
                || screenIdentity != mc.screen
                || levelIdentity != mc.player.level()
                || gunSlot != mc.player.getInventory().selected
                || VirtualAttachmentRules.isLiberated(mc.player)) return;
        IGun gun = IGun.getIGunOrNull(mc.player.getMainHandItem());
        if (gun == null || !Objects.equals(gunId, gun.getGunId(mc.player.getMainHandItem())))
            return;
        pendingId = -1;
        var next =
                packet.candidates().stream()
                        .filter(
                                entry ->
                                        entry.locator()
                                                .dimension()
                                                .equals(mc.player.level().dimension().location()))
                        .toList();
        boolean same = candidates.size() == next.size();
        for (int i = 0; same && i < next.size(); i++) {
            var a = candidates.get(i);
            var b = next.get(i);
            same =
                    a.locator().equals(b.locator())
                            && a.attachmentId().equals(b.attachmentId())
                            && a.type() == b.type()
                            && ItemStack.matches(a.displayStack(), b.displayStack());
        }
        candidates = List.copyOf(next);
        if (!same && mc.screen instanceof GunRefitScreen screen) {
            applying = true;
            try {
                screen.init();
            } finally {
                applying = false;
            }
        }
    }

    public static RefitDisplayInventory createDisplayInventory(Inventory real) {
        return new RefitDisplayInventory(
                real, VirtualAttachmentRules.isLiberated(real.player) ? List.of() : candidates);
    }

    public static void clear() {
        candidates = List.of();
        screenIdentity = null;
        levelIdentity = null;
        gunId = null;
        gunSlot = -1;
        pendingId = -1;
        lastRequestTick = Long.MIN_VALUE;
    }
}
