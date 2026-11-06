package com.mafuyu404.taczaddon.refit;

import com.mafuyu404.taczaddon.storage.api.CraftingItemSource;
import com.mafuyu404.taczaddon.storage.api.CraftingSourceKey;
import com.mafuyu404.taczaddon.storage.api.BackpackItemSource;
import net.minecraftforge.items.ItemStackHandler;
import com.mafuyu404.taczaddon.testutil.MinecraftTestBootstrap;
import com.tacz.guns.api.item.IAttachment;
import com.tacz.guns.api.item.attachment.AttachmentType;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Behavioural contract for the external attachment install rules.
 *
 * Only the container and gun surfaces are faked; the extraction, identity and
 * compensation logic under test is the production code.
 */
class RefitExternalInstallTransactionTest {
    @Test
    void backpackInstallAndCompensationConserveAttachments() {
        for (boolean failGun : new boolean[] {false, true}) {
            ItemStackHandler handler = new ItemStackHandler(1);
            handler.setStackInSlot(0, attachmentStack().copyWithCount(2));
            AtomicInteger savedCount = new AtomicInteger(-1);
            var source = new BackpackItemSource(
                    new CraftingSourceKey.Backpack(UUID.randomUUID(), "main", "", 3, UUID.randomUUID()),
                    handler, () -> true,
                    () -> savedCount.set(handler.getStackInSlot(0).getCount()), () -> {});
            FakeGun gun = new FakeGun();
            gun.failAfterInstalling = failGun;
            var result = RefitExternalInstallTransaction.execute(source, 0, ATTACHMENT_ID,
                    ATTACHMENT_TYPE, new FakeHost(gun, source));
            source.markChanged();
            assertEquals(failGun ? RefitExternalInstallTransaction.Outcome.MUTATION_FAILED_COMPENSATED
                    : RefitExternalInstallTransaction.Outcome.INSTALLED, result.outcome());
            assertEquals(failGun ? 2 : 1, savedCount.get());
            assertEquals(2, handler.getStackInSlot(0).getCount() + gun.installed.size());
        }
    }

    @Test
    void backpackRemovedBetweenSimulationAndExtractionCannotInstall() {
        AtomicBoolean available = new AtomicBoolean(true);
        ItemStackHandler handler = new ItemStackHandler(1) {
            @Override public ItemStack extractItem(int slot, int amount, boolean simulate) {
                ItemStack result = super.extractItem(slot, amount, simulate);
                if (simulate) available.set(false);
                return result;
            }
        };
        handler.setStackInSlot(0, attachmentStack());
        var source = new BackpackItemSource(
                new CraftingSourceKey.Backpack(UUID.randomUUID(), "main", "", 3, UUID.randomUUID()),
                handler, available::get, () -> {}, () -> {});
        FakeGun gun = new FakeGun();
        var result = RefitExternalInstallTransaction.execute(source, 0, ATTACHMENT_ID,
                ATTACHMENT_TYPE, new FakeHost(gun, source));
        assertFalse(result.succeeded());
        assertEquals(1, handler.getStackInSlot(0).getCount());
        assertTrue(gun.installed.isEmpty());
    }

    @Test
    void failingBackpackExtractionPersistsCapturedBackendWithoutInventingRefund() {
        AtomicBoolean available = new AtomicBoolean(true);
        AtomicInteger saves = new AtomicInteger();
        ItemStackHandler handler = new ItemStackHandler(1) {
            @Override public ItemStack extractItem(int slot, int amount, boolean simulate) {
                ItemStack result = super.extractItem(slot, amount, simulate);
                if (!simulate) {
                    available.set(false);
                    throw new IllegalStateException("backend failed after extraction");
                }
                return result;
            }
        };
        handler.setStackInSlot(0, attachmentStack().copyWithCount(2));
        var source = new BackpackItemSource(
                new CraftingSourceKey.Backpack(UUID.randomUUID(), "main", "", 3, UUID.randomUUID()),
                handler, available::get, saves::incrementAndGet, () -> {});
        FakeGun gun = new FakeGun();
        var result = RefitExternalInstallTransaction.execute(source, 0, ATTACHMENT_ID,
                ATTACHMENT_TYPE, new FakeHost(gun, source));
        source.markChanged();
        assertEquals(RefitExternalInstallTransaction.Outcome.MUTATION_FAILED_UNKNOWN, result.outcome());
        assertEquals(1, handler.getStackInSlot(0).getCount());
        assertEquals(1, saves.get());
        assertTrue(gun.installed.isEmpty());
    }
    private static final ResourceLocation ATTACHMENT_ID =
            ResourceLocation.tryBuild("taczaddon_test", "fake_scope");
    private static final AttachmentType ATTACHMENT_TYPE =
            AttachmentType.SCOPE;

