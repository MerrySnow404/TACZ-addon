package com.mafuyu404.taczaddon.compat.createstorage;

import com.mafuyu404.taczaddon.compat.api.ApiShapeProbe;
import com.mafuyu404.taczaddon.storage.api.BlockAccess;

import com.mojang.logging.LogUtils;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.items.IItemHandler;

import java.util.List;
import java.util.function.Predicate;

/** Optional boundary: no Create or Create Storage types may escape this class. */
public final class CreateStorageCompat {
    private static final String BASE = "net.fxnt.fxntstorage.";
    private static volatile boolean broken;

    private CreateStorageCompat() {}

    private static final class Support {
        static final boolean SUPPORTED = matches(ApiShapeProbe.sourceFor(CreateStorageCompat.class));
    }

    static boolean matches(ApiShapeProbe.ClassBytes source) {
        return ApiShapeProbe.satisfies(source, BASE + "backpack.inventory.BackpackContainer", List.of(
                ApiShapeProbe.method("<init>", "(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/item/ItemStack;)V"),
                ApiShapeProbe.method("getItemHandler", "()Lnet/minecraftforge/items/ItemStackHandler;"),
                ApiShapeProbe.method("saveItemsToStack", "()Lnet/minecraft/nbt/CompoundTag;")))
                && ApiShapeProbe.hasClass(source, BASE + "backpack.BackpackItem")
                && ApiShapeProbe.hasClass(source, BASE + "backpack.BackpackEntity")
                && ApiShapeProbe.satisfies(source, BASE + "backpack.inventory.BackpackSlotLayout", List.of(
                ApiShapeProbe.method("createLayout", "()Lnet/fxnt/fxntstorage/backpack/inventory/BackpackSlotLayout;"),
                ApiShapeProbe.method("items", "()Lnet/fxnt/fxntstorage/backpack/inventory/BackpackSlotLayout$SlotSection;"),
                ApiShapeProbe.method("tools", "()Lnet/fxnt/fxntstorage/backpack/inventory/BackpackSlotLayout$SlotSection;")))
                && ApiShapeProbe.satisfies(source, BASE + "backpack.inventory.BackpackSlotLayout$SlotSection", List.of(
                ApiShapeProbe.method("getStartIndex", "()I"), ApiShapeProbe.method("getCount", "()I"),
                ApiShapeProbe.method("contains", "(I)Z")))
                && ApiShapeProbe.satisfies(source, BASE + "controller.StorageControllerEntity", List.of(
                ApiShapeProbe.method("getConnectedNetwork", "()Lnet/fxnt/fxntstorage/storage_network/StorageNetwork;")))
                && ApiShapeProbe.satisfies(source, BASE + "controller.StorageInterfaceEntity", List.of(
                ApiShapeProbe.field("controller", "Lnet/fxnt/fxntstorage/controller/StorageControllerEntity;")))
                && ApiShapeProbe.satisfies(source, BASE + "storage_network.StorageNetwork", List.of(
                ApiShapeProbe.method("getBoxes", "()Ljava/util/List;"),
                ApiShapeProbe.method("getItemHandler", "()Lnet/minecraftforge/items/IItemHandlerModifiable;")))
                && ApiShapeProbe.satisfies(source, BASE + "storage_network.StorageNetwork$StorageNetworkItem", List.of(
                ApiShapeProbe.field("simpleStorageBoxEntity", "Lnet/fxnt/fxntstorage/simple_storage/SimpleStorageBoxEntity;")))
                && ApiShapeProbe.satisfies(source, BASE + "controller.StorageInterfaceFilteredEntity$FilteredItemHandler", List.of(
                ApiShapeProbe.field("filteredSlots", "Ljava/util/List;"),
                ApiShapeProbe.field("source", "Lnet/minecraftforge/items/IItemHandlerModifiable;")));
    }

    public static boolean isUsable() {
        var mods = ModList.get();
        return mods != null && !broken && mods.getModContainerById("fxntstorage")
                .map(mod -> supportsVersion(mod.getModInfo().getVersion().toString()))
                .orElse(false) && Support.SUPPORTED;
    }

    // Only the user-selected release is supported; matching shapes alone aren't a version promise.
    static boolean supportsVersion(String version) {
        return "1.2.7".equals(version);
    }

    public static boolean visitBackpacks(Player player, Predicate<IItemHandler> visitor) {
        if (player == null || !isUsable()) return false;
        try {
            return CreateStorageBackpacksCompatInner.visit(player, visitor, false);
        } catch (LinkageError error) {
            disable(error);
            return false;
        }
    }

    public static boolean mutateBackpacks(ServerPlayer player, Predicate<IItemHandler> visitor) {
        if (player == null || !isUsable()) return false;
        try {
            return CreateStorageBackpacksCompatInner.visit(player, visitor, true);
        } catch (LinkageError error) {
            disable(error);
            throw error; // A visitor may have already consumed ammunition.
        }
    }

    public static BlockAccess bind(BlockEntity block, IItemHandler handler) {
        if (block == null || handler == null) return null;
        // Only touch the optional backend for this mod's blocks.
        if (!block.getClass().getName().startsWith(BASE)) return null;
        if (!isUsable()) throw new IllegalStateException("Unsupported Create Storage API");
        return CreateStorageBlocksCompatInner.bind(block, handler);
    }

    private static void disable(LinkageError error) {
        if (!broken) LogUtils.getLogger().warn("Create Storage integration disabled after an API failure", error);
        broken = true;
    }
}
