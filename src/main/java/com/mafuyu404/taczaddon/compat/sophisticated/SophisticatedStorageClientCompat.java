package com.mafuyu404.taczaddon.compat.sophisticated;

import com.mafuyu404.taczaddon.compat.api.ApiShapeProbe;
import com.mafuyu404.taczaddon.compat.api.OptionalAbiStatus;

import com.mojang.logging.LogUtils;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.ContainerScreenEvent;
import net.minecraftforge.fml.ModList;
import org.slf4j.Logger;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

@OnlyIn(Dist.CLIENT)
public final class SophisticatedStorageClientCompat {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String BACKPACKS_MOD_ID =
            SophisticatedBackpackGeneration.BACKPACKS_MOD_ID;
    private static final String STORAGE_MOD_ID =
            "sophisticatedstorage";
    private static final String STORAGE_SCREEN_BASE =
            "net.p3pp3rf1y.sophisticatedcore.client.gui.StorageScreenBase";
    private static final String STORAGE_MENU_BASE =
            "net.p3pp3rf1y.sophisticatedcore.common.gui.StorageContainerMenuBase";

    private static volatile boolean linkageBroken;
    private static volatile OptionalAbiStatus status;
    private static final AtomicBoolean LINKAGE_WARNING_LOGGED =
            new AtomicBoolean();

    private SophisticatedStorageClientCompat() {
    }

    public static boolean isAnySophisticatedStorageLoaded() {
        ModList modList = ModList.get();
        return modList != null
                && (modList.isLoaded(BACKPACKS_MOD_ID)
                || modList.isLoaded(STORAGE_MOD_ID));
    }

    /**
     * Verified storage-screen shape the highlighting backend links against.
     */
    public static OptionalAbiStatus status() {
        OptionalAbiStatus current = status;
        if (current != null) {
            return current;
        }
        ApiShapeProbe.ClassBytes source =
                ApiShapeProbe.sourceFor(
                        SophisticatedStorageClientCompat.class
                );
        OptionalAbiStatus resolved = OptionalAbiStatus.of(
                ApiShapeProbe.hasClass(source, STORAGE_SCREEN_BASE),
                ApiShapeProbe.satisfies(
                        source,
                        STORAGE_MENU_BASE,
                        List.of(
                                ApiShapeProbe.method(
                                        "isStorageInventorySlot",
                                        "(I)Z"
                                ),
                                ApiShapeProbe.method(
                                        "isInaccessibleSlot",
                                        "(I)Z"
                                )
                        )
                )
        );
        status = resolved;
        return resolved;
    }

    public static boolean isSupported() {
        return status().supported();
    }

    public static boolean isUsable() {
        return isAnySophisticatedStorageLoaded()
                && isSupported()
                && !linkageBroken;
    }

    public static boolean isStorageScreen(
            AbstractContainerScreen<?> screen
    ) {
        if (!isUsable()) {
            return false;
        }
        try {
            return SophisticatedStorageClientCompatInner
                    .isStorageScreen(screen);
        } catch (LinkageError linkageError) {
            breakLinkage(linkageError);
            return false;
        }
    }

    public static void renderItemRelations(
            ContainerScreenEvent.Render.Foreground event
    ) {
        if (!isUsable()) {
            return;
        }
        try {
            SophisticatedStorageClientCompatInner
                    .renderItemRelations(event);
        } catch (LinkageError linkageError) {
            breakLinkage(linkageError);
        }
    }

    private static void breakLinkage(LinkageError linkageError) {
        linkageBroken = true;
        if (LINKAGE_WARNING_LOGGED.compareAndSet(false, true)) {
            LOGGER.warn(
                    "[TACZ-addon] Sophisticated Storage/Core API is "
                            + "unavailable; storage highlighting disabled",
                    linkageError
            );
        }
    }
}