    private static FakeAttachmentItem ATTACHMENT_ITEM;

    @BeforeAll
    static void bootstrap() throws Exception {
        MinecraftTestBootstrap.prepare();
        ATTACHMENT_ITEM = new FakeAttachmentItem();
        ResourceLocation itemId = ResourceLocation.tryBuild(
                "taczaddon_test",
                "fake_attachment"
        );
        if (!net.minecraftforge.registries.ForgeRegistries.ITEMS
                .containsKey(itemId)) {
            net.minecraftforge.registries.ForgeRegistries.ITEMS
                    .register(itemId, ATTACHMENT_ITEM);
        }
    }

    @Test
    void liveContainerSlotThatEmptiesOnSplitStillExtractsOneItem() {
        FakeSource source = new FakeSource(
                SlotMode.LIVE_OBJECT,
                attachmentStack()
        );

        FakeGun gun = new FakeGun();
        FakeHost host = new FakeHost(gun, source);

        RefitExternalInstallTransaction.Result result = execute(source, host);

        assertEquals(
                RefitExternalInstallTransaction.Outcome.INSTALLED,
                result.outcome()
        );
        assertEquals(1, gun.installed.size());
        assertEquals(0, source.slot(0).getCount());
        assertEquals(0, source.returned.size());
    }

    @Test
    void replacingVirtualOemAttachmentDoesNotCreateAnOwnedItem() {
        for (boolean virtual : new boolean[]{false, true}) {
            FakeSource source = new FakeSource(SlotMode.LIVE_OBJECT, attachmentStack());
            FakeGun gun = new FakeGun();
            gun.previousAttachment = attachmentStack();
            gun.previousAttachment.getOrCreateTag().putBoolean("VirtualOemAttachment", virtual);
            var result = execute(source, new FakeHost(gun, source));
            assertEquals(RefitExternalInstallTransaction.Outcome.INSTALLED, result.outcome());
            assertEquals(virtual, result.replaced().isEmpty());
            assertEquals(1, gun.installed.size());
            assertTrue(source.slot(0).isEmpty());
        }
    }

    @Test
    void replacingSlotObjectHandlerStillExtractsOneItem() {
        FakeSource source = new FakeSource(
                SlotMode.REPLACE_OBJECT,
                attachmentStack()
        );
        source.setSlotCount(0, 2);

        FakeGun gun = new FakeGun();
        FakeHost host = new FakeHost(gun, source);

        RefitExternalInstallTransaction.Result result = execute(source, host);

        assertEquals(
                RefitExternalInstallTransaction.Outcome.INSTALLED,
                result.outcome()
        );
        assertEquals(1, gun.installed.size());
        assertEquals(
                1,
                source.slot(0).getCount(),
                "the second attachment stays in the container"
        );
    }

    @Test
    void realExtractionRuntimeFailureAfterMutationIsUnknown() {
        FakeSource source = new FakeSource(
                SlotMode.REPLACE_OBJECT,
                attachmentStack()
        );
        source.runtimeFailureAfterRealExtraction = true;

        FakeGun gun = new FakeGun();
        FakeHost host =
                new FakeHost(
                        gun,
                        source
                );

        RefitExternalInstallTransaction.Result result =
                execute(
                        source,
                        host
                );

        assertEquals(
                RefitExternalInstallTransaction.Outcome
                        .MUTATION_FAILED_UNKNOWN,
                result.outcome()
        );

        assertFalse(
                result.extractionReturned()
        );

        assertEquals(
                0,
                source.slot(0).getCount(),
                "the handler already removed the item before throwing"
        );

        assertTrue(
                source.returned.isEmpty(),
                "an unknown real extraction must never reconstruct and return a guessed attachment"
        );

        assertTrue(
                gun.installed.isEmpty()
        );
    }

    @Test
    void realExtractionLinkageFailureAfterMutationIsUnknown() {
        FakeSource source = new FakeSource(
                SlotMode.REPLACE_OBJECT,
                attachmentStack()
        );
        source.linkageFailureAfterRealExtraction = true;

        FakeGun gun = new FakeGun();
        FakeHost host =
                new FakeHost(
                        gun,
                        source
                );

        RefitExternalInstallTransaction.Result result =
                execute(
                        source,
                        host
                );

        assertEquals(
                RefitExternalInstallTransaction.Outcome
                        .MUTATION_FAILED_UNKNOWN,
                result.outcome()
        );

        assertFalse(
                result.extractionReturned()
        );

        assertEquals(
                0,
                source.slot(0).getCount()
        );

        assertTrue(
                source.returned.isEmpty()
        );

        assertTrue(
                gun.installed.isEmpty()
        );
    }

