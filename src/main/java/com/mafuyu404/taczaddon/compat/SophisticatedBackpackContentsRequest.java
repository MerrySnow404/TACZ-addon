package com.mafuyu404.taczaddon.compat;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.List;
import java.util.UUID;

/** Adapts the ownership reference added to ordinary backpack requests. */
final class SophisticatedBackpackContentsRequest {
    static final String MESSAGE = "net.p3pp3rf1y.sophisticatedbackpacks.network.RequestBackpackInventoryContentsMessage";
    private static final String REFERENCE = "net.p3pp3rf1y.sophisticatedcore.network.ItemStackReference";

    static boolean matches(ApiShapeProbe.ClassBytes source) {
        return legacy(source) || (ApiShapeProbe.satisfies(source, MESSAGE, List.of(
                ApiShapeProbe.method("<init>", "(Ljava/util/UUID;L" + REFERENCE.replace('.', '/') + ";)V")))
                && ApiShapeProbe.satisfies(source, REFERENCE, List.of(
                ApiShapeProbe.method("<init>", "(IILjava/lang/String;Ljava/lang/String;)V"),
                ApiShapeProbe.field("EQUIPMENT_HANDLER", "I"))));
    }

    private static boolean legacy(ApiShapeProbe.ClassBytes source) {
        return ApiShapeProbe.satisfies(source, MESSAGE, List.of(
                ApiShapeProbe.method("<init>", "(Ljava/util/UUID;)V")));
    }

    private static final class Binding {
        static final Constructor<?> MESSAGE_CTOR;
        static final Constructor<?> REFERENCE_CTOR;
        static final int EQUIPMENT;

        static {
            try {
                ClassLoader loader = SophisticatedBackpackContentsRequest.class.getClassLoader();
                Class<?> message = Class.forName(MESSAGE, false, loader);
                if (legacy(ApiShapeProbe.sourceFor(SophisticatedBackpackContentsRequest.class))) {
                    MESSAGE_CTOR = message.getConstructor(UUID.class);
                    REFERENCE_CTOR = null;
                    EQUIPMENT = 0;
                } else {
                    Class<?> reference = Class.forName(REFERENCE, false, loader);
                    REFERENCE_CTOR = reference.getConstructor(int.class, int.class, String.class, String.class);
                    EQUIPMENT = reference.getField("EQUIPMENT_HANDLER").getInt(null);
                    MESSAGE_CTOR = message.getConstructor(UUID.class, reference);
                }
            } catch (ReflectiveOperationException error) {
                throw new LinkageError("Unsupported backpack contents request API", error);
            }
        }
    }

    static Object create(UUID uuid, String handlerName, String identifier, int slot) {
        try {
            if (Binding.REFERENCE_CTOR == null) return Binding.MESSAGE_CTOR.newInstance(uuid);
            // Use the exact PlayerInventoryProvider location, including Curios handlers.
            Object reference = Binding.REFERENCE_CTOR.newInstance(Binding.EQUIPMENT, slot, handlerName, identifier);
            return Binding.MESSAGE_CTOR.newInstance(uuid, reference);
        } catch (InvocationTargetException error) {
            if (error.getCause() instanceof Error fatal) throw fatal;
            if (error.getCause() instanceof RuntimeException runtime) throw runtime;
            throw new LinkageError("Backpack contents request construction failed", error);
        } catch (ReflectiveOperationException error) {
            throw new LinkageError("Unable to construct backpack contents request", error);
        }
    }

    private SophisticatedBackpackContentsRequest() {}
}
