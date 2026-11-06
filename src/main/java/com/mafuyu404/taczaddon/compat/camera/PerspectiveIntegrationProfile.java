package com.mafuyu404.taczaddon.compat.camera;

import com.mafuyu404.taczaddon.compat.api.ApiShapeProbe;

import java.util.List;

/**
 * Runtime profile of the Leawind Third Person + Perspective API integration.
 *
 * <p>Leawind Third Person has two real 1.20.1 generations. Only the 3.x
 * generation depends on {@code perspective_api}; the 2.x generation has no such
 * dependency, and a user may still install Perspective API next to it. The
 * Leawind generation is therefore verified structurally (see
 * {@link LeawindGeneration}) and never inferred from "Perspective API is
 * present". A loader-tolerant mod range must never be reported as
 * "Perspective API compatible".
 *
 * <p>The verified contract is Perspective API 1.5.0-beta
 * ({@code maven.modrinth:LIqveQm1:1.5.0-beta+forge-1.20.1}):
 * {@code PerspectiveAPI.getCurrent()} / {@code isCurrent(String)} /
 * {@code getOverrideChain()}, {@code PerspectiveOverrideChain.register(int,
 * Supplier)}, {@code PerspectiveOverrideRegistration.unregister()} and
 * {@code Perspective#info() -> PerspectiveInfo#id()}.
 */
public enum PerspectiveIntegrationProfile {
    /** Leawind Third Person is not installed. */
    ABSENT,
    /**
     * Leawind 2.x is installed. It coexists safely, but the addon never claims
     * a Perspective API integration for it, even when Perspective API happens
     * to be installed next to it.
     */
    LEAWIND_2_UNSUPPORTED,
    /**
     * Leawind 3.x without a usable Perspective API. Integration unavailable,
     * nothing else affected.
     */
    LEAWIND_3_MISSING_PERSPECTIVE_API,
    /** Leawind plus a structurally verified Perspective API generation. */
    LEAWIND_3_PERSPECTIVE_API_SUPPORTED,
    /** Leawind is installed but its generation is not recognized. */
    UNKNOWN;

    public static final String LEAWIND_MOD_ID = "leawind_third_person";
    public static final String PERSPECTIVE_API_MOD_ID = "perspective_api";

    private static final String API = "io.github.leawind.perspectiveapi.api.";
    private static final String PERSPECTIVE_DESCRIPTOR =
            "Lio/github/leawind/perspectiveapi/api/Perspective;";
    private static final String OVERRIDE_CHAIN_DESCRIPTOR =
            "Lio/github/leawind/perspectiveapi/api/PerspectiveOverrideChain;";
    private static final String OVERRIDE_REGISTRATION_DESCRIPTOR =
            "Lio/github/leawind/perspectiveapi/api/PerspectiveOverrideRegistration;";
    private static final String PERSPECTIVE_INFO_DESCRIPTOR =
            "Lio/github/leawind/perspectiveapi/api/PerspectiveInfo;";

    private static final List<ApiShapeProbe.Member> API_MEMBERS = List.of(
            ApiShapeProbe.method(
                    "getCurrent",
                    "()" + PERSPECTIVE_DESCRIPTOR
            ),
            ApiShapeProbe.method(
                    "isCurrent",
                    "(Ljava/lang/String;)Z"
            ),
            ApiShapeProbe.method(
                    "getOverrideChain",
                    "()" + OVERRIDE_CHAIN_DESCRIPTOR
            )
    );

    private static final List<ApiShapeProbe.Member> PERSPECTIVE_MEMBERS =
            List.of(
                    ApiShapeProbe.method(
                            "info",
                            "()" + PERSPECTIVE_INFO_DESCRIPTOR
                    )
            );

    private static final List<ApiShapeProbe.Member> INFO_MEMBERS =
            List.of(ApiShapeProbe.method("id", "()Ljava/lang/String;"));

    private static final List<ApiShapeProbe.Member> CHAIN_MEMBERS =
            List.of(ApiShapeProbe.method(
                    "register",
                    "(ILjava/util/function/Supplier;)"
                            + OVERRIDE_REGISTRATION_DESCRIPTOR
            ));

    private static final List<ApiShapeProbe.Member> REGISTRATION_MEMBERS =
            List.of(ApiShapeProbe.method("unregister", "()Z"));

    public static PerspectiveIntegrationProfile detect(
            boolean leawindInstalled,
            boolean perspectiveApiInstalled,
            ApiShapeProbe.ClassBytes source
    ) {
        if (!leawindInstalled) {
            return ABSENT;
        }
        LeawindGeneration generation =
                LeawindGeneration.detect(true, source);
        return switch (generation) {
            case LEAWIND_2 -> LEAWIND_2_UNSUPPORTED;
            case LEAWIND_3 -> perspectiveApiInstalled
                    && matchesVerifiedGeneration(source)
                    ? LEAWIND_3_PERSPECTIVE_API_SUPPORTED
                    : LEAWIND_3_MISSING_PERSPECTIVE_API;
            case ABSENT, UNKNOWN -> UNKNOWN;
        };
    }

    public boolean backendSupported() {
        return this == LEAWIND_3_PERSPECTIVE_API_SUPPORTED;
    }

    public boolean leawindInstalled() {
        return this != ABSENT;
    }

    public boolean coexistOnly() {
        return this == LEAWIND_2_UNSUPPORTED;
    }

    private static boolean matchesVerifiedGeneration(
            ApiShapeProbe.ClassBytes source
    ) {
        return ApiShapeProbe.satisfies(source, API + "PerspectiveAPI", API_MEMBERS)
                && ApiShapeProbe.satisfies(
                source,
                API + "Perspective",
                PERSPECTIVE_MEMBERS
        )
                && ApiShapeProbe.satisfies(
                source,
                API + "PerspectiveInfo",
                INFO_MEMBERS
        )
                && ApiShapeProbe.satisfies(
                source,
                API + "PerspectiveOverrideChain",
                CHAIN_MEMBERS
        )
                && ApiShapeProbe.satisfies(
                source,
                API + "PerspectiveOverrideRegistration",
                REGISTRATION_MEMBERS
        );
    }
}
