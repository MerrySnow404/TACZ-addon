package com.mafuyu404.taczaddon.config.network;

import com.mafuyu404.taczaddon.TACZaddon;
import com.mafuyu404.taczaddon.config.ClientRuleState;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record RuleSyncPacket(boolean liberateAttachment, boolean showAttachmentDetail)
        implements CustomPacketPayload {
    public static final Type<RuleSyncPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(TACZaddon.MODID, "rule_sync"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RuleSyncPacket> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.BOOL,
                    RuleSyncPacket::liberateAttachment,
                    ByteBufCodecs.BOOL,
                    RuleSyncPacket::showAttachmentDetail,
                    RuleSyncPacket::new);

    public static void handle(RuleSyncPacket msg, IPayloadContext context) {
        context.enqueueWork(
                () ->
                        ClientRuleState.setRuleState(
                                msg.liberateAttachment, msg.showAttachmentDetail));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
