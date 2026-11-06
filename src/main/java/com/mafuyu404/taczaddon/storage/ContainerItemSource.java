package com.mafuyu404.taczaddon.storage;

import com.mafuyu404.taczaddon.compat.createstorage.CreateStorageCompat;
import com.mafuyu404.taczaddon.compat.sophisticated.SophisticatedBlockStorageCompat;
import com.mafuyu404.taczaddon.storage.api.BlockAccess;
import com.mafuyu404.taczaddon.storage.api.CraftingItemSource;
import com.mafuyu404.taczaddon.storage.api.CraftingSourceKey;
import com.mafuyu404.taczaddon.storage.api.PhysicalSlot;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.util.BitSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nullable;

/**
 * A block inventory source with one backend selected for its lifetime.
 *
 * Source objects are request-scoped: one resolve/craft operation creates one
 * set of sources. Each source pins the concrete BlockEntity and the
 * capability/container resolved at construction. If that BlockEntity is
 * replaced, or the captured LazyOptional is invalidated, the source fails
 * closed instead of silently switching to a different storage.
 *
 * The captured LazyOptional is reused for the request lifetime. This matters
 * because a valid Forge implementation may return a fresh wrapper object from
 * repeated getCapability() calls even though the underlying capability has not
 * changed.
 *
 * The raw Container fallback is intentionally conservative. It is used only
 * when no Forge IItemHandler capability is exposed by the block entity.
 */
public final class ContainerItemSource implements CraftingItemSource {
    private enum Backend {
        ITEM_HANDLER,
        CONTAINER,
        NONE
    }

    private final ResourceKey<Level> dimension;
    private final BlockPos pos;
    private final Backend backend;
    private final boolean genericOnly;
    private final UUID linkedStorageGroup;
    private final BlockAccess createStorageAccess;
    private final BitSet excludedSlots = new BitSet();
    @Nullable
    private final BlockEntity expectedBlockEntity;
    @Nullable
    private final LazyOptional<IItemHandler> expectedItemHandlerCapability;
    @Nullable
    private final IItemHandler expectedItemHandler;
    @Nullable
    private final Container expectedContainer;

    public ContainerItemSource(Level level, BlockPos pos) {
        this(level, pos, false);
    }

    /** Refit uses only the standard item capability / vanilla Container contract. */
    public static ContainerItemSource generic(Level level, BlockPos pos) {
        return new ContainerItemSource(level, pos, true);
    }

    private ContainerItemSource(Level level, BlockPos pos, boolean genericOnly) {
        this.genericOnly = genericOnly;
        this.dimension = level.dimension();
        this.pos = pos.immutable();
        BackendBinding binding = detectBackend(level, this.pos);
        this.backend = binding.backend();
        this.expectedBlockEntity = binding.blockEntity();
        this.expectedItemHandlerCapability =
                binding.itemHandlerCapability();
        this.expectedItemHandler = binding.itemHandler();
        this.expectedContainer = binding.container();
        this.linkedStorageGroup = genericOnly ? null : SophisticatedBlockStorageCompat.groupId(this.expectedBlockEntity);
        this.createStorageAccess = genericOnly ? null : CreateStorageCompat.bind(this.expectedBlockEntity, this.expectedItemHandler);
        if (this.createStorageAccess != null) {
            for (int i = 0; i < this.createStorageAccess.slots().size(); i++) {
                if (this.createStorageAccess.slots().get(i).slot() < 0) this.excludedSlots.set(i);
            }
        }
    }

    boolean claimPhysicalSlots(Set<PhysicalSlot> seen) {
        if (this.createStorageAccess == null) return true;
        var slots = this.createStorageAccess.slots();
        this.excludedSlots.or(excludeClaimedSlots(slots, seen));
        return this.excludedSlots.nextClearBit(0) < slots.size();
    }

    static BitSet excludeClaimedSlots(List<PhysicalSlot> slots,
                                      Set<PhysicalSlot> seen) {
        BitSet excluded = new BitSet();
        for (int i = 0; i < slots.size(); i++) {
            var slot = slots.get(i);
            if (slot.slot() < 0 || !seen.add(slot)) excluded.set(i);
        }
        return excluded;
    }

    public UUID linkedStorageGroup() {
        return this.linkedStorageGroup;
    }

    @Override
    public CraftingSourceKey key() {
        return new CraftingSourceKey.BlockEntity(
                this.dimension,
                this.pos
        );
    }

    @Override
    public Object backendIdentity() {
        if (this.backend == Backend.ITEM_HANDLER) {
            return this.expectedItemHandler;
        }
        if (this.backend == Backend.CONTAINER) {
            return this.expectedContainer;
        }
        return this;
    }

    @Override
    public int slotCount() {
        if (this.backend == Backend.ITEM_HANDLER) {
            IItemHandler handler = resolveHandler();
            return handler == null ? 0 : handler.getSlots();
        }

        if (this.backend == Backend.CONTAINER) {
            Container container = resolveContainer();
            return container == null ? 0 : container.getContainerSize();
        }

        return 0;
    }

