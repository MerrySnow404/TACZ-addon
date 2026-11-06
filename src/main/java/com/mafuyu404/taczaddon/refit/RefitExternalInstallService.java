package com.mafuyu404.taczaddon.refit;

import com.mafuyu404.taczaddon.storage.PlayerInventorySource;
import com.mafuyu404.taczaddon.storage.SafeSourceInsert;
import com.mafuyu404.taczaddon.storage.api.CraftingItemSource;
import com.mafuyu404.taczaddon.storage.api.RefitSourceLocator;

import com.mojang.logging.LogUtils;
import com.tacz.guns.api.item.IAttachment;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.resource.modifier.AttachmentPropertyManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;

import java.util.List;
import java.util.Optional;
import javax.annotation.Nullable;

/**
 * Authoritative external-source attachment install.
 *
 * The client locator is only a hint. Every request re-resolves the currently
 * legal carried/nearby source set from the server player and verifies the
 * exact physical slot before any item is touched.
 */
public final class RefitExternalInstallService {
    private static final Logger LOGGER = LogUtils.getLogger();

    private RefitExternalInstallService() {
    }

    public static void handle(
            RefitInstallRequest request,
            @Nullable ServerPlayer player
    ) {
        if (player == null) {
            return;
        }

        if (LiberateAttachmentService.isEnabled(player)) {
            reject(player);
            return;
        }

        CraftingItemSource source = null;

        try {
            ItemStack gunStack =
                    LiberateAttachmentService.getCurrentRealGun(
                            player,
                            request.gunSlotIndex()
                    );

            if (gunStack == null) {
                reject(player);
                return;
            }

            IGun gun = IGun.getIGunOrNull(gunStack);
            if (gun == null
                    || gun.hasAttachmentLock(gunStack)) {
                reject(player);
                return;
            }

            List<CraftingItemSource> sources =
                    RefitSourceResolver.resolveExternalSources(player);

            Optional<CraftingItemSource> found =
                    RefitSourceResolver.findSource(
                            sources,
                            request.locator()
                    );

            if (found.isEmpty()) {
                reject(player);
                return;
            }

            source = found.get();

            installFromSource(
                    player,
                    gunStack,
                    gun,
                    source,
                    request
            );
        } catch (RuntimeException | LinkageError failure) {
            /*
             * If the failure happened after a third-party handler mutated its
             * inventory, its exact state may be unknown. Do not retry or perform
             * another extraction here. Only force the authoritative state to be
             * synchronized when the source was already resolved.
             */
            if (source != null) {
                synchronize(player, source);
            }

            LOGGER.error(
                    "Refit external install failed for player {}; "
                            + "operation stopped without retry",
                    player.getGameProfile().getName(),
                    failure
            );

            reject(player);
        }
    }

    private static void installFromSource(
            ServerPlayer player,
            ItemStack gunStack,
            IGun gun,
            CraftingItemSource source,
            RefitInstallRequest request
    ) {
        RefitSourceLocator locator = request.locator();
        if (!source.isValid(player)
                || locator.slot() < 0
                || locator.slot() >= source.slotCount()) {
            reject(player);
            return;
        }

        RefitExternalInstallTransaction.Result result =
                RefitExternalInstallTransaction.execute(
                        source,
                        locator.slot(),
                        request.expectedAttachmentId(),
                        request.expectedType(),
                        new ServerInstallHost(
                                player,
                                gunStack,
                                gun,
                                source,
                                locator.slot(),
                                request.gunSlotIndex()
                        )
                );

        if (!result.succeeded()) {
            // Even an extraction that throws may have changed a backpack. Persist
            // the captured source without retrying extraction or inventing a refund.
            synchronize(player, source);
            logOutcome(player, source, locator.slot(), result);
            reject(player);
            return;
        }

        ItemStack installed = result.installed();
        AttachmentType installedType =
                IAttachment.getIAttachmentOrNull(installed) != null
                        ? IAttachment.getIAttachmentOrNull(installed)
                        .getType(installed)
                        : AttachmentType.NONE;

        RefitExternalInstallCommitSequence.complete(
                new PostCommitSteps(
                        player,
                        gunStack,
                        gun,
                        source,
                        installedType,
                        result.replaced()
                ),
                failure -> LOGGER.error(
                        "Refit external install post-commit failure for "
                                + "player {}; the committed gun and "
                                + "attachment ownership state was retained",
                        player.getGameProfile().getName(),
                        failure
                )
        );
    }

