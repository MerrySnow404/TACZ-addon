package com.mafuyu404.taczaddon.compat.sophisticated;

import static com.mafuyu404.taczaddon.compat.sophisticated.SophisticatedBackpackMutations.mutateBackpackHandler;
import static com.mafuyu404.taczaddon.compat.sophisticated.SophisticatedContentsSync.syncBackpackContents;
import static com.mafuyu404.taczaddon.compat.sophisticated.SophisticatedInventoryAccess.getFreshInventoryHandler;

import com.mafuyu404.taczaddon.inventory.CarriedInventorySource;
import com.tacz.guns.api.item.IAmmo;
import com.tacz.guns.api.item.IAmmoBox;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.items.IItemHandler;
import net.p3pp3rf1y.sophisticatedbackpacks.backpack.BackpackBlockEntity;
import net.p3pp3rf1y.sophisticatedbackpacks.backpack.BackpackItem;
import net.p3pp3rf1y.sophisticatedbackpacks.backpack.wrapper.BackpackLinkedStorageHostWrapper;
import net.p3pp3rf1y.sophisticatedbackpacks.backpack.wrapper.BackpackWrapper;
import net.p3pp3rf1y.sophisticatedbackpacks.backpack.wrapper.IBackpackWrapper;
import net.p3pp3rf1y.sophisticatedbackpacks.common.gui.BackpackContext;
import net.p3pp3rf1y.sophisticatedbackpacks.network.RequestBackpackInventoryContentsPayload;
import net.p3pp3rf1y.sophisticatedcore.init.ModCoreDataComponents;
import net.p3pp3rf1y.sophisticatedcore.linkedstorage.LinkedStorageEndpointData;
import net.p3pp3rf1y.sophisticatedcore.linkedstorage.LinkedStorageEndpointStackState;
import net.p3pp3rf1y.sophisticatedcore.linkedstorage.LinkedStorageStackLifecycle;
import net.p3pp3rf1y.sophisticatedcore.network.ItemStackReference;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Optional runtime adapter coordinating Sophisticated inventory access, mutations and contents
 * sync.
 *
 * <p>This class is intentionally never referenced by class literal or constructor reference from
 * the safe facade or runtime. It is loaded by name only after the optional dependency group has
 * been confirmed present, which keeps optional classes out of the ordinary TACZAddon class-loading
 * path.
 *
 * <p>Resolves the provider ABI through {@link PlayerInventoryProviderBridge}; remaining APIs are
 * protected by the per-capability runtime guard.
 *
 * <p>Carried backpacks are always resolved through {@code BackpackContext.Item} built from the
 * provider location (inventory handler name, identifier, slot). The exact stack/location supplied
 * by {@code PlayerInventoryProvider} is authoritative; defensive stack copies are never used as
 * wrapper identity.
 *
 * <p>Read traversal self-heals stale client-side wrapper handlers by comparing the handler's
 * serialized inventory against the synchronized {@code BackpackStorage} NBT. Server mutation
 * snapshots every slot, detects both {@code extractItem} and in-place ammo-box modifications,
 * persists the changed handler and immediately pushes the authoritative contents payload to the
 * client.
 */
