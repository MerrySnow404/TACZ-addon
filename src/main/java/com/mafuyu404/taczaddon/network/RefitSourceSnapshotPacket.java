package com.mafuyu404.taczaddon.network;

import com.mafuyu404.taczaddon.refit.RefitSourceResolver;
import com.mafuyu404.taczaddon.storage.api.RefitSourceLocator;

import com.tacz.guns.api.item.attachment.AttachmentType;
import io.netty.handler.codec.DecoderException;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class RefitSourceSnapshotPacket {
    private final long requestId;
    private final List<RefitSourceResolver.RefitExternalCandidate>
            candidates;

    public RefitSourceSnapshotPacket(
            long requestId,
            List<RefitSourceResolver.RefitExternalCandidate> candidates
    ) {
        this.requestId = requestId;

        ArrayList<RefitSourceResolver.RefitExternalCandidate> copies =
                new ArrayList<>();
        if (candidates != null) {
            int size = Math.min(
                    candidates.size(),
                    RefitSourceResolver.MAX_EXTERNAL_CANDIDATES
            );
            for (int index = 0; index < size; index++) {
                RefitSourceResolver.RefitExternalCandidate candidate =
                        candidates.get(index);
                if (candidate != null
                        && candidate.displayStack() != null
                        && !candidate.displayStack().isEmpty()) {
                    copies.add(candidate.copy());
                }
            }
        }
        this.candidates = Collections.unmodifiableList(copies);
    }

    public static void encode(
            RefitSourceSnapshotPacket message,
            FriendlyByteBuf buffer
    ) {
        buffer.writeLong(message.requestId);
        buffer.writeVarInt(message.candidates.size());

        for (RefitSourceResolver.RefitExternalCandidate candidate
                : message.candidates) {
            candidate.locator().encode(buffer);
            buffer.writeResourceLocation(candidate.attachmentId());
            buffer.writeEnum(candidate.type());
            buffer.writeItemStack(candidate.displayStack(), true);
        }
    }

    public static RefitSourceSnapshotPacket decode(
            FriendlyByteBuf buffer
    ) {
        long requestId = buffer.readLong();
        int declaredSize = buffer.readVarInt();

        if (declaredSize < 0
                || declaredSize
                > RefitSourceResolver.MAX_EXTERNAL_CANDIDATES) {
            throw new DecoderException(
                    "Invalid refit source snapshot size: "
                            + declaredSize
            );
        }

        ArrayList<RefitSourceResolver.RefitExternalCandidate> candidates =
                new ArrayList<>(declaredSize);
        for (int index = 0; index < declaredSize; index++) {
            RefitSourceLocator locator = RefitSourceLocator.decode(buffer);
            ResourceLocation attachmentId =
                    buffer.readResourceLocation();
            AttachmentType type = buffer.readEnum(AttachmentType.class);
            ItemStack stack = buffer.readItem();

            if (stack.isEmpty()) {
                throw new DecoderException(
                        "Refit snapshot contained an empty candidate"
                );
            }

            candidates.add(
                    new RefitSourceResolver.RefitExternalCandidate(
                            attachmentId,
                            type,
                            locator,
                            stack
                    )
            );
        }

        return new RefitSourceSnapshotPacket(requestId, candidates);
    }

    public long requestId() {
        return this.requestId;
    }

    public List<RefitSourceResolver.RefitExternalCandidate> candidates() {
        ArrayList<RefitSourceResolver.RefitExternalCandidate> copies =
                new ArrayList<>(this.candidates.size());
        for (RefitSourceResolver.RefitExternalCandidate candidate
                : this.candidates) {
            copies.add(candidate.copy());
        }
        return Collections.unmodifiableList(copies);
    }
}
