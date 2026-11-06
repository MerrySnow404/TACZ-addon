package com.mafuyu404.taczaddon.compat;

import com.mafuyu404.taczaddon.inventory.ItemStackData;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.IAttachment;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.api.item.builder.AttachmentItemBuilder;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.lang.reflect.Method;
import java.util.*;

/**
 * The official OEM API is absent from the original 1.1.8 port. Resolve only this small boundary.
 */
public final class TaczOemCompat {
    private static final Class<?> OEM = findOem();
    private static final Method ALLOWED =
            method(
                    "isAllowedVirtualOem",
                    ResourceLocation.class,
                    ResourceLocation.class,
                    AttachmentType.class);
    private static final Method SHOW =
            method(
                    "shouldShowIcon",
                    ResourceLocation.class,
                    ResourceLocation.class,
                    AttachmentType.class);
    private static final Method BUILTIN = method("isBuiltinAttachment", ResourceLocation.class);

    private TaczOemCompat() {}

    private static Class<?> findOem() {
        try {
            return Class.forName(
                    "com.tacz.guns.util.VirtualOemAttachment",
                    false,
                    TaczOemCompat.class.getClassLoader());
        } catch (ClassNotFoundException absentOnLegacy) {
            return null;
        }
    }

    private static Method method(String name, Class<?>... parameters) {
        if (OEM == null) return null;
        try {
            return OEM.getMethod(name, parameters);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Unsupported TaCZ OEM API: " + name, e);
        }
    }

    private static boolean query(Method method, Object... arguments) {
        if (method == null) return false;
        try {
            return (boolean) method.invoke(null, arguments);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Cannot invoke TaCZ OEM API", e);
        }
    }

    public static boolean isAvailable() {
        return OEM != null;
    }

    public static boolean isBuiltin(ItemStack stack) {
        IAttachment attachment = IAttachment.getIAttachmentOrNull(stack);
        return attachment != null && query(BUILTIN, attachment.getAttachmentId(stack));
    }

    public static Optional<ItemStack> createAllowed(ItemStack gunStack, ResourceLocation id) {
        IGun gun = IGun.getIGunOrNull(gunStack);
        if (OEM == null || gun == null) return Optional.empty();
        var index = TimelessAPI.getCommonAttachmentIndex(id);
        if (index.isEmpty() || !query(ALLOWED, gun.getGunId(gunStack), id, index.get().getType()))
            return Optional.empty();
        ItemStack stack = AttachmentItemBuilder.create().setId(id).build();
        if (!gun.allowAttachment(gunStack, stack)) return Optional.empty();
        ItemStackData.updateCustomData(stack, tag -> tag.putBoolean("VirtualOemAttachment", true));
        return Optional.of(stack);
    }

    public static List<ItemStack> visibleAttachments(
            ItemStack gunStack, AttachmentType type, Set<ResourceLocation> excluded) {
        IGun gun = IGun.getIGunOrNull(gunStack);
        if (OEM == null || gun == null) return List.of();
        return TimelessAPI.getAllCommonAttachmentIndex().stream()
                .filter(entry -> entry.getValue().getType() == type)
                .filter(
                        entry ->
                                !excluded.contains(entry.getKey())
                                        && !entry.getKey()
                                                .equals(gun.getAttachmentId(gunStack, type)))
                .filter(entry -> query(SHOW, gun.getGunId(gunStack), entry.getKey(), type))
                .sorted(Comparator.comparingInt(entry -> entry.getValue().getSort()))
                .map(entry -> createAllowed(gunStack, entry.getKey()))
                .flatMap(Optional::stream)
                .toList();
    }

    public static void triggerRefit(ServerPlayer player, ItemStack before, ItemStack after) {
        if (OEM == null) return;
        Trigger.fire(player, before, after);
    }

    private static final class Trigger {
        private static final Object INSTANCE;
        private static final Method FIRE;

        static {
            try {
                Class<?> type = Class.forName("com.tacz.guns.advancement.GunRefitTrigger");
                INSTANCE = type.getField("INSTANCE").get(null);
                FIRE =
                        type.getMethod(
                                "trigger", ServerPlayer.class, ItemStack.class, ItemStack.class);
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException("Unsupported TaCZ refit advancement API", e);
            }
        }

        static void fire(ServerPlayer player, ItemStack before, ItemStack after) {
            try {
                FIRE.invoke(INSTANCE, player, before, after);
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException("Cannot trigger TaCZ refit advancement", e);
            }
        }
    }
}