    @Test
    void singleItemSlotAndMultiItemSlotBothInstall() {
        for (int count : new int[] {1, 2}) {
            FakeSource source = new FakeSource(
                    SlotMode.REPLACE_OBJECT,
                    attachmentStack()
            );
            if (count == 2) {
                source.setSlotCount(0, 2);
            }

            FakeGun gun = new FakeGun();
            FakeHost host = new FakeHost(gun, source);

            assertEquals(
                    RefitExternalInstallTransaction.Outcome.INSTALLED,
                    execute(source, host).outcome(),
                    "slot with " + count + " items must install"
            );
            assertEquals(1, gun.installed.size());
        }
    }

    @Test
    void identityChangeIsRejectedWithoutTouchingTheSlot() {
        FakeSource source = new FakeSource(
                SlotMode.REPLACE_OBJECT,
                attachmentStack()
        );
        FakeGun gun = new FakeGun();
        FakeHost host = new FakeHost(gun, source);

        RefitExternalInstallTransaction.Result result =
                RefitExternalInstallTransaction.execute(
                        source,
                        0,
                        ResourceLocation.tryBuild(
                                "taczaddon_test",
                                "different_scope"
                        ),
                        ATTACHMENT_TYPE,
                        host
                );

        assertEquals(
                RefitExternalInstallTransaction.Outcome.REJECTED_IDENTITY,
                result.outcome()
        );
        assertEquals(1, source.slot(0).getCount());
        assertTrue(gun.installed.isEmpty());
    }

    @Test
    void simulateAndRealExtractionMismatchIsRejectedAndCompensated() {
        FakeSource source = new FakeSource(
                SlotMode.REPLACE_OBJECT,
                attachmentStack()
        );
        source.shortSimulation = true;

        FakeGun gun = new FakeGun();
        FakeHost host = new FakeHost(gun, source);

        RefitExternalInstallTransaction.Result result = execute(source, host);

        assertEquals(
                RefitExternalInstallTransaction.Outcome.REJECTED_EXTRACTION,
                result.outcome()
        );
        assertTrue(gun.installed.isEmpty());
        assertEquals(
                1,
                source.slot(0).getCount(),
                "nothing may be consumed on a rejected install"
        );
    }

    @Test
    void controlledMutationFailureRestoresGunAndReturnsExtractionOnce() {
        FakeSource source = new FakeSource(
                SlotMode.REPLACE_OBJECT,
                attachmentStack()
        );

        FakeGun gun = new FakeGun();
        gun.failAfterInstalling = true;
        FakeHost host = new FakeHost(gun, source);

        RefitExternalInstallTransaction.Result result = execute(source, host);

        assertEquals(
                RefitExternalInstallTransaction.Outcome
                        .MUTATION_FAILED_COMPENSATED,
                result.outcome()
        );
        assertTrue(result.extractionReturned());
        assertTrue(gun.installed.isEmpty());
        assertEquals(
                1,
                source.returned.size(),
                "the extracted attachment is returned exactly once"
        );
        assertEquals(
                1,
                source.slot(0).getCount(),
                "no duplicate attachment appears"
        );
        assertEquals(1, host.restoreAttempts);
    }

    @Test
    void unverifiableGunRestoreDoesNotDuplicateTheAttachment() {
        FakeSource source = new FakeSource(
                SlotMode.REPLACE_OBJECT,
                attachmentStack()
        );

        FakeGun gun = new FakeGun();
        gun.failAfterInstalling = true;
        FakeHost host = new FakeHost(gun, source);
        host.restoreSucceeds = false;

        RefitExternalInstallTransaction.Result result = execute(source, host);

        assertEquals(
                RefitExternalInstallTransaction.Outcome
                        .MUTATION_FAILED_UNKNOWN,
                result.outcome()
        );
        assertFalse(result.extractionReturned());
        assertEquals(
                1,
                gun.installed.size(),
                "the gun keeps the attachment it may already carry"
        );
        assertTrue(
                source.returned.isEmpty(),
                "an unverified state must not be reported as rolled back"
        );
    }

    @Test
    void failedSourceCompensationIsNeverReportedAsCompensated() {
        FakeSource source = new FakeSource(
                SlotMode.REPLACE_OBJECT,
                attachmentStack()
        );

        FakeGun gun = new FakeGun();
        gun.failAfterInstalling = true;
        FakeHost host = new FakeHost(gun, source);
        host.sourceReturnSucceeds = false;

        RefitExternalInstallTransaction.Result result = execute(source, host);

        assertEquals(
                RefitExternalInstallTransaction.Outcome
                        .MUTATION_FAILED_UNKNOWN,
                result.outcome(),
                "an unverifiable return must not claim compensation"
        );
        assertFalse(result.extractionReturned());
        assertEquals(
                0,
                source.returned.size(),
                "the failed compensation must not be counted as returned"
        );
    }

