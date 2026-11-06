package com.mafuyu404.taczaddon.compat.camera;

import com.mafuyu404.taczaddon.compat.api.ApiShapeProbe;

/**
 * Which Leawind Third Person generation is installed.
 *
 * <p>Verified against the real 1.20.1 Forge artifacts:
 *
 * <ul>
 *     <li>2.2.0 keeps the original {@code com.github.leawind.thirdperson}
 *     package (main class {@code com.github.leawind.thirdperson.ThirdPerson})
 *     and has no {@code perspective_api} dependency at all;</li>
 *     <li>3.0.3-beta+forge-1.20.1 moved to
 *     {@code io.github.leawind.thirdperson}
 *     ({@code io.github.leawind.thirdperson.ThirdPerson}) and declares a
 *     {@code perspective_api} dependency.</li>
 * </ul>
 *
 * <p>Perspective API presence alone therefore cannot identify the generation:
 * a user can install Perspective API next to Leawind 2.x.
 */
public enum LeawindGeneration {
    ABSENT,
    LEAWIND_2,
    LEAWIND_3,
    UNKNOWN;

    /** Package that only the 2.x line uses. */
    static final String LEAWIND_2_ANCHOR =
            "com.github.leawind.thirdperson.ThirdPerson";
    /** Package that only the 3.x line uses. */
    static final String LEAWIND_3_ANCHOR =
            "io.github.leawind.thirdperson.ThirdPerson";

    public static LeawindGeneration detect(
            boolean installed,
            ApiShapeProbe.ClassBytes source
    ) {
        if (!installed) {
            return ABSENT;
        }
        if (ApiShapeProbe.hasClass(source, LEAWIND_3_ANCHOR)) {
            return LEAWIND_3;
        }
        if (ApiShapeProbe.hasClass(source, LEAWIND_2_ANCHOR)) {
            return LEAWIND_2;
        }
        return UNKNOWN;
    }
}
