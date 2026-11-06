package com.mafuyu404.taczaddon.inventory;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.BlockCapabilityCache;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.IItemHandlerModifiable;

import java.util.function.Function;

/** Request-scoped endpoint retaining source validation and exact-slot restoration. */
final class BlockInventoryHandler implements IItemHandlerModifiable {

    private enum BackendKind {
        BLOCK_CAPABILITY,
        CONTAINER,
        SOPHISTICATED_BACKPACK
    }

    private final ServerPlayer player;
    private final BlockPos pos;
    private final BlockState expectedState;
    private final BlockEntity expectedBlockEntity;

    private final BackendKind kind;
    private final IItemHandler expectedHandler;

    private final BlockCapabilityCache<IItemHandler, Direction> capabilityCache;

    private BlockInventoryHandler(
            ServerPlayer player,
            BlockPos pos,
            BlockState expectedState,
            BlockEntity expectedBlockEntity,
            BackendKind kind,
            IItemHandler expectedHandler,
            BlockCapabilityCache<IItemHandler, Direction> capabilityCache) {
        this.player = player;
        this.pos = pos.immutable();
        this.expectedState = expectedState;
        this.expectedBlockEntity = expectedBlockEntity;
        this.kind = kind;
        this.expectedHandler = expectedHandler;
        this.capabilityCache = capabilityCache;
    }

    static BlockInventoryHandler forCapability(
            ServerPlayer player,
            BlockPos pos,
            BlockState expectedState,
            BlockEntity expectedBlockEntity) {
        BlockCapabilityCache<IItemHandler, Direction> cache =
                BlockCapabilityCache.create(
                        Capabilities.ItemHandler.BLOCK, player.serverLevel(), pos, null);

        IItemHandler handler = cache.getCapability();

        if (handler == null) {
            return null;
        }

        return new BlockInventoryHandler(
                player,
                pos,
                expectedState,
                expectedBlockEntity,
                BackendKind.BLOCK_CAPABILITY,
                handler,
                cache);
    }

    static BlockInventoryHandler forContainer(
            ServerPlayer player,
            BlockPos pos,
            BlockState expectedState,
            BlockEntity expectedBlockEntity,
            IItemHandler handler) {
        return new BlockInventoryHandler(
                player,
                pos,
                expectedState,
                expectedBlockEntity,
                BackendKind.CONTAINER,
                handler,
                null);
    }

    static BlockInventoryHandler forSophisticated(
            ServerPlayer player,
            BlockPos pos,
            BlockState expectedState,
            BlockEntity expectedBlockEntity,
            IItemHandler handler) {
        return new BlockInventoryHandler(
                player,
                pos,
                expectedState,
                expectedBlockEntity,
                BackendKind.SOPHISTICATED_BACKPACK,
                handler,
                null);
    }

    boolean isValid() {
        if (!basePositionValid()) {
            return false;
        }

        return currentHandler() != null;
    }

    private boolean basePositionValid() {
        ServerLevel level = player.serverLevel();

        if (!level.isLoaded(pos)) {
            return false;
        }

        if (!expectedState.equals(level.getBlockState(pos))) {
            return false;
        }

        BlockEntity currentBlockEntity = level.getBlockEntity(pos);

        if (currentBlockEntity != expectedBlockEntity) {
            return false;
        }

        return expectedBlockEntity == null || !expectedBlockEntity.isRemoved();
    }

    private IItemHandler currentHandler() {
        if (!basePositionValid()) {
            return null;
        }

        return switch (kind) {
            case BLOCK_CAPABILITY -> {
                if (capabilityCache == null) {
                    yield null;
                }

                IItemHandler current = capabilityCache.getCapability();

                /*
                 * A capability invalidation that replaces the handler
                 * invalidates this request-scoped source. A new resolve()
                 * will bind the replacement on the next operation.
                 */
                yield current == expectedHandler ? current : null;
            }

            case CONTAINER -> expectedHandler;

            case SOPHISTICATED_BACKPACK -> findExpectedSophisticatedHandler();
        };
    }

    private IItemHandler findExpectedSophisticatedHandler() {
        IItemHandler[] matched = new IItemHandler[1];

        InventoryIntegrations.forEachBlockBackpackHandler(
                player,
                pos,
                handler -> {
                    if (handler == expectedHandler) {
                        matched[0] = handler;
                    }
                });

        return matched[0];
    }

    Object backendIdentity() {
        if (kind == BackendKind.CONTAINER && expectedBlockEntity != null) {
            return expectedBlockEntity;
        }

        return expectedHandler;
    }

    private <T> T access(Function<IItemHandler, T> action) {
        IItemHandler handler = currentHandler();

        if (handler == null) {
            throw new IllegalStateException("Nearby inventory source changed: " + pos);
        }

        return action.apply(handler);
    }

    @Override
    public int getSlots() {
        return access(IItemHandler::getSlots);
    }