    @Test
    void linkageErrorAfterInstallFollowsTheSameCompensationProtocol() {
        FakeSource source = new FakeSource(
                SlotMode.REPLACE_OBJECT,
                attachmentStack()
        );

        FakeGun gun = new FakeGun();
        gun.linkageFailureAfterInstalling = true;
        FakeHost host = new FakeHost(gun, source);

        RefitExternalInstallTransaction.Result result = execute(source, host);

        assertEquals(
                RefitExternalInstallTransaction.Outcome
                        .MUTATION_FAILED_COMPENSATED,
                result.outcome()
        );
        assertTrue(result.extractionReturned());
        assertTrue(gun.installed.isEmpty());
        assertEquals(1, source.returned.size());
        assertEquals(1, source.slot(0).getCount());
    }

    @Test
    void unverifiedExtractionMismatchReturnIsNotReportedAsRejected() {
        FakeSource source = new FakeSource(
                SlotMode.REPLACE_OBJECT,
                attachmentStack()
        );
        source.mismatchRealExtraction = true;

        FakeGun gun = new FakeGun();
        FakeHost host = new FakeHost(gun, source);
        host.sourceReturnSucceeds = false;

        RefitExternalInstallTransaction.Result result = execute(source, host);

        assertEquals(
                RefitExternalInstallTransaction.Outcome
                        .MUTATION_FAILED_UNKNOWN,
                result.outcome()
        );
        assertFalse(result.extractionReturned());
    }

    private static RefitExternalInstallTransaction.Result execute(
            FakeSource source,
            FakeHost host
    ) {
        return RefitExternalInstallTransaction.execute(
                source,
                0,
                ATTACHMENT_ID,
                ATTACHMENT_TYPE,
                host
        );
    }

    private static ItemStack attachmentStack() {
        return new ItemStack(ATTACHMENT_ITEM);
    }

    private enum SlotMode {
        /** Vanilla style: getStackInSlot hands out the live mutable stack. */
        LIVE_OBJECT,
        /** Handler style: every access returns a fresh object. */
        REPLACE_OBJECT
    }

    private static final class FakeSource implements CraftingItemSource {
        private final SlotMode mode;
        private final List<ItemStack> slots = new ArrayList<>();
        private final List<ItemStack> returned = new ArrayList<>();
        private boolean shortSimulation;
        private boolean mismatchRealExtraction;
        private boolean runtimeFailureAfterRealExtraction;
        private boolean linkageFailureAfterRealExtraction;

        private FakeSource(SlotMode mode, ItemStack... stacks) {
            this.mode = mode;
            for (ItemStack stack : stacks) {
                this.slots.add(stack.copy());
            }
        }

        ItemStack slot(int index) {
            return this.slots.get(index);
        }

        void setSlotCount(int index, int count) {
            this.slots.set(
                    index,
                    this.slots.get(index).copyWithCount(count)
            );
        }

        @Override
        public CraftingSourceKey key() {
            return new CraftingSourceKey.BlockEntity(
                    null,
                    new BlockPos(1, 0, 0)
            );
        }

        @Override
        public Object backendIdentity() {
            return this;
        }

        @Override
        public int slotCount() {
            return this.slots.size();
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            return this.mode == SlotMode.LIVE_OBJECT
                    ? this.slots.get(slot)
                    : this.slots.get(slot).copy();
        }

        @Override
        public ItemStack extractItem(
                int slot,
                int amount,
                boolean simulate
        ) {
            ItemStack stack =
                    this.slots.get(slot);

            if (stack.isEmpty()
                    || amount <= 0) {
                return ItemStack.EMPTY;
            }

            int extracted =
                    Math.min(
                            amount,
                            stack.getCount()
                    );

            if (simulate
                    && this.shortSimulation) {
                return ItemStack.EMPTY;
            }

            ItemStack resultStack =
                    stack.copyWithCount(
                            extracted
                    );

            if (!simulate) {
                if (this.mismatchRealExtraction) {
                    return new ItemStack(
                            Items.STICK,
                            extracted
                    );
                }

                int remaining =
                        stack.getCount()
                                - extracted;

                if (this.mode
                        == SlotMode.LIVE_OBJECT) {
                    /*
                     * Vanilla-style live reference mutation.
                     */
                    stack.setCount(
                            remaining
                    );
                } else {
                    this.slots.set(
                            slot,
                            remaining <= 0
                                    ? ItemStack.EMPTY
                                    : stack.copyWithCount(
                                    remaining
                            )
                    );
                }

                /*
                 * Simulate a third-party handler that has already committed its
                 * inventory mutation before the call fails.
                 */
                if (this.linkageFailureAfterRealExtraction) {
                    throw new NoSuchMethodError(
                            "external inventory ABI failed after extraction"
                    );
                }

                if (this.runtimeFailureAfterRealExtraction) {
                    throw new IllegalStateException(
                            "external inventory failed after extraction"
                    );
                }
            }

            return resultStack;
        }