public final class SophisticatedBackpacksIntegrationImpl
        implements SophisticatedBackpacksIntegration {

    /** One traversal, with separate namespaces for ordinary storage and linked groups. */
    static final class InventoryVisits {
        private record Key(boolean linked, UUID id) {}

        private final Set<Key> ids = new HashSet<>();
        private final Set<IBackpackWrapper> anonymous =
                java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());

        boolean first(IBackpackWrapper wrapper) {
            return wrapper.getContentsUuid()
                    .map(
                            id ->
                                    ids.add(
                                            new Key(
                                                    wrapper
                                                            instanceof
                                                            BackpackLinkedStorageHostWrapper,
                                                    id)))
                    .orElseGet(() -> anonymous.add(wrapper));
        }
    }

    private static final int MAX_AMMO_COUNT = 9999;

    @Override
    public boolean probeCarriedBackpack() {
        bridge();
        checkClass("net.p3pp3rf1y.sophisticatedbackpacks.backpack." + "BackpackItem");
        checkClass("net.p3pp3rf1y.sophisticatedbackpacks.backpack.wrapper." + "BackpackWrapper");
        checkClass("net.p3pp3rf1y.sophisticatedbackpacks.backpack.wrapper." + "IBackpackWrapper");
        checkClass("net.p3pp3rf1y.sophisticatedbackpacks.common.gui." + "BackpackContext$Item");
        checkClass("net.p3pp3rf1y.sophisticatedbackpacks.backpack." + "BackpackStorage");
        checkClass("net.p3pp3rf1y.sophisticatedcore.inventory." + "InventoryHandler");
        checkClass("net.p3pp3rf1y.sophisticatedcore.upgrades." + "UpgradeHandler");
        return true;
    }

    @Override
    public boolean probeBlockBackpack() {
        checkClass("net.p3pp3rf1y.sophisticatedbackpacks.common.gui." + "BackpackContext$Block");
        checkClass("net.p3pp3rf1y.sophisticatedbackpacks.backpack.wrapper." + "IBackpackWrapper");
        checkClass(
                "net.p3pp3rf1y.sophisticatedbackpacks.backpack.wrapper." + "IBackpackWrapper$Noop");
        checkClass("net.p3pp3rf1y.sophisticatedbackpacks.backpack." + "BackpackBlockEntity");
        return true;
    }

    @Override
    public boolean probeClientSync() {
        bridge();
        checkClass(
                "net.p3pp3rf1y.sophisticatedbackpacks.network."
                        + "RequestBackpackInventoryContentsPayload");
        checkClass("net.p3pp3rf1y.sophisticatedbackpacks.network." + "BackpackContentsPayload");
        checkClass("net.p3pp3rf1y.sophisticatedbackpacks.backpack.wrapper." + "BackpackWrapper");
        checkClass("net.p3pp3rf1y.sophisticatedcore.init." + "ModCoreDataComponents");
        Objects.requireNonNull(ModCoreDataComponents.STORAGE_UUID.get());
        Objects.requireNonNull(ModCoreDataComponents.LINKED_STORAGE_ENDPOINT.get());

        // Exercise the exact typed members used by bootstrap, without sending
        // packets or resolving a wrapper before its native snapshot exists.
        UUID probeId = new UUID(0L, 0L);
        Objects.requireNonNull(
                new RequestBackpackInventoryContentsPayload(
                                probeId,
                                new ItemStackReference(
                                        ItemStackReference.EQUIPMENT_HANDLER, 0, "main", ""))
                        .type());
        Objects.requireNonNull(
                LinkedStorageRequestBridge.create(
                                probeId,
                                -1L,
                                new ItemStackReference(
                                        ItemStackReference.EQUIPMENT_HANDLER, 0, "main", ""))
                        .type());
        Objects.requireNonNull(LinkedStorageStackLifecycle.classifyEndpoint(ItemStack.EMPTY));
        Objects.requireNonNull(LinkedStorageEndpointStackState.ENDPOINT);
        Objects.requireNonNull(new LinkedStorageEndpointData(probeId, probeId).groupId());
        return true;
    }

    @Override
    public List<ItemStack> getItemsFromBackpackBlock(BlockPos blockPos, Player player) {
        List<ItemStack> items = new ArrayList<>();

        BackpackContext.Block context = new BackpackContext.Block(blockPos);
        IBackpackWrapper wrapper = context.getBackpackWrapper(player);

        if (wrapper == IBackpackWrapper.Noop.INSTANCE) {
            return items;
        }

        addHandlerItems(items, wrapper.getInventoryHandler());
        return items;
    }

    @Override
    public int countInventoryBackpackAmmo(Player player, ItemStack gunStack) {
        if (player == null || gunStack.isEmpty()) {
            return 0;
        }

        int[] total = {0};

        visitInventoryBackpacks(
                player,
                handler -> {
                    for (int slot = 0; slot < handler.getSlots(); slot++) {
                        ItemStack candidate = handler.getStackInSlot(slot);

                        if (candidate.isEmpty()) {
                            continue;
                        }

                        if (candidate.getItem() instanceof IAmmo ammo) {
                            if (ammo.isAmmoOfGun(gunStack, candidate)) {
                                total[0] = addAmmoSafely(total[0], candidate.getCount());
                            }
                        }

                        if (candidate.getItem() instanceof IAmmoBox ammoBox) {
                            if (!ammoBox.isAmmoBoxOfGun(gunStack, candidate)) {
                                continue;
                            }

                            if (ammoBox.isAllTypeCreative(candidate)
                                    || ammoBox.isCreative(candidate)) {
                                total[0] = MAX_AMMO_COUNT;
                                return true;
                            }

                            total[0] = addAmmoSafely(total[0], ammoBox.getAmmoCount(candidate));
                        }

                        if (total[0] >= MAX_AMMO_COUNT) {
                            return true;
                        }
                    }

                    return false;
                });

        return total[0];
    }

    private static int addAmmoSafely(int current, int amount) {
        if (amount <= 0) {
            return current;
        }

        long result = (long) current + amount;

        return result >= MAX_AMMO_COUNT ? MAX_AMMO_COUNT : (int) result;
    }

    @Override
    public List<CarriedInventorySource> resolveCarriedSources(ServerPlayer player) {
        var result = new ArrayList<CarriedInventorySource>();
        InventoryVisits seen = new InventoryVisits();
        bridge().forEachBackpack(
                        player,
                        (backpack, name, identifier, slot) -> {
                            BackpackContext.Item context =
                                    new BackpackContext.Item(name, identifier, slot);
                            if (!context.canInteractWith(player)) return false;
                            IBackpackWrapper wrapper = context.getBackpackWrapper(player);
                            if (wrapper == IBackpackWrapper.Noop.INSTANCE) return false;
                            UUID id = wrapper.getContentsUuid().orElse(null);
                            if (id == null || !seen.first(wrapper)) return false;
                            var handler = wrapper.getInventoryHandler();
                            result.add(
                                    new CarriedInventorySource(
                                            name,
                                            String.valueOf(identifier),
                                            slot,
                                            id,
                                            handler,
                                            () ->
                                                    context.canInteractWith(player)
                                                            && context.getBackpackWrapper(player)
                                                                    == wrapper,
                                            () -> {
                                                handler.saveInventory();
                                                player.getInventory().setChanged();
                                                player.containerMenu.broadcastChanges();
                                                syncBackpackContents(player, wrapper);
                                            }));
                            return false;
                        });
        return List.copyOf(result);
    }

    @Override
    public boolean visitInventoryBackpacks(Player player, Predicate<IItemHandler> visitor) {
        if (player == null || visitor == null) {
            return false;
        }

        boolean[] stopped = {false};
        InventoryVisits seen = new InventoryVisits();

        bridge().forEachBackpack(
                        player,
                        (backpack, inventoryHandlerName, identifier, slot) -> {
                            BackpackContext.Item context =
                                    new BackpackContext.Item(
                                            inventoryHandlerName, identifier, slot);

                            IBackpackWrapper wrapper = context.getBackpackWrapper(player);

                            if (wrapper == IBackpackWrapper.Noop.INSTANCE || !seen.first(wrapper)) {
                                return false;
                            }

                            IItemHandler handler = getFreshInventoryHandler(player, wrapper);

                            if (visitor.test(handler)) {
                                stopped[0] = true;
                                return true;
                            }

                            return false;
                        });

        return stopped[0];
    }

    @Override
    public boolean mutateInventoryBackpacks(ServerPlayer player, Predicate<IItemHandler> visitor) {
        if (player == null || visitor == null) {
            return false;
        }

        boolean[] stopped = {false};
        InventoryVisits seen = new InventoryVisits();

        bridge().forEachBackpack(
                        player,
                        (backpack, inventoryHandlerName, identifier, slot) -> {
                            BackpackContext.Item context =
                                    new BackpackContext.Item(
                                            inventoryHandlerName, identifier, slot);

                            if (!context.canInteractWith(player)) {
                                return false;
                            }

                            IBackpackWrapper wrapper = context.getBackpackWrapper(player);

                            if (wrapper == IBackpackWrapper.Noop.INSTANCE || !seen.first(wrapper)) {
                                return false;
                            }

                            IItemHandler handler = wrapper.getInventoryHandler();

                            boolean stop = mutateBackpackHandler(player, wrapper, handler, visitor);

                            if (stop) {
                                stopped[0] = true;
                            }

                            return stop;
                        });

        return stopped[0];
    }

    @Override
    public void modifyBlockBackpack(
            ServerPlayer player, BlockPos blockPos, Consumer<IItemHandler> action) {
        forEachBlockBackpackHandler(player, blockPos, action);
    }

    @Override
    public void forEachBlockBackpackHandler(
            Player player, BlockPos blockPos, Consumer<IItemHandler> action) {
        BackpackContext.Block context = new BackpackContext.Block(blockPos);
        IBackpackWrapper wrapper = context.getBackpackWrapper(player);

        if (wrapper != IBackpackWrapper.Noop.INSTANCE) {
            action.accept(wrapper.getInventoryHandler());
        }
    }

    @Override
    public List<ItemStack> getItemsFromBackpackItem(ItemStack itemStack) {
        List<ItemStack> items = new ArrayList<>();

        if (itemStack.isEmpty() || !(itemStack.getItem() instanceof BackpackItem)) {
            return items;
        }

        /*
         * This method expects the actual backpack stack, not a defensive
         * copy. fromExistingData uses the ItemStack-keyed
         * StorageWrapperRepository and may return empty for copied stacks.
         */
        BackpackWrapper.fromExistingData(itemStack)
                .ifPresent(wrapper -> addHandlerItems(items, wrapper.getInventoryHandler()));

        return items;
    }

    @Override
    public List<ItemStack> getItemsFromInventoryBackpack(Player player) {
        List<ItemStack> items = new ArrayList<>();

        visitInventoryBackpacks(
                player,
                handler -> {
                    addHandlerItems(items, handler);
                    return false;
                });

        return items;
    }

    /**
     * Requests authoritative inventory NBT for every backpack currently accessible through
     * Sophisticated Backpacks' player inventory providers.
     *
     * <p>Client side only.
     */
    @Override
    public void syncAllBackpack(Player player) {
        SophisticatedContentsSync.syncAllBackpack(bridge(), player);
    }

    @javax.annotation.Nullable

    /**
     * Called after BackpackContentsPayload has written the received NBT into the client-side
     * BackpackStorage.
     *
     * <p>Returns true when a carried backpack with the UUID was found.
     */
    @Override
    public boolean refreshInventoryBackpackWrapper(Player player, UUID updatedUuid) {
        return SophisticatedContentsSync.refreshInventoryBackpackWrapper(
                bridge(), player, updatedUuid);
    }

    @Override
    public void modifyInventoryBackpack(
            ServerPlayer player, ItemStack backpackItem, Consumer<IItemHandler> action) {
        bridge().forEachBackpack(
                        player,
                        (backpack, inventoryName, identifier, index) -> {
                            if (!ItemStack.isSameItemSameComponents(backpack, backpackItem)) {
                                return false;
                            }

                            IBackpackWrapper wrapper = BackpackWrapper.fromStack(backpack);

                            action.accept(wrapper.getInventoryHandler());
                            return false;
                        });
    }

    @Override
    public void forEachInventoryBackpackHandler(Player player, Consumer<IItemHandler> action) {
        if (player == null || action == null) {
            return;
        }

        visitInventoryBackpacks(
                player,
                handler -> {
                    action.accept(handler);
                    return false;
                });
    }

    /**
     * Returns defensive copies for callers that only need backpack item snapshots. Do not use these
     * copies for StorageWrapperRepository lookups.
     */
    @Override
    public List<ItemStack> getAllInventoryBackpack(Player player) {
        List<ItemStack> items = new ArrayList<>();

        bridge().forEachBackpack(
                        player,
                        (backpack, inventoryName, identifier, index) -> {
                            items.add(backpack.copy());
                            return false;
                        });

        return items;
    }

    @Override
    public boolean isBackpackBlock(Level level, BlockPos blockPos) {
        return level.isLoaded(blockPos)
                && level.getBlockEntity(blockPos) instanceof BackpackBlockEntity;
    }

    private PlayerInventoryProviderBridge playerBridge;

    private PlayerInventoryProviderBridge bridge() {
        if (playerBridge == null) playerBridge = PlayerInventoryProviderBridge.createDefault();
        return playerBridge;
    }

    private static void checkClass(String className) {
        try {
            Class.forName(
                    className, false, SophisticatedBackpacksIntegrationImpl.class.getClassLoader());
        } catch (ReflectiveOperationException | LinkageError exception) {
            throw new SophisticatedCompatibilityException(
                    "Sophisticated class contract missing: " + className, exception);
        }
    }

    private static void addHandlerItems(List<ItemStack> items, IItemHandler handler) {
        for (int slot = 0; slot < handler.getSlots(); slot++) {
            ItemStack stack = handler.getStackInSlot(slot);

            if (!stack.isEmpty()) {
                items.add(stack.copy());
            }
        }
    }
}