    @Override
    public ItemStack getStackInSlot(int slot) {
        if (slot < 0 || this.excludedSlots.get(slot)) {
            return ItemStack.EMPTY;
        }

        if (this.backend == Backend.ITEM_HANDLER) {
            IItemHandler handler = resolveHandler();
            if (handler == null || slot >= handler.getSlots()) {
                return ItemStack.EMPTY;
            }
            return handler.getStackInSlot(slot);
        }

        if (this.backend == Backend.CONTAINER) {
            Container container = resolveContainer();
            if (container == null || slot >= container.getContainerSize()) {
                return ItemStack.EMPTY;
            }
            return container.getItem(slot);
        }

        return ItemStack.EMPTY;
    }

    @Override
    public ItemStack extractItem(
            int slot,
            int amount,
            boolean simulate
    ) {
        if (slot < 0 || amount <= 0 || this.excludedSlots.get(slot)) {
            return ItemStack.EMPTY;
        }

        if (this.backend == Backend.ITEM_HANDLER) {
            IItemHandler handler = resolveHandler();
            if (handler == null || slot >= handler.getSlots()) {
                return ItemStack.EMPTY;
            }
            return handler.extractItem(slot, amount, simulate);
        }

        if (this.backend == Backend.CONTAINER) {
            Container container = resolveContainer();
            if (container == null || slot >= container.getContainerSize()) {
                return ItemStack.EMPTY;
            }

            ItemStack current = container.getItem(slot);
            if (current.isEmpty()) {
                return ItemStack.EMPTY;
            }

            int extractedCount = Math.min(amount, current.getCount());
            if (simulate) {
                return current.copyWithCount(extractedCount);
            }

            ItemStack extracted =
                    container.removeItem(slot, extractedCount);
            container.setChanged();
            return extracted;
        }

        return ItemStack.EMPTY;
    }

    @Override
    public ItemStack insertItem(
            int slot,
            ItemStack stack,
            boolean simulate
    ) {
        if (stack.isEmpty()) {
            return ItemStack.EMPTY;
        }

        if (slot < 0 || this.excludedSlots.get(slot)) {
            return stack.copy();
        }

        if (this.backend == Backend.ITEM_HANDLER) {
            IItemHandler handler = resolveHandler();
            if (handler == null || slot >= handler.getSlots()) {
                return stack.copy();
            }
            return handler.insertItem(slot, stack, simulate);
        }

        if (this.backend == Backend.CONTAINER) {
            Container container = resolveContainer();
            if (container == null || slot >= container.getContainerSize()) {
                return stack.copy();
            }

            if (!container.canPlaceItem(slot, stack)) {
                return stack.copy();
            }

            ItemStack current = container.getItem(slot);
            int slotLimit = Math.min(
                    container.getMaxStackSize(),
                    stack.getMaxStackSize()
            );

            if (current.isEmpty()) {
                int insertedCount =
                        Math.min(slotLimit, stack.getCount());
                if (!simulate) {
                    container.setItem(
                            slot,
                            stack.copyWithCount(insertedCount)
                    );
                    container.setChanged();
                }
                return remainder(stack, insertedCount);
            }

            if (!ItemStack.isSameItemSameTags(current, stack)) {
                return stack.copy();
            }

            int availableSpace =
                    Math.min(slotLimit, current.getMaxStackSize())
                            - current.getCount();
            if (availableSpace <= 0) {
                return stack.copy();
            }

            int insertedCount =
                    Math.min(availableSpace, stack.getCount());
            if (!simulate) {
                current.grow(insertedCount);
                container.setChanged();
            }
            return remainder(stack, insertedCount);
        }

        return stack.copy();
    }

    @Override
    public boolean isValid(ServerPlayer player) {
        if (!player.level().dimension().equals(this.dimension)
                || !player.level().isLoaded(this.pos)) {
            return false;
        }

        BlockEntity blockEntity =
                player.level().getBlockEntity(this.pos);
        if (blockEntity == null || blockEntity.isRemoved()) {
            return false;
        }

        if (this.backend == Backend.ITEM_HANDLER) {
            IItemHandler handler = resolveHandler();
            return handler != null && handler.getSlots() > 0;
        }

        if (this.backend == Backend.CONTAINER) {
            Container container = resolveContainer();
            return container != null
                    && container.getContainerSize() > 0;
        }

        return false;
    }

    @Override
    public boolean hasUsableBackend() {
        return this.backend != Backend.NONE && this.slotCount() > 0;
    }

    @Override
    public void markChanged() {
        BlockEntity blockEntity = resolveExpectedBlockEntity();
        if (blockEntity != null) {
            blockEntity.setChanged();
        }
    }

    @Override
    public void synchronize(ServerPlayer player) {
        Level level = resolveLevel();
        BlockEntity blockEntity =
                level == null ? null : level.getBlockEntity(this.pos);

        if (level != null
                && blockEntity != null
                && !blockEntity.isRemoved()
                && blockEntity == this.expectedBlockEntity) {
            blockEntity.setChanged();
            level.sendBlockUpdated(
                    this.pos,
                    blockEntity.getBlockState(),
                    blockEntity.getBlockState(),
                    3
            );
        }
    }

