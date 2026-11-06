package com.mafuyu404.taczaddon.inventory.network;

import com.mafuyu404.taczaddon.TACZaddon;
import com.mafuyu404.taczaddon.config.CommonConfig;
import com.mafuyu404.taczaddon.gunsmith.GunSmithCraftingSessionManager;
import com.mafuyu404.taczaddon.inventory.ContainerReaderState;
import com.mafuyu404.taczaddon.inventory.NearbyInventorySourceResolver;
import com.mafuyu404.taczaddon.network.NetworkHandler;
import com.mafuyu404.taczaddon.network.ServerboundPacketGuard;
import com.tacz.guns.inventory.GunSmithTableMenu;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;

public final class ContainerPositionPacket implements CustomPacketPayload {

    public static final Type<ContainerPositionPacket> TYPE =
            new Type<>(
                    ResourceLocation.fromNamespaceAndPath(TACZaddon.MODID, "container_position"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ContainerPositionPacket> STREAM_CODEC =
            BlockPos.STREAM_CODEC
                    .<RegistryFriendlyByteBuf>cast()
                    .map(ContainerPositionPacket::new, ContainerPositionPacket::blockPos);

    private static final double MAX_REQUEST_DISTANCE_SQR = 64.0D;
    private static final int MAX_RETURNED_STACKS = 216;
    private static final int COOLDOWN_TICKS = 10;

    private final BlockPos blockPos;

    public ContainerPositionPacket(BlockPos blockPos) {
        this.blockPos = blockPos;
    }

    private BlockPos blockPos() {
        return this.blockPos;
    }

    public static void handle(ContainerPositionPacket message, IPayloadContext context) {
        context.enqueueWork(
                () -> {
                    if (!(context.player() instanceof ServerPlayer player)) {
                        return;
                    }

                    if (message.blockPos == null) {
                        return;
                    }

                    if (!CommonConfig.enableContainerReader()) {
                        return;
                    }

                    if (!(player.containerMenu instanceof GunSmithTableMenu)) {
                        return;
                    }

                    if (ServerboundPacketGuard.isRateLimited(player, TYPE.id(), COOLDOWN_TICKS)) {
                        return;
                    }

                    if (message.blockPos.distToCenterSqr(player.position())
                            > MAX_REQUEST_DISTANCE_SQR) {
                        return;
                    }

                    if (!player.level().isLoaded(message.blockPos)) {
                        return;
                    }

                    var session = GunSmithCraftingSessionManager.getSession(player.getUUID());
                    var state = player.level().getBlockState(message.blockPos);
                    if (!(state.getBlock()
                            instanceof com.tacz.guns.block.AbstractGunSmithTableBlock tableBlock))
                        return;
                    BlockPos rootPos = tableBlock.getRootPos(message.blockPos, state);
                    if (session == null
                            || !session.validate(player, player.containerMenu.containerId)
                            || !session.getTablePos().equals(rootPos)) return;
                    scanAndSend(player, session.getTablePos());
                });
    }

    /**
     * Performs the initial scan, stores the discovered source positions, and sends their current
     * contents to the client.
     */
    private static void scanAndSend(ServerPlayer player, BlockPos tablePos) {
        List<ItemStack> items = new ArrayList<>();
        List<BlockPos> containerPositions = new ArrayList<>();
        List<BlockPos> backpackPositions = new ArrayList<>();

        for (var source :
                NearbyInventorySourceResolver.resolve(
                        player, tablePos, CommonConfig.getContainerScanRadius(), 1)) {
            try {
                if (!source.isValid()) continue;
                List<ItemStack> sourceItems = new ArrayList<>();
                for (int slot = 0;
                        slot < source.handler().getSlots()
                                && items.size() + sourceItems.size() < MAX_RETURNED_STACKS;
                        slot++) {
                    ItemStack stack = source.handler().getStackInSlot(slot);
                    if (!stack.isEmpty()) sourceItems.add(stack.copy());
                }
                items.addAll(sourceItems);
                if (source.kind() == NearbyInventorySourceResolver.SourceKind.CONTAINER) {
                    containerPositions.add(source.pos());
                } else {
                    backpackPositions.add(source.pos());
                }
            } catch (RuntimeException exception) {
                com.mojang.logging.LogUtils.getLogger()
                        .warn(
                                "Skipping unavailable nearby ingredient source {}",
                                source.pos(),
                                exception);
            }
        }

        ContainerReaderState.setSnapshot(player, containerPositions, backpackPositions);

        NetworkHandler.sendToClient(player, new ContainerReaderPacket(items));
    }

    /**
     * Re-resolves the loaded sources around the authoritative session anchor.
     *
     * <p>Call this after the server has consumed crafting ingredients.
     */
    public static void refreshStoredSnapshot(ServerPlayer player) {
        if (!CommonConfig.enableContainerReader()) {
            return;
        }

        if (!(player.containerMenu instanceof GunSmithTableMenu)) {
            return;
        }

        var session = GunSmithCraftingSessionManager.getSession(player.getUUID());
        if (session != null && session.validate(player, player.containerMenu.containerId)) {
            scanAndSend(player, session.getTablePos());
        }
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
