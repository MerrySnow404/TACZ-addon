package com.mafuyu404.taczaddon.refit;

import com.mafuyu404.taczaddon.compat.tacz.TaczAttachmentCompat;
import com.mafuyu404.taczaddon.storage.api.CraftingItemSource;

import com.tacz.guns.api.item.IAttachment;
import com.tacz.guns.api.item.attachment.AttachmentType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;

/**
 * Extraction and compensation rules for one external attachment install.
 *
 * <p>This is deliberately not a generic transaction engine: it only owns the
 * three decisions that were previously mixed into the packet handler.
 *
 * <ol>
 *     <li>the container identity snapshot is frozen <em>before</em> the
 *     extraction, because {@code getStackInSlot} may hand out the live
 *     container object which reports as empty after a split;</li>
 *     <li>a failed controlled gun mutation only returns the extracted item
 *     once the gun is verified to be back at its pre-mutation snapshot;</li>
 *     <li>unverifiable third-party state is reported, never claimed to be
 *     rolled back.</li>
 * </ol>
 */
public final class RefitExternalInstallTransaction {

    /** The controlled gun mutation surface. */
    public interface GunMutation {
        boolean hasAttachmentLock();

        boolean allowsAttachment(ItemStack candidate);

        ItemStack getAttachment(AttachmentType type);

        /**
         * Installs the extracted attachment.
         *
         * @throws RuntimeException when the controlled mutation fails; the
         *                          gun may already be partially modified
         * @throws LinkageError     when an optional/third-party gun API is
         *                          binary-incompatible; the gun may already
         *                          be partially modified as well
         */
        void installAttachment(ItemStack attachment);
    }

    /**
     * Player/inventory side of the operation.
     */
    public interface Host {
        GunMutation gun();

        /** Owned pre-mutation gun state, taken before the install. */
        ItemStack ownedSnapshot();

        /**
         * @return true only when the gun is verified to match the snapshot
         *         again
         */
        boolean restoreOwnedSnapshot(ItemStack snapshot);

        /**
         * Returns the extracted attachment to the source or a verified
         * fallback.
         *
         * @return true only when compensation completed with a known result.
         *         false means the insertion state became unknown and the
         *         caller must not claim that the extraction was returned.
         */
        boolean returnExtractionToSource(ItemStack extracted);
    }

    public enum Outcome {
        /** The attachment is installed on the gun. */
        INSTALLED,
        /** Slot identity, type, lock or allow-check failed. */
        REJECTED_IDENTITY,
        /** Simulated and real extraction did not agree. */
        REJECTED_EXTRACTION,
        /** The mutation failed while the gun was reverted and the item returned. */
        MUTATION_FAILED_COMPENSATED,
        /**
         * The gun state or the compensation insertion state could not be
         * verified. The operation must not retry the original extracted stack
         * because doing so could duplicate an item that was already partially
         * committed.
         */
        MUTATION_FAILED_UNKNOWN
    }

    public record Result(
            Outcome outcome,
            ItemStack installed,
            ItemStack replaced,
            boolean extractionReturned
    ) {
        public boolean succeeded() {
            return this.outcome == Outcome.INSTALLED;
        }
    }

    private RefitExternalInstallTransaction() {
    }