    /**
     * Post-commit effects for an ownership-finalized external install.
     *
     * <p>This type intentionally has no access to extraction or compensation
     * operations. A post-commit failure must never repeat or compensate the
     * completed installation.
     */
    private static final class PostCommitSteps
            implements RefitExternalInstallCommitSequence.Steps {
        private final ServerPlayer player;
        private final ItemStack gunStack;
        private final IGun gun;
        private final CraftingItemSource source;
        private final AttachmentType installedType;
        private final ItemStack replaced;

        private PostCommitSteps(
                ServerPlayer player,
                ItemStack gunStack,
                IGun gun,
                CraftingItemSource source,
                AttachmentType installedType,
                ItemStack replaced
        ) {
            this.player = player;
            this.gunStack = gunStack;
            this.gun = gun;
            this.source = source;
            this.installedType = installedType;
            this.replaced = replaced;
        }

        @Override
        public void finalizeOwnership() {
            returnReplacedAttachment(
                    this.player,
                    this.replaced
            );
        }

        @Override
        public void postChangeEvent() {
            AttachmentPropertyManager.postChangeEvent(
                    this.player,
                    this.gunStack
            );
        }

        @Override
        public void dropAmmo() {
            if (this.installedType == AttachmentType.EXTENDED_MAG) {
                this.gun.dropAllAmmo(
                        this.player,
                        this.gunStack
                );
            }
        }

        @Override
        public void synchronizeAuthoritativeState() {
            synchronize(
                    this.player,
                    this.source
            );
        }

        @Override
        public void refreshScreen() {
            LiberateAttachmentService.refreshRefitScreen(
                    this.player
            );
        }
    }

    private static void logOutcome(
            ServerPlayer player,
            CraftingItemSource source,
            int slot,
            RefitExternalInstallTransaction.Result result
    ) {
        switch (result.outcome()) {
            case MUTATION_FAILED_COMPENSATED -> LOGGER.error(
                    "Gun refit mutation failed after external extraction "
                            + "for player {}; gun state restored and source "
                            + "{} slot {} refilled",
                    player.getGameProfile().getName(),
                    source.key(),
                    slot
            );
            case MUTATION_FAILED_UNKNOWN -> LOGGER.error(
                    "Gun refit mutation failed and the gun state could not be "
                            + "confirmed for player {}; the extracted "
                            + "attachment is left in place to avoid "
                            + "duplicating it",
                    player.getGameProfile().getName()
            );
            default -> {
                // Ordinary rejection; nothing was committed.
            }
        }
    }

    /**
     * Adapts the real player, gun and source to the install rules.
     */
    private static final class ServerInstallHost
            implements RefitExternalInstallTransaction.Host {
        private final ServerPlayer player;
        private final ItemStack gunStack;
        private final IGun gun;
        private final CraftingItemSource source;
        private final int sourceSlot;
        private final int gunSlotIndex;
        private final ItemStack snapshot;

        private ServerInstallHost(
                ServerPlayer player,
                ItemStack gunStack,
                IGun gun,
                CraftingItemSource source,
                int sourceSlot,
                int gunSlotIndex
        ) {
            this.player = player;
            this.gunStack = gunStack;
            this.gun = gun;
            this.source = source;
            this.sourceSlot = sourceSlot;
            this.gunSlotIndex = gunSlotIndex;
            /*
             * Owned pre-mutation state, captured before the install attempt.
             */
            this.snapshot = gunStack.copy();
        }

        @Override
        public RefitExternalInstallTransaction.GunMutation gun() {
            return new RefitExternalInstallTransaction.GunMutation() {
                @Override
                public boolean hasAttachmentLock() {
                    return ServerInstallHost.this.gun
                            .hasAttachmentLock(
                                    ServerInstallHost.this.gunStack
                            );
                }

                @Override
                public boolean allowsAttachment(ItemStack candidate) {
                    return ServerInstallHost.this.gun
                            .allowAttachment(
                                    ServerInstallHost.this.gunStack,
                                    candidate
                            );
                }

                @Override
                public ItemStack getAttachment(AttachmentType type) {
                    return ServerInstallHost.this.gun.getAttachment(
                            ServerInstallHost.this.gunStack,
                            type
                    );
                }

                @Override
                public void installAttachment(ItemStack attachment) {
                    ServerInstallHost.this.gun.installAttachment(
                            ServerInstallHost.this.gunStack,
                            attachment
                    );
                }
            };
        }

        @Override
        public ItemStack ownedSnapshot() {
            return this.snapshot;
        }

        @Override
        public boolean restoreOwnedSnapshot(ItemStack ownedSnapshot) {
            if (ItemStack.matches(this.gunStack, ownedSnapshot)) {
                return true;
            }

            try {
                this.player.getInventory().setItem(
                        this.gunSlotIndex,
                        ownedSnapshot.copy()
                );
                this.player.inventoryMenu.broadcastChanges();
            } catch (RuntimeException exception) {
                LOGGER.error(
                        "Refit gun snapshot restore failed for player {}",
                        this.player.getGameProfile().getName(),
                        exception
                );
                return false;
            }

            if (!LiberateAttachmentService.isValidIndex(
                    this.player.getInventory(),
                    this.gunSlotIndex
            )) {
                return false;
            }
            return ItemStack.matches(
                    this.player.getInventory()
                            .getItem(this.gunSlotIndex),
                    ownedSnapshot
            );
        }

        @Override
        public boolean returnExtractionToSource(ItemStack extracted) {
            return rollbackExtraction(
                    this.player,
                    this.source,
                    this.sourceSlot,
                    extracted
            );
        }
    }

