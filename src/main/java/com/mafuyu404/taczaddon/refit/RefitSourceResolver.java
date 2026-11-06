package com.mafuyu404.taczaddon.refit;

import com.mafuyu404.taczaddon.compat.sophisticated.SophisticatedBackpacksCompat;
import com.mafuyu404.taczaddon.config.CommonConfig;
import com.mafuyu404.taczaddon.storage.NearbyInventorySourceResolver;
import com.mafuyu404.taczaddon.storage.api.CraftingItemSource;
import com.mafuyu404.taczaddon.storage.api.CraftingSourceKey;
import com.mafuyu404.taczaddon.storage.api.RefitSourceLocator;

import com.mojang.logging.LogUtils;
import com.tacz.guns.api.item.IAttachment;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.attachment.AttachmentType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Server-side carried-backpack and generic nearby-container attachment sources.
 * Candidates retain enough identity for a later authoritative install.
 */
public final class RefitSourceResolver {
    public static final int MAX_EXTERNAL_CANDIDATES = 256;

    private static final Logger LOGGER = LogUtils.getLogger();

    private RefitSourceResolver() {
    }

    public static List<CraftingItemSource> resolveExternalSources(
            ServerPlayer player
    ) {
        ArrayList<CraftingItemSource> sources = new ArrayList<>();
        try {
            sources.addAll(SophisticatedBackpacksCompat.resolveRefitSources(player));
        } catch (RuntimeException | LinkageError failure) {
            LOGGER.warn("Skipping unavailable carried backpack sources", failure);
        }
        if (CommonConfig.enableNearbyContainerSources()) {
            sources.addAll(NearbyInventorySourceResolver.resolveGeneric(
                    player, player.blockPosition(),
                    CommonConfig.getNearbyContainerScanRadius(), 1));
        }
        return List.copyOf(sources);
    }

    public static List<RefitExternalCandidate> resolveExternalCandidates(
            ServerPlayer player
    ) {
        ItemStack heldGun = player.getMainHandItem();
        IGun gun = IGun.getIGunOrNull(heldGun);
        if (gun == null) {
            return List.of();
        }

        ArrayList<RefitExternalCandidate> candidates =
                new ArrayList<>();

        for (CraftingItemSource source
                : resolveExternalSources(player)) {
            try {
                collectCandidatesFromSource(
                        player,
                        heldGun,
                        gun,
                        source,
                        candidates
                );
            } catch (RuntimeException | LinkageError failure) {
                LOGGER.warn(
                        "Skipping unreadable or binary-incompatible refit source",
                        failure
                );
            }
        }

        return candidates.size() > MAX_EXTERNAL_CANDIDATES
                ? List.copyOf(
                candidates.subList(
                        0,
                        MAX_EXTERNAL_CANDIDATES
                )
        )
                : List.copyOf(candidates);
    }

    private static void collectCandidatesFromSource(
            ServerPlayer player,
            ItemStack heldGun,
            IGun gun,
            CraftingItemSource source,
            List<RefitExternalCandidate> candidates
    ) {
        if (!source.isValid(player)) {
            return;
        }

        if (!(source.key() instanceof CraftingSourceKey.BlockEntity)
                && !(source.key() instanceof CraftingSourceKey.Backpack)) {
            return;
        }

        int slots = source.slotCount();
        for (int slot = 0; slot < slots; slot++) {
            if (candidates.size() >= MAX_EXTERNAL_CANDIDATES) {
                return;
            }

            ItemStack stack = source.getStackInSlot(slot);
            IAttachment attachment =
                    IAttachment.getIAttachmentOrNull(stack);
            if (attachment == null) {
                continue;
            }

            ResourceLocation attachmentId =
                    attachment.getAttachmentId(stack);
            AttachmentType type = attachment.getType(stack);
            if (attachmentId == null
                    || type == null
                    || type == AttachmentType.NONE
                    || !gun.allowAttachment(heldGun, stack)) {
                continue;
            }

            if (extractableStack(source, slot).isEmpty()) continue;

            candidates.add(new RefitExternalCandidate(
                    attachmentId,
                    type,
                    new RefitSourceLocator(source.key(), slot),
                    stack.copy()
            ));
        }
    }

    public static Optional<CraftingItemSource> findSource(
            List<CraftingItemSource> sources,
            RefitSourceLocator locator
    ) {
        for (CraftingItemSource source : sources) {
            if (source.key().equals(locator.sourceKey())) {
                return Optional.of(source);
            }
        }
        return Optional.empty();
    }

    /** Visible templates and output-blocked slots are not installable attachments. */
    static ItemStack extractableStack(CraftingItemSource source, int slot) {
        ItemStack visible = source.getStackInSlot(slot);
        if (visible.isEmpty()) return ItemStack.EMPTY;
        ItemStack extracted = source.extractItem(slot, 1, true);
        return extracted.getCount() == 1 && ItemStack.isSameItemSameTags(visible, extracted)
                ? visible : ItemStack.EMPTY;
    }

    public record RefitExternalCandidate(
            ResourceLocation attachmentId,
            AttachmentType type,
            RefitSourceLocator locator,
            ItemStack displayStack
    ) {
        public RefitExternalCandidate {
            displayStack = displayStack.copy();
        }

        public RefitExternalCandidate copy() {
            return new RefitExternalCandidate(
                    this.attachmentId,
                    this.type,
                    this.locator,
                    this.displayStack
            );
        }
    }
}