    private static BackendBinding detectBackend(
            Level level,
            BlockPos pos
    ) {
        if (!level.isLoaded(pos)) {
            return BackendBinding.none();
        }

        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity == null || blockEntity.isRemoved()) {
            return BackendBinding.none();
        }

        LazyOptional<IItemHandler> capability =
                blockEntity.getCapability(
                        ForgeCapabilities.ITEM_HANDLER
                );
        IItemHandler handler = capability.orElse(null);
        if (handler != null && handler.getSlots() > 0) {
            return new BackendBinding(
                    Backend.ITEM_HANDLER,
                    blockEntity,
                    capability,
                    handler,
                    null
            );
        }

        if (blockEntity instanceof Container container
                && container.getContainerSize() > 0) {
            return new BackendBinding(
                    Backend.CONTAINER,
                    blockEntity,
                    null,
                    null,
                    container
            );
        }

        return BackendBinding.none();
    }

    @Nullable
    private Level resolveLevel() {
        var server = ServerLifecycleHooks.getCurrentServer();
        return server == null ? null : server.getLevel(this.dimension);
    }

    @Nullable
    private BlockEntity resolveBlockEntity() {
        Level level = resolveLevel();
        if (level == null || !level.isLoaded(this.pos)) {
            return null;
        }

        BlockEntity blockEntity = level.getBlockEntity(this.pos);
        return blockEntity == null || blockEntity.isRemoved()
                ? null
                : blockEntity;
    }

    @Nullable
    private BlockEntity resolveExpectedBlockEntity() {
        BlockEntity blockEntity = resolveBlockEntity();
        return blockEntity == this.expectedBlockEntity
                ? blockEntity
                : null;
    }

    @Nullable
    private IItemHandler resolveHandler() {
        if (this.backend != Backend.ITEM_HANDLER) {
            return null;
        }

        BlockEntity blockEntity = resolveBlockEntity();
        if (blockEntity == null
                || blockEntity != this.expectedBlockEntity) {
            return null;
        }

        LazyOptional<IItemHandler> capability =
                this.expectedItemHandlerCapability;
        if (capability == null) {
            return null;
        }

        /*
         * Use the capability instance captured when this request-scoped source
         * was created. Some valid Forge block entities return a fresh wrapper
         * object from each getCapability() call; repeatedly querying and
         * comparing wrapper identity would reject those sources spuriously.
         *
         * LazyOptional invalidation still makes this fail closed when the
         * capability itself is retired.
         */
        IItemHandler handler = capability.orElse(null);
        return isExpectedBackend(blockEntity, handler)
                ? handler
                : null;
    }

    @Nullable
    private Container resolveContainer() {
        if (this.backend != Backend.CONTAINER) {
            return null;
        }

        BlockEntity blockEntity = resolveBlockEntity();
        if (blockEntity == null
                || blockEntity != this.expectedBlockEntity) {
            return null;
        }

        Container container = blockEntity instanceof Container value
                ? value
                : null;
        return isExpectedBackend(blockEntity, container)
                ? container
                : null;
    }

    private boolean isExpectedBackend(
            BlockEntity currentBlockEntity,
            Object currentBackend
    ) {
        return (this.createStorageAccess == null || this.createStorageAccess.valid().getAsBoolean())
                && (this.genericOnly || Objects.equals(this.linkedStorageGroup,
                SophisticatedBlockStorageCompat.groupId(currentBlockEntity)))
                && sameBackendIdentity(
                this.expectedBlockEntity,
                expectedBackend(),
                currentBlockEntity,
                currentBackend
        );
    }

    private Object expectedBackend() {
        if (this.backend == Backend.ITEM_HANDLER) {
            return this.expectedItemHandler;
        }
        if (this.backend == Backend.CONTAINER) {
            return this.expectedContainer;
        }
        return this;
    }

    static boolean sameBackendIdentity(
            Object expectedBlockEntity,
            Object expectedBackend,
            Object currentBlockEntity,
            Object currentBackend
    ) {
        return expectedBlockEntity == currentBlockEntity
                && expectedBackend == currentBackend;
    }

    private record BackendBinding(
            Backend backend,
            @Nullable BlockEntity blockEntity,
            @Nullable LazyOptional<IItemHandler> itemHandlerCapability,
            @Nullable IItemHandler itemHandler,
            @Nullable Container container
    ) {
        private static BackendBinding none() {
            return new BackendBinding(
                    Backend.NONE,
                    null,
                    null,
                    null,
                    null
            );
        }
    }

    private static ItemStack remainder(
            ItemStack original,
            int inserted
    ) {
        int remaining = original.getCount() - inserted;
        return remaining <= 0
                ? ItemStack.EMPTY
                : original.copyWithCount(remaining);
    }
}