        @Override
        public ItemStack insertItem(
                int slot,
                ItemStack stack,
                boolean simulate
        ) {
            if (simulate) {
                return ItemStack.EMPTY;
            }
            this.slots.set(slot, stack.copy());
            this.returned.add(stack.copy());
            return ItemStack.EMPTY;
        }

        @Override
        public boolean isValid(ServerPlayer player) {
            return true;
        }

        @Override
        public void markChanged() {
        }

        @Override
        public void synchronize(ServerPlayer player) {
        }
    }

    private static final class FakeGun
            implements RefitExternalInstallTransaction.GunMutation {
        private ItemStack previousAttachment = ItemStack.EMPTY;
        private final List<ItemStack> installed = new ArrayList<>();
        private boolean failAfterInstalling;
        private boolean linkageFailureAfterInstalling;

        @Override
        public boolean hasAttachmentLock() {
            return false;
        }

        @Override
        public boolean allowsAttachment(ItemStack candidate) {
            return true;
        }

        @Override
        public ItemStack getAttachment(AttachmentType type) {
            return previousAttachment;
        }

        @Override
        public void installAttachment(ItemStack attachment) {
            this.installed.add(attachment.copy());
            if (this.linkageFailureAfterInstalling) {
                throw new NoSuchMethodError(
                        "third-party gun ABI mismatch"
                );
            }
            if (this.failAfterInstalling) {
                throw new IllegalStateException(
                        "controlled mutation failed"
                );
            }
        }
    }

    private static final class FakeHost
            implements RefitExternalInstallTransaction.Host {
        private final FakeGun gun;
        private final CraftingItemSource source;
        private ItemStack snapshot = ItemStack.EMPTY;
        private boolean restoreSucceeds = true;
        private boolean sourceReturnSucceeds = true;
        private int restoreAttempts;

        private FakeHost(FakeGun gun, CraftingItemSource source) {
            this.gun = gun;
            this.source = source;
        }

        @Override
        public RefitExternalInstallTransaction.GunMutation gun() {
            return this.gun;
        }

        @Override
        public ItemStack ownedSnapshot() {
            this.snapshot = new ItemStack(ATTACHMENT_ITEM);
            return this.snapshot;
        }

        @Override
        public boolean restoreOwnedSnapshot(ItemStack snapshot) {
            this.restoreAttempts++;
            assertSame(this.snapshot, snapshot);
            if (this.restoreSucceeds) {
                this.gun.installed.clear();
            }
            return this.restoreSucceeds;
        }

        @Override
        public boolean returnExtractionToSource(ItemStack extracted) {
            if (!this.sourceReturnSucceeds) {
                return false;
            }

            this.source.insertItem(0, extracted, false);
            return true;
        }
    }

    private static final class FakeAttachmentItem
            extends Item implements IAttachment {
        private FakeAttachmentItem() {
            super(new Item.Properties());
        }

        @Override
        public ResourceLocation getAttachmentId(ItemStack stack) {
            return ATTACHMENT_ID;
        }

        @Override
        public void setAttachmentId(
                ItemStack stack,
                ResourceLocation attachmentId
        ) {
        }

        @Override
        public ResourceLocation getSkinId(ItemStack stack) {
            return null;
        }

        @Override
        public void setSkinId(
                ItemStack stack,
                ResourceLocation skinId
        ) {
        }

        @Override
        public int getZoomNumber(ItemStack stack) {
            return 0;
        }

        @Override
        public void setZoomNumber(ItemStack stack, int zoomNumber) {
        }

        @Override
        public AttachmentType getType(ItemStack stack) {
            return ATTACHMENT_TYPE;
        }

        @Override
        public boolean hasCustomLaserColor(ItemStack stack) {
            return false;
        }

        @Override
        public int getLaserColor(ItemStack stack) {
            return 0;
        }

        @Override
        public void setLaserColor(ItemStack stack, int color) {
        }
    }
}
