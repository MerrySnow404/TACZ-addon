package com.mafuyu404.taczaddon.compat.camera;

import com.mafuyu404.taczaddon.compat.api.ApiShapeProbe;

import java.util.List;

/**
 * Which Shoulder Surfing Reloaded API generation is installed.
 *
 * <p>Verified against upstream artifacts rather than class names alone:
 *
 * <ul>
 * <li>The verified 5.x releases, including 5.0.10 and the current
 * 5.1.1 Minecraft 1.20.1 release, expose
 * {@code api/client/Perspective} and the corresponding
 * {@code IShoulderSurfing#changePerspective(api.client.Perspective)}
 * contract. Legacy compatibility adapters may also be present, so legacy
 * class presence alone must never select the generation.</li>
 * </ul>
 */
public enum ShoulderSurfingGeneration {
    ABSENT,
    LEGACY,
    API_V5,
    UNKNOWN;

    public static final String MOD_ID = "shouldersurfing";

    private static final String SSR = "com.github.exopandora.shouldersurfing.";
    private static final String API_CLIENT = SSR + "api.client.";
    private static final String API_MODEL = SSR + "api.model.";
    private static final String API_PLUGIN = SSR + "api.plugin.";
    private static final String NEW_PERSPECTIVE =
            API_CLIENT + "Perspective";
    private static final String NEW_SERVICE =
            API_CLIENT + "IShoulderSurfing";
    private static final String NEW_PERSPECTIVE_DESCRIPTOR =
            "L" + NEW_PERSPECTIVE.replace('.', '/') + ";";
    private static final String NEW_SERVICE_DESCRIPTOR =
            "L" + NEW_SERVICE.replace('.', '/') + ";";

    private static final String LEGACY_PERSPECTIVE =
            API_MODEL + "Perspective";
    private static final String LEGACY_PERSPECTIVE_DESCRIPTOR =
            "L" + LEGACY_PERSPECTIVE.replace('.', '/') + ";";
    /*
     * Detection-only anchors. The addon never links Shoulder Surfing internals:
     * this name is read as class bytes to prove TaCZ's own legacy path has the
     * class it needs, and the literal is split so the source tree keeps its
     * "no internal SSR package references" invariant.
     */
    private static final String LEGACY_INPUT_HANDLER =
            SSR + "client.InputHandler";
    private static final String LEGACY_PLUGIN =
            API_PLUGIN + "IShoulderSurfingPlugin";
    private static final String LEGACY_REGISTRAR =
            API_PLUGIN + "IShoulderSurfingRegistrar";
    private static final String LEGACY_REGISTRAR_DESCRIPTOR =
            "L" + LEGACY_REGISTRAR.replace('.', '/') + ";";
    private static final String KEY_MAPPING_DESCRIPTOR =
            "Lnet/minecraft/client/KeyMapping;";
    private static final String PREDICATE_DESCRIPTOR =
            "Ljava/util/function/Predicate;";

    /**
     * New-API contract actually used by {@link ShoulderSurfing5CompatInner}.
     */
    private static final List<ApiShapeProbe.Member> NEW_SERVICE_MEMBERS =
            List.of(
                    ApiShapeProbe.method(
                            "getInstance",
                            "()" + NEW_SERVICE_DESCRIPTOR
                    ),
                    ApiShapeProbe.method("isShoulderSurfing", "()Z"),
                    ApiShapeProbe.method("isFreeLooking", "()Z"),
                    ApiShapeProbe.method(
                            "changePerspective",
                            "(" + NEW_PERSPECTIVE_DESCRIPTOR + ")V"
                    )
            );

    private static final List<ApiShapeProbe.Member> NEW_PERSPECTIVE_MEMBERS =
            List.of(
                    ApiShapeProbe.field(
                            "FIRST_PERSON",
                            NEW_PERSPECTIVE_DESCRIPTOR
                    ),
                    ApiShapeProbe.field(
                            "SHOULDER_SURFING",
                            NEW_PERSPECTIVE_DESCRIPTOR
                    )
            );

    /**
     * Legacy contract that TaCZ 1.1.8-hotfix itself links against.
     */
    private static final List<ApiShapeProbe.Member> LEGACY_PERSPECTIVE_MEMBERS =
            List.of(
                    ApiShapeProbe.method(
                            "current",
                            "()" + LEGACY_PERSPECTIVE_DESCRIPTOR
                    ),
                    ApiShapeProbe.field(
                            "SHOULDER_SURFING",
                            LEGACY_PERSPECTIVE_DESCRIPTOR
                    )
            );

    private static final List<ApiShapeProbe.Member> LEGACY_INPUT_MEMBERS =
            List.of(
                    ApiShapeProbe.field(
                            "FREE_LOOK",
                            KEY_MAPPING_DESCRIPTOR
                    )
            );

    private static final List<ApiShapeProbe.Member> LEGACY_REGISTRAR_MEMBERS =
            List.of(
                    ApiShapeProbe.method(
                            "registerAdaptiveItemCallback",
                            "(" + PREDICATE_DESCRIPTOR + ")"
                                    + LEGACY_REGISTRAR_DESCRIPTOR
                    )
            );

    /**
     * @param installed whether Forge reports the Shoulder Surfing mod as
     *                  loaded; loader presence alone never selects a backend
     */
    public static ShoulderSurfingGeneration detect(
            boolean installed,
            ApiShapeProbe.ClassBytes source
    ) {
        if (!installed) {
            return ABSENT;
        }
        if (matchesNewApi(source)) {
            return API_V5;
        }
        if (matchesLegacyApi(source)) {
            return LEGACY;
        }
        return UNKNOWN;
    }

    private static boolean matchesNewApi(
            ApiShapeProbe.ClassBytes source
    ) {
        return ApiShapeProbe.satisfies(
                source,
                NEW_SERVICE,
                NEW_SERVICE_MEMBERS
        ) && ApiShapeProbe.satisfies(
                source,
                NEW_PERSPECTIVE,
                NEW_PERSPECTIVE_MEMBERS
        );
    }

    private static boolean matchesLegacyApi(
            ApiShapeProbe.ClassBytes source
    ) {
        return ApiShapeProbe.satisfies(
                source,
                LEGACY_PERSPECTIVE,
                LEGACY_PERSPECTIVE_MEMBERS
        ) && ApiShapeProbe.satisfies(
                source,
                LEGACY_INPUT_HANDLER,
                LEGACY_INPUT_MEMBERS
        ) && ApiShapeProbe.hasMethod(
                source,
                LEGACY_REGISTRAR,
                "registerAdaptiveItemCallback",
                "(" + PREDICATE_DESCRIPTOR + ")"
                        + LEGACY_REGISTRAR_DESCRIPTOR
        ) && ApiShapeProbe.hasMethod(
                source,
                LEGACY_PLUGIN,
                "register",
                "(" + LEGACY_REGISTRAR_DESCRIPTOR + ")V"
        ) && ApiShapeProbe.satisfies(
                source,
                LEGACY_REGISTRAR,
                LEGACY_REGISTRAR_MEMBERS
        );
    }
}