    private static void returnReplacedAttachment(
            ServerPlayer player,
            ItemStack replaced
    ) {
        if (replaced.isEmpty()) {
            return;
        }

        ItemStack remainder = replaced.copy();
        boolean added = player.getInventory().add(remainder);
        if (!added) {
            player.drop(remainder, false);
        }
    }

    private static boolean rollbackExtraction(
            ServerPlayer player,
            CraftingItemSource source,
            int originalSlot,
            ItemStack extracted
    ) {
        SafeSourceInsert.Result insertion =
                insertSafely(
                        source,
                        originalSlot,
                        extracted,
                        "refit-original-slot"
                );

        if (!insertion.known()) {
            LOGGER.error(
                    "Refit rollback insertion state is unknown for source {} "
                            + "slot {}; refusing another insertion to avoid "
                            + "duplicating the attachment",
                    source.key(),
                    originalSlot
            );
            synchronize(player, source);
            return false;
        }

        ItemStack remainder = insertion.remainder();

        if (!remainder.isEmpty()) {
            insertion = insertIntoOtherSlots(
                    source,
                    originalSlot,
                    remainder
            );

            if (!insertion.known()) {
                LOGGER.error(
                        "Refit source fallback insertion state is unknown for {}",
                        source.key()
                );
                synchronize(player, source);
                return false;
            }

            remainder = insertion.remainder();
        }

        if (!remainder.isEmpty()) {
            PlayerInventorySource playerSource =
                    new PlayerInventorySource(player);

            insertion = insertIntoOtherSlots(
                    playerSource,
                    -1,
                    remainder
            );

            if (!insertion.known()) {
                LOGGER.error(
                        "Refit player fallback insertion state is unknown for {}",
                        player.getGameProfile().getName()
                );
                synchronize(player, source);
                return false;
            }

            remainder = insertion.remainder();
        }

        if (!remainder.isEmpty()) {
            try {
                if (player.drop(remainder.copy(), false) == null) {
                    LOGGER.error(
                            "Refit rollback could not persist remainder {} x{} "
                                    + "for player {}",
                            remainder.getHoverName().getString(),
                            remainder.getCount(),
                            player.getGameProfile().getName()
                    );
                    synchronize(player, source);
                    return false;
                }
            } catch (RuntimeException failure) {
                LOGGER.error(
                        "Refit rollback drop failed for player {}",
                        player.getGameProfile().getName(),
                        failure
                );
                synchronize(player, source);
                return false;
            }
        }

        synchronize(player, source);
        return true;
    }

    private static SafeSourceInsert.Result insertSafely(
            CraftingItemSource source,
            int slot,
            ItemStack stack,
            String operation
    ) {
        SafeSourceInsert.Result result =
                SafeSourceInsert.commit(
                        source,
                        slot,
                        stack
                );

        if (!result.known()) {
            LOGGER.error(
                    "Refit rollback {} entered unknown mutation state "
                            + "for source {} slot {}",
                    operation,
                    source.key(),
                    slot,
                    result.failure()
            );
        }

        return result;
    }

    private static SafeSourceInsert.Result insertIntoOtherSlots(
            CraftingItemSource source,
            int excludedSlot,
            ItemStack stack
    ) {
        ItemStack remainder = stack.copy();

        int slots;
        try {
            slots = source.slotCount();
        } catch (RuntimeException | LinkageError failure) {
            LOGGER.error(
                    "Refit rollback could not read slot count for {}",
                    source.key(),
                    failure
            );

            /*
             * This was a read-only failure. No insertion occurred, so the
             * exact remainder is still known and a higher fallback is safe.
             */
            return SafeSourceInsert.Result.known(remainder);
        }

        for (int slot = 0;
             slot < slots && !remainder.isEmpty();
             slot++) {
            if (slot == excludedSlot) {
                continue;
            }

            SafeSourceInsert.Result result =
                    insertSafely(
                            source,
                            slot,
                            remainder,
                            "refit-other-slot"
                    );

            if (!result.known()) {
                return result;
            }

            remainder = result.remainder();
        }

        return SafeSourceInsert.Result.known(remainder);
    }

    private static void synchronize(
            ServerPlayer player,
            CraftingItemSource source
    ) {
        try {
            source.markChanged();
        } catch (RuntimeException | LinkageError failure) {
            LOGGER.error(
                    "Refit source markChanged failed",
                    failure
            );
        }

        try {
            source.synchronize(player);
        } catch (RuntimeException | LinkageError failure) {
            LOGGER.error(
                    "Refit source synchronize failed",
                    failure
            );
        }

        try {
            player.inventoryMenu.broadcastChanges();
        } catch (RuntimeException | LinkageError failure) {
            LOGGER.error(
                    "Refit player inventory sync failed",
                    failure
            );
        }
    }

    private static void reject(ServerPlayer player) {
        LiberateAttachmentService.refreshRefitScreen(player);
    }
}
