package com.mafuyu404.taczaddon.storage.api;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/** A hint only: the server resolves the player's currently accessible sources again. */
public record RefitSourceLocator(CraftingSourceKey sourceKey, int slot) {
    public RefitSourceLocator {
        if (!(sourceKey instanceof CraftingSourceKey.BlockEntity)
                && !(sourceKey instanceof CraftingSourceKey.Backpack)) {
            throw new IllegalArgumentException("Unsupported refit source");
        }
    }

    public RefitSourceLocator(ResourceKey<Level> dimension, BlockPos pos, int slot) {
        this(new CraftingSourceKey.BlockEntity(dimension, pos.immutable()), slot);
    }

    public void encode(FriendlyByteBuf buffer) {
        if (sourceKey instanceof CraftingSourceKey.BlockEntity block) {
            buffer.writeByte(0);
            buffer.writeResourceLocation(block.dimension().location());
            buffer.writeBlockPos(block.pos());
        } else if (sourceKey instanceof CraftingSourceKey.Backpack backpack) {
            buffer.writeByte(1);
            buffer.writeUUID(backpack.playerId());
            buffer.writeUtf(backpack.handlerName(), 256);
            buffer.writeUtf(backpack.identifier(), 256);
            buffer.writeInt(backpack.inventorySlot());
            buffer.writeUUID(backpack.contentsId());
        }
        buffer.writeInt(slot);
    }

    public static RefitSourceLocator decode(FriendlyByteBuf buffer) {
        CraftingSourceKey key = switch (buffer.readUnsignedByte()) {
            case 0 -> new CraftingSourceKey.BlockEntity(
                    ResourceKey.create(Registries.DIMENSION, buffer.readResourceLocation()),
                    buffer.readBlockPos());
            case 1 -> new CraftingSourceKey.Backpack(buffer.readUUID(),
                    buffer.readUtf(256), buffer.readUtf(256), buffer.readInt(), buffer.readUUID());
            default -> throw new IllegalArgumentException("Unknown refit source type");
        };
        return new RefitSourceLocator(key, buffer.readInt());
    }

    public static RefitSourceLocator fromBlockSource(CraftingSourceKey.BlockEntity key, int slot) {
        return new RefitSourceLocator(key, slot);
    }
}
