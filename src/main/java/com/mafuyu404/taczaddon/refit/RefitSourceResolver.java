package com.mafuyu404.taczaddon.refit;

import com.mafuyu404.taczaddon.compat.SophisticatedBackpacksCompat;
import com.mafuyu404.taczaddon.config.CommonConfig;
import com.mafuyu404.taczaddon.inventory.NearbyInventorySourceResolver;
import com.tacz.guns.api.item.*;
import com.tacz.guns.api.item.attachment.AttachmentType;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.*;

public final class RefitSourceResolver {
    public static final int MAX_EXTERNAL_CANDIDATES = 256;

    private RefitSourceResolver() {}

    public record ExternalCandidate(
            RefitSourceLocator locator,
            ResourceLocation attachmentId,
            AttachmentType type,
            ItemStack displayStack) {}

    public static boolean canUseSources(ServerPlayer player) {
        return player != null
                && player.isAlive()
                && !player.isSpectator()
                && player.containerMenu == player.inventoryMenu
                && !VirtualAttachmentRules.isLiberated(player)
                && IGun.getIGunOrNull(player.getMainHandItem()) != null;
    }

    public static List<RefitSource> resolveExternalSources(ServerPlayer player) {
        List<RefitSource> sources = new ArrayList<>();
        for (var source : SophisticatedBackpacksCompat.resolveCarriedSources(player)) {
            sources.add(
                    new RefitSource(
                            new RefitSourceLocator(
                                    player.level().dimension().location(),
                                    net.minecraft.core.BlockPos.ZERO,
                                    NearbyInventorySourceResolver.SourceKind.SOPHISTICATED_BACKPACK,
                                    -1,
                                    source.inventoryName(),
                                    source.identifier(),
                                    source.slot(),
                                    source.storageId()),
                            source));
        }
        if (CommonConfig.enableNearbyContainerSources()) {
            for (var source :
                    NearbyInventorySourceResolver.resolve(
                            player,
                            player.blockPosition(),
                            CommonConfig.getContainerScanRadius(),
                            1)) {
                sources.add(
                        new RefitSource(
                                new RefitSourceLocator(
                                        player.level().dimension().location(),
                                        source.pos(),
                                        source.kind(),
                                        -1),
                                source));
            }
        }
        return List.copyOf(sources);
    }

    public static List<ExternalCandidate> resolveExternalCandidates(ServerPlayer player) {
        if (!canUseSources(player)) return List.of();
        ItemStack gunStack = player.getMainHandItem();
        IGun gun = IGun.getIGunOrNull(gunStack);
        if (gun.hasAttachmentLock(gunStack)) return List.of();
        List<ExternalCandidate> result = new ArrayList<>();
        for (var source : resolveExternalSources(player)) {
            try {
                if (!source.isValid()) continue;
                var handler = source.handler();
                for (int slot = 0; slot < handler.getSlots(); slot++) {
                    ItemStack stack = handler.getStackInSlot(slot);
                    IAttachment attachment = IAttachment.getIAttachmentOrNull(stack);
                    if (attachment == null) continue;
                    ResourceLocation id = attachment.getAttachmentId(stack);
                    AttachmentType type = attachment.getType(stack);
                    if (id == null
                            || type == null
                            || type == AttachmentType.NONE
                            || !gun.allowAttachment(gunStack, stack)) continue;
                    if (extractableStack(handler, slot).isEmpty()) continue;
                    result.add(
                            new ExternalCandidate(
                                    source.locator().withSlot(slot), id, type, stack.copy()));
                    if (result.size() >= MAX_EXTERNAL_CANDIDATES) return List.copyOf(result);
                }
            } catch (RuntimeException exception) {
                com.mojang.logging.LogUtils.getLogger()
                        .warn("Unreadable refit source {}", source.locator(), exception);
            }
        }
        return List.copyOf(result);
    }

    /** Visible templates and output-blocked slots are not installable attachments. */
    static ItemStack extractableStack(net.neoforged.neoforge.items.IItemHandler handler, int slot) {
        ItemStack visible = handler.getStackInSlot(slot);
        if (visible.isEmpty()) return ItemStack.EMPTY;
        ItemStack extracted = handler.extractItem(slot, 1, true);
        return extracted.getCount() == 1 && ItemStack.isSameItemSameComponents(visible, extracted)
                ? visible
                : ItemStack.EMPTY;
    }
}
