package com.mafuyu404.taczaddon.compat;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Adapts the linked-storage protocol moved from Backpacks to Core in Core 1.5.2. */
final class SophisticatedLinkedStorageCompatNetwork {
    private static final String REFERENCE = "net.p3pp3rf1y.sophisticatedcore.network.ItemStackReference";
    enum Protocol {
        CORE("net.p3pp3rf1y.sophisticatedcore.",
                "linkedstorage.ClientLinkedStorageContents", "PacketHandler",
                "RequestLinkedStorageContentsMessage", "LinkedStorageContentsMessage"),
        BACKPACKS("net.p3pp3rf1y.sophisticatedbackpacks.",
                "backpack.wrapper.ClientLinkedStorageBackpackContents", "SBPPacketHandler",
                "RequestLinkedStorageBackpackContentsMessage", "LinkedStorageBackpackContentsMessage");

        final String contents;
        final String handler;
        final String request;
        final String snapshot;

        Protocol(String base, String contents, String handler, String request, String snapshot) {
            this.contents = base + contents;
            this.handler = base + "network." + handler;
            this.request = base + "network." + request;
            this.snapshot = base + "network." + snapshot;
        }

        boolean matches(ApiShapeProbe.ClassBytes source) {
            return ApiShapeProbe.satisfies(source, contents, List.of(
                    ApiShapeProbe.method("getRevision", "(Ljava/util/UUID;)Ljava/util/Optional;")))
                    && (ApiShapeProbe.satisfies(source, request, List.of(
                    ApiShapeProbe.method("<init>", "(Ljava/util/UUID;J)V")))
                    || (ApiShapeProbe.satisfies(source, request, List.of(
                    ApiShapeProbe.method("<init>", "(Ljava/util/UUID;JL" + REFERENCE.replace('.', '/') + ";)V")))
                    && ApiShapeProbe.satisfies(source, REFERENCE, List.of(
                    ApiShapeProbe.method("<init>", "(IILjava/lang/String;Ljava/lang/String;)V"),
                    ApiShapeProbe.field("EQUIPMENT_HANDLER", "I")))))
                    && ApiShapeProbe.satisfies(source, snapshot, List.of(
                    ApiShapeProbe.method("createSnapshot", "(Lnet/minecraft/server/level/ServerLevel;"
                            + "Ljava/util/UUID;)L" + snapshot.replace('.', '/') + ";")))
                    && ApiShapeProbe.satisfies(source, handler, List.of(
                    ApiShapeProbe.field("INSTANCE", "L" + handler.replace('.', '/') + ";"),
                    ApiShapeProbe.method("sendToServer", "(Ljava/lang/Object;)V"),
                    ApiShapeProbe.method("sendToClient",
                            "(Lnet/minecraft/server/level/ServerPlayer;Ljava/lang/Object;)V")));
        }

        static Protocol detect(ApiShapeProbe.ClassBytes source) {
            for (Protocol protocol : values()) {
                if (protocol.matches(source)) return protocol;
            }
            throw new LinkageError("Unrecognized Sophisticated linked-storage network API");
        }
    }

    // These holders bind only on first use. In particular, sending a server
    // snapshot must never initialize the upstream client cache.
    private static final class Network {
        static final Protocol PROTOCOL = Protocol.detect(
                ApiShapeProbe.sourceFor(SophisticatedLinkedStorageCompatNetwork.class));
        static final Constructor<?> REQUEST;
        static final Constructor<?> ITEM_REFERENCE;
        static final int EQUIPMENT;
        static final Method SNAPSHOT;
        static final Method SEND_SERVER;
        static final Method SEND_CLIENT;
        static final Object HANDLER;

        static {
            try {
                Class<?> handler = load(PROTOCOL.handler);
                Constructor<?> request;
                Constructor<?> reference = null;
                int equipment = 0;
                try {
                    request = load(PROTOCOL.request).getConstructor(UUID.class, long.class);
                } catch (NoSuchMethodException modern) {
                    Class<?> type = load(REFERENCE);
                    reference = type.getConstructor(int.class, int.class, String.class, String.class);
                    equipment = type.getField("EQUIPMENT_HANDLER").getInt(null);
                    request = load(PROTOCOL.request).getConstructor(UUID.class, long.class, type);
                }
                REQUEST = request;
                ITEM_REFERENCE = reference;
                EQUIPMENT = equipment;
                SNAPSHOT = load(PROTOCOL.snapshot).getMethod("createSnapshot", ServerLevel.class, UUID.class);
                SEND_SERVER = handler.getMethod("sendToServer", Object.class);
                SEND_CLIENT = handler.getMethod("sendToClient", ServerPlayer.class, Object.class);
                HANDLER = handler.getField("INSTANCE").get(null);
            } catch (ReflectiveOperationException error) {
                throw linkage(error);
            }
        }
    }

    private static final class Client {
        static final Method REVISION;

        static {
            try {
                REVISION = load(Network.PROTOCOL.contents).getMethod("getRevision", UUID.class);
            } catch (ReflectiveOperationException error) {
                throw linkage(error);
            }
        }
    }

    private SophisticatedLinkedStorageCompatNetwork() { }

    @SuppressWarnings("unchecked")
    static Optional<Long> getRevision(UUID groupId) {
        return (Optional<Long>) invoke(Client.REVISION, null, groupId);
    }

    static void request(UUID groupId, long revision) {
        request(groupId, revision, null, null, -1);
    }

    static void request(UUID groupId, long revision, String handlerName, String identifier, int slot) {
        try {
            Object message;
            if (Network.ITEM_REFERENCE == null) {
                message = Network.REQUEST.newInstance(groupId, revision);
            } else {
                // New servers require proof of a carried endpoint; never send a null reference.
                if (handlerName == null || identifier == null || slot < 0) return;
                Object reference = Network.ITEM_REFERENCE.newInstance(Network.EQUIPMENT, slot, handlerName, identifier);
                message = Network.REQUEST.newInstance(groupId, revision, reference);
            }
            invoke(Network.SEND_SERVER, Network.HANDLER, message);
        } catch (ReflectiveOperationException error) {
            throw linkage(error);
        }
    }

    static void sendSnapshot(ServerLevel level, ServerPlayer player, UUID groupId) {
        Object message = invoke(Network.SNAPSHOT, null, level, groupId);
        invoke(Network.SEND_CLIENT, Network.HANDLER, player, message);
    }

    private static Class<?> load(String name) throws ClassNotFoundException {
        return Class.forName(name, false, SophisticatedLinkedStorageCompatNetwork.class.getClassLoader());
    }

    private static Object invoke(Method method, Object receiver, Object... arguments) {
        try {
            return method.invoke(receiver, arguments);
        } catch (ReflectiveOperationException error) {
            throw linkage(error);
        }
    }

    private static LinkageError linkage(ReflectiveOperationException error) {
        if (error instanceof InvocationTargetException invocation) {
            Throwable cause = invocation.getCause();
            if (cause instanceof Error fatal) throw fatal;
            if (cause instanceof RuntimeException runtime) throw runtime;
        }
        return new LinkageError("Unable to link Sophisticated linked-storage network API", error);
    }
}
