package com.mafuyu404.taczaddon.refit;

import com.tacz.guns.api.item.attachment.AttachmentType;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LiberateAttachmentServiceTest {
    @Test
    void rejectsNegativeAndPastEndSlots() {
        assertFalse(
                LiberateAttachmentService.isSlotInRange(41, -1)
        );
        assertFalse(
                LiberateAttachmentService.isSlotInRange(41, 41)
        );
        assertFalse(
                LiberateAttachmentService.isSlotInRange(0, 0)
        );
    }

    @Test
    void acceptsOnlySlotsInsideInventory() {
        assertTrue(
                LiberateAttachmentService.isSlotInRange(41, 0)
        );
        assertTrue(
                LiberateAttachmentService.isSlotInRange(41, 40)
        );
    }

    @Test
    void validatesServerResolvedCandidateMetadata() {
        ResourceLocation attachmentId = attachmentId("scope");

        assertTrue(LiberateAttachmentService.isValidCandidate(
                attachmentId,
                attachmentId,
                AttachmentType.SCOPE,
                AttachmentType.SCOPE,
                false,
                true
        ));
    }

    @Test
    void rejectsMissingOrMismatchedCandidateMetadata() {
        ResourceLocation requestedId = attachmentId("scope");
        ResourceLocation differentId = attachmentId("muzzle");

        assertFalse(LiberateAttachmentService.isValidCandidate(
                requestedId,
                null,
                AttachmentType.SCOPE,
                AttachmentType.SCOPE,
                false,
                true
        ));
        assertFalse(LiberateAttachmentService.isValidCandidate(
                requestedId,
                differentId,
                AttachmentType.SCOPE,
                AttachmentType.SCOPE,
                false,
                true
        ));
        assertFalse(LiberateAttachmentService.isValidCandidate(
                requestedId,
                requestedId,
                AttachmentType.SCOPE,
                AttachmentType.MUZZLE,
                false,
                true
        ));
        assertFalse(LiberateAttachmentService.isValidCandidate(
                requestedId,
                requestedId,
                AttachmentType.NONE,
                AttachmentType.NONE,
                false,
                true
        ));
    }

    @Test
    void rejectsLockedOrDisallowedCandidate() {
        ResourceLocation attachmentId = attachmentId("scope");

        assertFalse(LiberateAttachmentService.isValidCandidate(
                attachmentId,
                attachmentId,
                AttachmentType.SCOPE,
                AttachmentType.SCOPE,
                true,
                true
        ));
        assertFalse(LiberateAttachmentService.isValidCandidate(
                attachmentId,
                attachmentId,
                AttachmentType.SCOPE,
                AttachmentType.SCOPE,
                false,
                false
        ));
    }

    @Test
    void acceptsOnlyCurrentMainHandGunSelection() {
        assertTrue(LiberateAttachmentService.isCurrentGunSelection(
                41,
                2,
                2,
                true,
                true
        ));
        assertFalse(LiberateAttachmentService.isCurrentGunSelection(
                41,
                -1,
                2,
                true,
                true
        ));
        assertFalse(LiberateAttachmentService.isCurrentGunSelection(
                41,
                41,
                41,
                true,
                true
        ));
        assertFalse(LiberateAttachmentService.isCurrentGunSelection(
                41,
                1,
                2,
                true,
                true
        ));
        assertFalse(LiberateAttachmentService.isCurrentGunSelection(
                41,
                2,
                2,
                false,
                true
        ));
        assertFalse(LiberateAttachmentService.isCurrentGunSelection(
                41,
                2,
                2,
                true,
                false
        ));
    }

    private static ResourceLocation attachmentId(String path) {
        return Objects.requireNonNull(
                ResourceLocation.tryBuild("tacz", path)
        );
    }
}
