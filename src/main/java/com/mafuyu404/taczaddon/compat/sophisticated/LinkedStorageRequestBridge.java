package com.mafuyu404.taczaddon.compat.sophisticated;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.UUID;
import net.p3pp3rf1y.sophisticatedcore.network.ItemStackReference;

/** Core now owns linked-storage requests; older Backpacks releases owned the same payload. */
final class LinkedStorageRequestBridge {
    private static final String CORE = "net.p3pp3rf1y.sophisticatedcore.linkedstorage.RequestLinkedStorageContentsPayload";
    private static final String BACKPACKS = "net.p3pp3rf1y.sophisticatedbackpacks.network.RequestLinkedStorageBackpackContentsPayload";

    private LinkedStorageRequestBridge() {}

    private static final class Request {
        static final Constructor<? extends CustomPacketPayload> CONSTRUCTOR = resolve(
                LinkedStorageRequestBridge.class.getClassLoader(), CORE, BACKPACKS);
    }

    static CustomPacketPayload create(UUID id, long revision, ItemStackReference reference) {
        try {
            return Request.CONSTRUCTOR.newInstance(id, revision, reference);
        } catch (InvocationTargetException failure) {
            throw new SophisticatedCompatibilityException("Linked storage request failed", failure.getCause());
        } catch (ReflectiveOperationException failure) {
            throw new SophisticatedCompatibilityException("Cannot create linked storage request", failure);
        }
    }

    static Constructor<? extends CustomPacketPayload> resolve(ClassLoader loader, String current, String legacy) {
        try {
            Class<?> type;
            try {
                type = Class.forName(current, false, loader);
            } catch (ClassNotFoundException absent) {
                type = Class.forName(legacy, false, loader);
            }
            return type.asSubclass(CustomPacketPayload.class).getConstructor(UUID.class, long.class, ItemStackReference.class);
        } catch (ReflectiveOperationException | ClassCastException failure) {
            throw new SophisticatedCompatibilityException("Unsupported linked storage request API", failure);
        }
    }
}
