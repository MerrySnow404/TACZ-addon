package com.mafuyu404.taczaddon.refit;

import com.mafuyu404.taczaddon.inventory.NearbyInventorySourceResolver.SourceKind;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

public record RefitSourceLocator(
        ResourceLocation dimension,
        BlockPos pos,
        SourceKind kind,
        int slot,
        String inventory,
        String identifier,
        int backpackSlot,
        java.util.UUID backpackId) {
    public RefitSourceLocator(ResourceLocation dimension, BlockPos pos, SourceKind kind, int slot) {
        this(dimension, pos, kind, slot, "", "", -1, null);
    }

    public boolean carried() {
        return backpackId != null;
    }

    public RefitSourceLocator withSlot(int value) {
        return new RefitSourceLocator(
                dimension, pos, kind, value, inventory, identifier, backpackSlot, backpackId);
    }

    public boolean sameSource(RefitSourceLocator other) {
        return withSlot(-1).equals(other.withSlot(-1));
    }

    public void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeResourceLocation(dimension);
        buffer.writeBoolean(carried());
        if (carried()) {
            buffer.writeUtf(inventory, 256);
            buffer.writeUtf(identifier, 256);
            buffer.writeVarInt(backpackSlot);
            buffer.writeUUID(backpackId);
        } else {
            buffer.writeBlockPos(pos);
            buffer.writeEnum(kind);
        }
        buffer.writeVarInt(slot);
    }

    public static RefitSourceLocator read(RegistryFriendlyByteBuf buffer) {
        var dimension = buffer.readResourceLocation();
        if (buffer.readBoolean()) {
            String inventory = buffer.readUtf(256), identifier = buffer.readUtf(256);
            int backpackSlot = buffer.readVarInt();
            var id = buffer.readUUID();
            return new RefitSourceLocator(
                    dimension,
                    BlockPos.ZERO,
                    SourceKind.SOPHISTICATED_BACKPACK,
                    buffer.readVarInt(),
                    inventory,
                    identifier,
                    backpackSlot,
                    id);
        }
        return new RefitSourceLocator(
                dimension,
                buffer.readBlockPos(),
                buffer.readEnum(SourceKind.class),
                buffer.readVarInt());
    }
}
