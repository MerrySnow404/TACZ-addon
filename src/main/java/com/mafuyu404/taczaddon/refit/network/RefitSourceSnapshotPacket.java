package com.mafuyu404.taczaddon.refit.network;

import com.mafuyu404.taczaddon.TACZaddon;
import com.mafuyu404.taczaddon.refit.RefitSourceLocator;
import com.mafuyu404.taczaddon.refit.RefitSourceResolver;
import com.mafuyu404.taczaddon.refit.RefitSourceResolver.ExternalCandidate;
import com.mafuyu404.taczaddon.refit.client.RefitExternalSourceState;
import com.tacz.guns.api.item.attachment.AttachmentType;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record RefitSourceSnapshotPacket(
        long requestId, java.util.List<ExternalCandidate> candidates)
        implements CustomPacketPayload {
    public static final Type<RefitSourceSnapshotPacket> TYPE =
            new Type<>(
                    ResourceLocation.fromNamespaceAndPath(
                            TACZaddon.MODID, "refit_source_snapshot"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RefitSourceSnapshotPacket>
            STREAM_CODEC =
                    StreamCodec.of(
                            (buffer, packet) -> {
                                buffer.writeLong(packet.requestId);
                                buffer.writeVarInt(packet.candidates.size());
                                for (var entry : packet.candidates) {
                                    entry.locator().write(buffer);
                                    buffer.writeResourceLocation(entry.attachmentId());
                                    buffer.writeEnum(entry.type());
                                    ItemStack.STREAM_CODEC.encode(buffer, entry.displayStack());
                                }
                            },
                            buffer -> {
                                long id = buffer.readLong();
                                int size = buffer.readVarInt();
                                if (size < 0 || size > RefitSourceResolver.MAX_EXTERNAL_CANDIDATES)
                                    throw new io.netty.handler.codec.DecoderException(
                                            "Invalid refit candidate count");
                                java.util.List<ExternalCandidate> entries =
                                        new java.util.ArrayList<>(size);
                                for (int i = 0; i < size; i++)
                                    entries.add(
                                            new ExternalCandidate(
                                                    RefitSourceLocator.read(buffer),
                                                    buffer.readResourceLocation(),
                                                    buffer.readEnum(AttachmentType.class),
                                                    ItemStack.STREAM_CODEC.decode(buffer)));
                                return new RefitSourceSnapshotPacket(
                                        id, java.util.List.copyOf(entries));
                            });

    public static void handle(RefitSourceSnapshotPacket packet, IPayloadContext context) {
        context.enqueueWork(
                () -> {
                    RefitExternalSourceState.accept(packet);
                });
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