    @Override
    public ItemStack getStackInSlot(int slot) {
        return access(
                handler -> {
                    if (slot < 0 || slot >= handler.getSlots()) {
                        return ItemStack.EMPTY;
                    }

                    ItemStack stack = handler.getStackInSlot(slot);

                    /*
                     * For a generic non-modifiable handler, do not expose an
                     * extraction-only/output slot to crafting/refit. Such a slot
                     * cannot be guaranteed rollback-safe.
                     */
                    if (!(handler instanceof IItemHandlerModifiable)
                            && !stack.isEmpty()
                            && !handler.isItemValid(slot, stack)) {
                        return ItemStack.EMPTY;
                    }

                    return stack;
                });
    }

    @Override
    public ItemStack extractItem(int slot, int count, boolean simulate) {
        return access(
                handler -> {
                    if (slot < 0 || slot >= handler.getSlots() || count <= 0) {
                        return ItemStack.EMPTY;
                    }

                    ItemStack current = handler.getStackInSlot(slot);

                    if (!canRestoreAfterExtraction(handler, slot, current)) {
                        return ItemStack.EMPTY;
                    }

                    return handler.extractItem(slot, count, simulate);
                });
    }

    @Override
    public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
        return access(handler -> handler.insertItem(slot, stack, simulate));
    }

    @Override
    public int getSlotLimit(int slot) {
        return access(handler -> handler.getSlotLimit(slot));
    }

    @Override
    public boolean isItemValid(int slot, ItemStack stack) {
        return access(handler -> handler.isItemValid(slot, stack));
    }

    /** TACZAddon only uses this mutation for rollback/snapshot restoration. */
    @Override
    public void setStackInSlot(int slot, ItemStack snapshot) {
        access(
                handler -> {
                    if (slot < 0 || slot >= handler.getSlots()) {
                        throw new IllegalArgumentException("Invalid nearby inventory slot " + slot);
                    }

                    if (handler instanceof IItemHandlerModifiable modifiable) {
                        modifiable.setStackInSlot(slot, snapshot.copy());
                        return Boolean.TRUE;
                    }

                    restoreGenericSnapshot(handler, slot, snapshot);

                    return Boolean.TRUE;
                });
    }

    private static boolean canRestoreAfterExtraction(
            IItemHandler handler, int slot, ItemStack current) {
        if (current == null || current.isEmpty()) {
            return false;
        }

        if (handler instanceof IItemHandlerModifiable) {
            return true;
        }

        /*
         * Generic handler rollback is compensating insertion, therefore
         * only consume a slot that accepts its own current item.
         *
         * This intentionally excludes output-only machine slots.
         */
        return handler.isItemValid(slot, current);
    }

    private static void restoreGenericSnapshot(IItemHandler handler, int slot, ItemStack snapshot) {
        ItemStack current = handler.getStackInSlot(slot);

        if (ItemStack.matches(current, snapshot)) {
            return;
        }

        if (snapshot.isEmpty()) {
            throw new IllegalStateException(
                    "Generic handler rollback cannot "
                            + "remove unexpected contents from slot "
                            + slot);
        }

        if (!current.isEmpty() && !ItemStack.isSameItemSameComponents(current, snapshot)) {
            throw new IllegalStateException(
                    "Generic handler slot identity changed " + "during rollback at slot " + slot);
        }

        int currentCount = current.isEmpty() ? 0 : current.getCount();

        int missing = snapshot.getCount() - currentCount;

        if (missing <= 0) {
            throw new IllegalStateException(
                    "Generic handler slot contains more "
                            + "items than the rollback snapshot at slot "
                            + slot);
        }

        if (!handler.isItemValid(slot, snapshot)) {
            throw new IllegalStateException(
                    "Generic handler no longer accepts its " + "rollback item at slot " + slot);
        }

        ItemStack restoring = snapshot.copyWithCount(missing);

        ItemStack remainder;

        try {
            remainder = handler.insertItem(slot, restoring, false);
        } catch (RuntimeException exception) {
            /*
             * Some handlers may mutate and then throw. Verify the final
             * state before declaring rollback failure.
             */
            if (ItemStack.matches(handler.getStackInSlot(slot), snapshot)) {
                return;
            }

            throw new IllegalStateException(
                    "Generic handler threw while restoring " + "slot " + slot, exception);
        }

        if (!remainder.isEmpty() || !ItemStack.matches(handler.getStackInSlot(slot), snapshot)) {
            throw new IllegalStateException(
                    "Generic handler could not exactly " + "restore slot " + slot);
        }
    }

    void markChanged() {
        if (!basePositionValid()) {
            return;
        }

        ServerLevel level = player.serverLevel();

        if (expectedBlockEntity != null) {
            expectedBlockEntity.setChanged();
        }

        BlockState state = level.getBlockState(pos);

        level.sendBlockUpdated(pos, state, state, 3);
    }
}
