package com.mafuyu404.taczaddon.client.gunsmith;

import com.mafuyu404.taczaddon.client.config.ClientSyncedConfig;
import com.mafuyu404.taczaddon.client.gunsmith.GunSmithCraftScreenAccess;
import com.mafuyu404.taczaddon.client.gunsmith.GunSmithSourceScreenAccess;
import com.mafuyu404.taczaddon.client.ui.ItemIconToast;
import com.mafuyu404.taczaddon.config.Config;
import com.mafuyu404.taczaddon.network.GunSmithCraftResultPacket;
import com.mafuyu404.taczaddon.network.GunSmithSourceSnapshotPacket;
import com.mafuyu404.taczaddon.network.ServerFeatureConfigSyncPacket;

import com.tacz.guns.client.gui.GunSmithTableScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class ClientGunSmithPacketHandler {
    private ClientGunSmithPacketHandler() {
    }

    public static void handleConfigSync(
            ServerFeatureConfigSyncPacket message
    ) {
        ClientSyncedConfig.apply(message.batchCraftMax(), message.enableShootWhileReloading(), message.enableFastSwapGun());

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen
                instanceof GunSmithSourceScreenAccess sourceAccess) {
            sourceAccess.taczaddon$requestSourceRefresh();
        }
    }

    public static void handleSourceSnapshot(
            GunSmithSourceSnapshotPacket message
    ) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!(minecraft.screen instanceof GunSmithTableScreen screen)
                || !(screen
                instanceof GunSmithSourceScreenAccess sourceAccess)) {
            return;
        }

        sourceAccess.taczaddon$acceptSourceSnapshot(
                message.containerId(),
                message.requestId(),
                message.sourceRevision(),
                message.externalStacks(),
                message.externalSourcesAuthorized(),
                message.displayTruncated(),
                message.aggregateCounts()
        );
    }

    public static void handleCraftResult(
            GunSmithCraftResultPacket message
    ) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!(minecraft.screen instanceof GunSmithTableScreen screen)
                || !(screen
                instanceof GunSmithCraftScreenAccess craftAccess)) {
            return;
        }

        boolean accepted = craftAccess.taczaddon$acceptCraftResult(
                message.containerId(),
                message.requestId(),
                message.success(),
                message.craftedExecutions(),
                message.outputPerCraft(),
                message.failureReason()
        );

        if (!accepted) {
            return;
        }

        if (message.success()
                && message.craftedExecutions() > 0
                && Config.enableGunSmithTableCraftToast()) {
            ItemStack output = message.outputPerCraft();
            int totalCount = output.getCount()
                    * message.craftedExecutions();

            Component title = Component.translatable(
                    "gui.taczaddon.gun_smith_table.crafted",
                    message.craftedExecutions()
            );
            Component description = output
                    .getHoverName()
                    .copy()
                    .append(Component.literal(" × " + totalCount));

            ItemIconToast.show(
                    title,
                    description,
                    output
            );
        } else if (!message.success()) {
            /*
             * Surface the failure instead of leaving the click without any
             * feedback; the request is finished and never re-sent.
             */
            String reason = message.failureReason() == null
                    ? "unknown"
                    : message.failureReason().name().toLowerCase(
                    java.util.Locale.ROOT
            );
            if (minecraft.player != null) {
                minecraft.player.displayClientMessage(
                    Component.translatable(
                            "gui.taczaddon.gun_smith_table.failed",
                            Component.translatable(
                                    "gui.taczaddon.gun_smith_table.failure."
                                            + reason
                            )
                    ),
                    true
                );
            }
        }

        if (screen instanceof GunSmithSourceScreenAccess sourceAccess) {
            sourceAccess.taczaddon$requestSourceRefresh();
        }
    }
}
