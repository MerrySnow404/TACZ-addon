package com.mafuyu404.taczaddon.client.camera;

import net.minecraft.client.CameraType;

import javax.annotation.Nullable;

public record AimCameraContext(
        CameraType vanillaCameraType,
        boolean shoulderSurfingActive,
        @Nullable String perspectiveApiId
) {
}