    public static Result execute(
            CraftingItemSource source,
            int slot,
            @Nullable ResourceLocation expectedAttachmentId,
            @Nullable AttachmentType expectedType,
            Host host
    ) {
        GunMutation gun;

        ItemStack expected;
        IAttachment attachment;
        ResourceLocation actualId;
        AttachmentType actualType;
        ItemStack replaced;
        ItemStack snapshot;

        /*
         * Finish every read-only identity/gun query before the real extraction.
         * If one of these optional/third-party calls is binary-incompatible,
         * nothing has been extracted yet and the operation can be rejected
         * cleanly.
         */
        try {
            gun = host.gun();

            /*
             * Freeze the identity snapshot before anything is extracted.
             */
            expected = source.getStackInSlot(slot).copy();

            attachment =
                    IAttachment.getIAttachmentOrNull(expected);
            if (attachment == null) {
                return rejected(
                        Outcome.REJECTED_IDENTITY
                );
            }

            actualId =
                    attachment.getAttachmentId(expected);
            actualType =
                    attachment.getType(expected);

            if (!LiberateAttachmentService.isValidCandidate(
                    expectedAttachmentId,
                    actualId,
                    expectedType,
                    actualType,
                    gun.hasAttachmentLock(),
                    gun.allowsAttachment(expected)
            )) {
                return rejected(
                        Outcome.REJECTED_IDENTITY
                );
            }

            /*
             * Capture all gun-side state before the external source is really
             * mutated. No fallible gun query should remain between extraction
             * and installAttachment().
             */
            replaced = TaczAttachmentCompat.returnableAttachment(gun.getAttachment(actualType)).copy();
            snapshot = host.ownedSnapshot();
        } catch (RuntimeException | LinkageError failure) {
            return rejected(
                    Outcome.REJECTED_IDENTITY
            );
        }

        ItemStack simulated;
        try {
            simulated =
                    source.extractItem(
                            slot,
                            1,
                            true
                    );
        } catch (RuntimeException | LinkageError failure) {
            /*
             * simulate=true is contractually non-mutating. Abort this request
             * without trying the real extraction.
             */
            return rejected(
                    Outcome.REJECTED_EXTRACTION
            );
        }

        if (!matchesExtracted(simulated, expected)) {
            return rejected(
                    Outcome.REJECTED_EXTRACTION
            );
        }

        ItemStack extracted;
        try {
            extracted =
                    source.extractItem(
                            slot,
                            1,
                            false
                    );
        } catch (RuntimeException | LinkageError failure) {
            /*
             * The real handler may have committed zero, some, or all of the
             * extraction before throwing. There is no reliable remainder object
             * to compensate with, therefore retrying or reconstructing an item
             * would risk duplication.
             */
            return unknownFailure();
        }

        if (!matchesExtracted(extracted, expected)) {
            /*
             * A real mutation call returned an unusable result. If it returned an
             * actual stack, that exact returned stack can be compensated. If it
             * returned empty, we cannot prove that the underlying inventory was
             * not modified.
             */
            if (extracted.isEmpty()) {
                return unknownFailure();
            }

            boolean returned;
            try {
                returned =
                        host.returnExtractionToSource(
                                extracted
                        );
            } catch (RuntimeException | LinkageError failure) {
                return unknownFailure();
            }

            if (!returned) {
                return unknownFailure();
            }

            return rejected(
                    Outcome.REJECTED_EXTRACTION
            );
        }

        try {
            gun.installAttachment(extracted);
        } catch (RuntimeException | LinkageError failure) {
            return compensate(
                    host,
                    snapshot,
                    extracted,
                    failure
            );
        }

        return new Result(
                Outcome.INSTALLED,
                extracted.copy(),
                replaced.copy(),
                false
        );
    }

    private static Result compensate(
            Host host,
            ItemStack snapshot,
            ItemStack extracted,
            Throwable failure
    ) {
        if (!host.restoreOwnedSnapshot(snapshot)) {
            return unknownFailure();
        }

        if (!host.returnExtractionToSource(extracted)) {
            return unknownFailure();
        }

        return new Result(
                Outcome.MUTATION_FAILED_COMPENSATED,
                ItemStack.EMPTY,
                ItemStack.EMPTY,
                true
        );
    }

    private static Result unknownFailure() {
        return new Result(
                Outcome.MUTATION_FAILED_UNKNOWN,
                ItemStack.EMPTY,
                ItemStack.EMPTY,
                false
        );
    }

    private static Result rejected(Outcome outcome) {
        return new Result(
                outcome,
                ItemStack.EMPTY,
                ItemStack.EMPTY,
                false
        );
    }

    static boolean matchesExtracted(
            ItemStack extracted,
            ItemStack expected
    ) {
        return !extracted.isEmpty()
                && extracted.getCount() == 1
                && ItemStack.isSameItemSameTags(extracted, expected);
    }
}
