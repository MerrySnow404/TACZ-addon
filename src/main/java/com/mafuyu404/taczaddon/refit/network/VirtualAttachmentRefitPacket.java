package com.mafuyu404.taczaddon.refit.network;

import com.mafuyu404.taczaddon.TACZaddon;
import com.mafuyu404.taczaddon.network.ServerboundPacketGuard;
import com.mafuyu404.taczaddon.refit.AttachmentRefitService;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Client -> server refit request.
 *
 * <p>The packet only carries the source slot, the gun slot and the attachment id. The client never
 * sends an ItemStack or any provenance flag; the server rebuilds the canonical attachment and
 * decides virtual vs physical ownership in {@link AttachmentRefitService}.
 */
public record VirtualAttachmentRefitPacket(
        int sourceSlot, int gunSlot, ResourceLocation attachmentId) implements CustomPacketPayload {

    /**
     * sourceSlot == -1 means that the attachment is virtual. sourceSlot >= 0 means that it comes
     * from the real player inventory.
     */
    public static final int VIRTUAL_SOURCE_SLOT = AttachmentRefitService.VIRTUAL_SOURCE_SLOT;

    private static final int COOLDOWN_TICKS = 2;

    public static final Type<VirtualAttachmentRefitPacket> TYPE =
            new Type<>(
                    ResourceLocation.fromNamespaceAndPath(
                            TACZaddon.MODID, "virtual_attachment_refit"));

    public static final StreamCodec<RegistryFriendlyByteBuf, VirtualAttachmentRefitPacket>
            STREAM_CODEC =
                    StreamCodec.composite(
                            ByteBufCodecs.VAR_INT,
                            VirtualAttachmentRefitPacket::sourceSlot,
                            ByteBufCodecs.VAR_INT,
                            VirtualAttachmentRefitPacket::gunSlot,
                            ResourceLocation.STREAM_CODEC,
                            VirtualAttachmentRefitPacket::attachmentId,
                            VirtualAttachmentRefitPacket::new);

    public static void handle(VirtualAttachmentRefitPacket packet, IPayloadContext context) {
        context.enqueueWork(
                () -> {
                    if (!(context.player() instanceof ServerPlayer player)) {
                        return;
                    }

                    if (ServerboundPacketGuard.isRateLimited(player, TYPE.id(), COOLDOWN_TICKS)) {
                        return;
                    }

                    AttachmentRefitService.install(
                            player, packet.sourceSlot(), packet.gunSlot(), packet.attachmentId());
                });
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
