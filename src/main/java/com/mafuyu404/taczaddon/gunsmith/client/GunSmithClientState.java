package com.mafuyu404.taczaddon.gunsmith.client;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;

import java.util.*;

public final class GunSmithClientState {
    private GunSmithClientState() {}

    private static BlockPos lastGunSmithInteractPos;
    private static ItemStack gunSmithJeiStack = ItemStack.EMPTY;
    private static Map<String, String> attachmentData = Map.of();

    public static void clear() {
        lastGunSmithInteractPos = null;
        gunSmithJeiStack = ItemStack.EMPTY;
        attachmentData = Map.of();
    }

    public static Optional<BlockPos> getLastGunSmithInteractPos() {
        return Optional.ofNullable(lastGunSmithInteractPos);
    }

    public static void setLastGunSmithInteractPos(BlockPos blockPos) {
        lastGunSmithInteractPos = blockPos;
    }

    public static ItemStack getGunSmithJeiStack() {
        return gunSmithJeiStack.copy();
    }

    public static void setGunSmithJeiStack(ItemStack itemStack) {
        gunSmithJeiStack = itemStack.copy();
    }

    public static Map<String, String> getAttachmentData() {
        return attachmentData;
    }

    public static void setAttachmentData(Map<String, String> data) {
        attachmentData = Map.copyOf(data);
    }
}
