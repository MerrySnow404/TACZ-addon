package com.mafuyu404.taczaddon.refit.network;

import com.mafuyu404.taczaddon.TACZaddon;
import com.mafuyu404.taczaddon.network.ServerboundPacketGuard;
import com.mafuyu404.taczaddon.refit.RefitExternalInstallService;
import com.mafuyu404.taczaddon.refit.RefitSourceLocator;
import com.tacz.guns.api.item.attachment.AttachmentType;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record RefitExternalAttachmentInstallPacket(
        int gunSlot,
        RefitSourceLocator locator,
        ResourceLocation expectedAttachmentId,
        AttachmentType expectedType)
        implements CustomPacketPayload {
    public static final Type<RefitExternalAttachmentInstallPacket> TYPE =
            new Type<>(
                    ResourceLocation.fromNamespaceAndPath(
                            TACZaddon.MODID, "refit_external_install"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RefitExternalAttachmentInstallPacket>
            STREAM_CODEC =
                    StreamCodec.of(
                            (buffer, packet) -> {
                                buffer.writeVarInt(packet.gunSlot);
                                packet.locator.write(buffer);
                                buffer.writeResourceLocation(packet.expectedAttachmentId);
                                buffer.writeEnum(packet.expectedType);
                            },
                            buffer ->
                                    new RefitExternalAttachmentInstallPacket(
                                            buffer.readVarInt(),
                                            RefitSourceLocator.read(buffer),
                                            buffer.readResourceLocation(),
                                            buffer.readEnum(AttachmentType.class)));

    public static void handle(
            RefitExternalAttachmentInstallPacket packet, IPayloadContext context) {
        context.enqueueWork(
                () -> {
                    if (!(context.player() instanceof ServerPlayer player)) return;
                    if (ServerboundPacketGuard.isRateLimited(player, TYPE.id(), 2)) return;
                    RefitExternalInstallService.install(
                            player,
                            packet.gunSlot,
                            packet.locator,
                            packet.expectedAttachmentId,
                            packet.expectedType);
                });
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
