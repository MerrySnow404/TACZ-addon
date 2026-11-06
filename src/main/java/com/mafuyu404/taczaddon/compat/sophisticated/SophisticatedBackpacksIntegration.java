package com.mafuyu404.taczaddon.compat.sophisticated;

import com.mafuyu404.taczaddon.inventory.CarriedInventorySource;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.items.IItemHandler;

import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * TACZAddon-owned contract for the Sophisticated Backpacks + Core runtime implementation.
 *
 * <p>This interface is the only handle the guarded {@link SophisticatedRuntime} and the public
 * facade ever use. It intentionally exposes no Sophisticated type in any parameter, return value,
 * field, or generic signature; only Minecraft, NeoForge, JDK, and TACZAddon types are allowed here.
 *
 * <p>All state transitions and failure containment are the responsibility of {@link
 * SophisticatedRuntime}; the implementation object itself is stateless with respect to capability
 * lifecycle.
 */
public interface SophisticatedBackpacksIntegration {

    /**
     * Verifies that the carried-backpack contract (including the cached reflective {@code
     * PlayerInventoryProvider} bridge) is usable.
     *
     * @return true when the capability may be marked READY
     */
    boolean probeCarriedBackpack();

    default List<CarriedInventorySource> resolveCarriedSources(ServerPlayer player) {
        return List.of();
    }

    /**
     * Verifies that the block-backpack class surface is loadable.
     *
     * @return true when the capability may be marked READY
     */
    boolean probeBlockBackpack();

    /**
     * Verifies the request-side classes used by the client cache refresh. The response-side mixin
     * contract is checked separately by the runtime.
     *
     * @return true when the request side may be marked READY
     */
    boolean probeClientSync();

    List<ItemStack> getItemsFromBackpackBlock(BlockPos blockPos, Player player);

    List<ItemStack> getItemsFromBackpackItem(ItemStack itemStack);

    List<ItemStack> getItemsFromInventoryBackpack(Player player);

    /**
     * Read-only traversal over every carried backpack visible through the player inventory
     * providers.
     *
     * <p>The visitor receives the fresh client-side inventory handler for each backpack. Returning
     * {@code true} from the visitor stops the enumeration.
     *
     * @return true when the visitor requested an early stop
     */
    boolean visitInventoryBackpacks(Player player, Predicate<IItemHandler> visitor);

    /**
     * Server-only mutation traversal over every carried backpack.
     *
     * <p>Changed handlers are saved and their authoritative contents are immediately pushed back to
     * the client. Returning {@code true} from the visitor stops the enumeration.
     *
     * @return true when the visitor requested an early stop
     */
    boolean mutateInventoryBackpacks(ServerPlayer player, Predicate<IItemHandler> visitor);

    /**
     * Client only. Requests authoritative inventory NBT for every carried backpack currently
     * visible through the player inventory providers.
     */
    void syncAllBackpack(Player player);

    /**
     * Refreshes the client-side {@code BackpackWrapper} whose storage UUID matches the payload that
     * just arrived.
     *
     * @return true when a matching wrapper was refreshed
     */
    boolean refreshInventoryBackpackWrapper(Player player, UUID backpackUuid);

    void modifyInventoryBackpack(
            ServerPlayer player, ItemStack backpackItem, Consumer<IItemHandler> action);

    void modifyBlockBackpack(ServerPlayer player, BlockPos blockPos, Consumer<IItemHandler> action);

    void forEachInventoryBackpackHandler(Player player, Consumer<IItemHandler> action);

    void forEachBlockBackpackHandler(
            Player player, BlockPos blockPos, Consumer<IItemHandler> action);

    boolean isBackpackBlock(Level level, BlockPos blockPos);

    int countInventoryBackpackAmmo(Player player, ItemStack gunStack);

    List<ItemStack> getAllInventoryBackpack(Player player);
}
