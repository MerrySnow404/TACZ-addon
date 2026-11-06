package com.mafuyu404.taczaddon.compat;

import com.mafuyu404.taczaddon.compat.sophisticated.SophisticatedCapability;
import com.mafuyu404.taczaddon.compat.sophisticated.SophisticatedRuntime;
import com.mafuyu404.taczaddon.inventory.CarriedInventorySource;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.items.IItemHandler;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * The only public Sophisticated Backpacks facade used by the rest of TACZAddon.
 *
 * <p>Every operation is routed through the {@link SophisticatedRuntime} capability guard, which
 * returns neutral fallbacks for ABSENT/BROKEN capabilities and contains linkage failures per
 * capability. No Sophisticated class is referenced from this facade or from
 * gameplay/event/network/cache code that calls it.
 */
public final class SophisticatedBackpacksCompat {
    private static final String MOD_ID = "sophisticatedbackpacks";

    private SophisticatedBackpacksCompat() {}

    private static SophisticatedRuntime runtime() {
        return SophisticatedRuntime.get();
    }

    public static boolean isInstalled() {
        ModList modList = ModList.get();
        return modList != null && modList.isLoaded(MOD_ID);
    }

    public static java.util.List<CarriedInventorySource> resolveCarriedSources(
            ServerPlayer player) {
        return runtime()
                .call(
                        SophisticatedCapability.CARRIED_BACKPACK,
                        java.util.List::of,
                        integration -> integration.resolveCarriedSources(player));
    }

    public static void init() {
        // Runs the one-time environment checks and loads the integration
        // implementation when the dependency group is present. Capability
        // probes stay lazy until first guarded use.
        runtime().ensureInitialized();
    }

    public static List<ItemStack> getItemsFromBackpackBlock(BlockPos blockPos, Player player) {
        return runtime()
                .call(
                        SophisticatedCapability.BLOCK_BACKPACK,
                        ArrayList::new,
                        integration -> integration.getItemsFromBackpackBlock(blockPos, player));
    }

    public static List<ItemStack> getItemsFromBackpackItem(ItemStack itemStack) {
        return runtime()
                .call(
                        SophisticatedCapability.CARRIED_BACKPACK,
                        ArrayList::new,
                        integration -> integration.getItemsFromBackpackItem(itemStack));
    }

    public static List<ItemStack> getItemsFromInventoryBackpack(Player player) {
        return runtime()
                .call(
                        SophisticatedCapability.CARRIED_BACKPACK,
                        ArrayList::new,
                        integration -> integration.getItemsFromInventoryBackpack(player));
    }

    /**
     * Read-only traversal over every carried backpack. The visitor may stop the enumeration by
     * returning {@code true}.
     */
    public static boolean visitInventoryBackpacks(Player player, Predicate<IItemHandler> visitor) {
        if (player == null || visitor == null) {
            return false;
        }

        return runtime()
                .call(
                        SophisticatedCapability.CARRIED_BACKPACK,
                        () -> false,
                        integration -> integration.visitInventoryBackpacks(player, visitor));
    }

    /**
     * Server-only mutation traversal over every carried backpack. The visitor may stop the
     * enumeration by returning {@code true}.
     *
     * <p>Changed backpack handlers are saved and their authoritative contents are immediately
     * pushed back to the client inside the integration.
     */
    public static boolean mutateInventoryBackpacks(
            ServerPlayer player, Predicate<IItemHandler> visitor) {
        if (player == null || visitor == null) {
            return false;
        }

        return runtime()
                .mutate(
                        SophisticatedCapability.CARRIED_BACKPACK,
                        () -> false,
                        integration -> integration.mutateInventoryBackpacks(player, visitor));
    }

    public static void syncAllBackpack(Player player) {
        runtime()
                .run(
                        SophisticatedCapability.CLIENT_SYNC,
                        integration -> integration.syncAllBackpack(player));
    }

    /**
     * Refreshes the client-side wrapper whose storage UUID matches the BackpackContentsPayload
     * response that just arrived.
     *
     * @return true when a matching carried backpack wrapper was refreshed
     */
    public static boolean refreshInventoryBackpackWrapper(Player player, UUID backpackUuid) {
        return runtime()
                .call(
                        SophisticatedCapability.CLIENT_SYNC,
                        () -> false,
                        integration ->
                                integration.refreshInventoryBackpackWrapper(player, backpackUuid));
    }

    public static void modifyInventoryBackpack(
            ServerPlayer player, ItemStack backpackItem, Consumer<IItemHandler> action) {
        runtime()
                .run(
                        SophisticatedCapability.CARRIED_BACKPACK,
                        integration ->
                                integration.modifyInventoryBackpack(player, backpackItem, action));
    }

    public static void modifyBlockBackpack(
            ServerPlayer player, BlockPos blockPos, Consumer<IItemHandler> action) {
        runtime()
                .run(
                        SophisticatedCapability.BLOCK_BACKPACK,
                        integration -> integration.modifyBlockBackpack(player, blockPos, action));
    }

    public static void forEachInventoryBackpackHandler(
            Player player, Consumer<IItemHandler> action) {
        runtime()
                .run(
                        SophisticatedCapability.CARRIED_BACKPACK,
                        integration -> integration.forEachInventoryBackpackHandler(player, action));
    }

    public static void forEachBlockBackpackHandler(
            Player player, BlockPos blockPos, Consumer<IItemHandler> action) {
        runtime()
                .run(
                        SophisticatedCapability.BLOCK_BACKPACK,
                        integration ->
                                integration.forEachBlockBackpackHandler(player, blockPos, action));
    }

    public static boolean isBackpackBlock(Level level, BlockPos blockPos) {
        return runtime()
                .call(
                        SophisticatedCapability.BLOCK_BACKPACK,
                        () -> false,
                        integration -> integration.isBackpackBlock(level, blockPos));
    }

    public static int countInventoryBackpackAmmo(Player player, ItemStack gunStack) {
        if (player == null || gunStack.isEmpty()) {
            return 0;
        }

        return runtime()
                .call(
                        SophisticatedCapability.CARRIED_BACKPACK,
                        () -> 0,
                        integration -> integration.countInventoryBackpackAmmo(player, gunStack));
    }

    public static List<ItemStack> getAllInventoryBackpack(Player player) {
        return runtime()
                .call(
                        SophisticatedCapability.CARRIED_BACKPACK,
                        ArrayList::new,
                        integration -> integration.getAllInventoryBackpack(player));
    }
}
