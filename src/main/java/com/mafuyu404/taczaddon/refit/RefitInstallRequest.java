package com.mafuyu404.taczaddon.refit;

import com.mafuyu404.taczaddon.storage.api.RefitSourceLocator;

import com.tacz.guns.api.item.attachment.AttachmentType;
import net.minecraft.resources.ResourceLocation;

public record RefitInstallRequest(RefitSourceLocator locator, ResourceLocation expectedAttachmentId,
                                  AttachmentType expectedType, int gunSlotIndex, long snapshotRequestId) {}
