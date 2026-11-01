package com.mafuyu404.taczaddon.compat;

import com.mafuyu404.taczaddon.testutil.CompatibilityFixtures;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Generation detection verified against real upstream release jars.
 *
 * <p>Shoulder Surfing Reloaded keeps
 * {@code api/model/Perspective} and {@code client/InputHandler} alive in 5.x for
 * source compatibility, so class presence alone cannot tell the generations
 * apart. These fixtures prove the anchor the addon actually keys on:
 * {@code api/client/Perspective} plus the
 * {@code IShoulderSurfing#changePerspective(api.client.Perspective)} shape.
 *
 * <p>The Leawind fixtures prove the upstream dependency difference the
 * {@link PerspectiveIntegrationProfile} models: 2.2.0 has no
 * {@code perspective_api} dependency, 3.0.3-beta+forge-1.20.1 has one.
 */
class UpstreamAbiFixtureTest {
    private static final String SSR_PACKAGE =
            "com.github.exopandora.shouldersurfing.";
    private static final String SSR_INPUT_HANDLER =
            SSR_PACKAGE + "client.InputHandler";
    /** Every fixture used below must be the declared pinned artifact. */
    @Test
    void fixturesArePinnedAndVersionChecked() {
        for (String[] fixture : List.of(
                new String[] {"shouldersurfing", "legacy", "shouldersurfing"},
                new String[] {"shouldersurfing", "v5", "shouldersurfing"},
                new String[] {"shouldersurfing", "v5.current", "shouldersurfing"},
                new String[] {"shouldersurfing", "v5.previous", "shouldersurfing"},
                new String[] {"leawind", "2", "leawind"},
                new String[] {"leawind", "3", "leawind"},
                new String[] {"perspectiveapi", "1.5.0", "perspectiveapi"}
        )) {
            Path jar = CompatibilityFixtures.jar(
                    fixture[0],
                    fixture[1],
                    fixture[2]
            );
            CompatibilityFixtures.requireVersion(
                    jar,
                    CompatibilityFixtures.version(
                            fixture[0],
                            fixture[1],
                            fixture[2]
                    ),
                    CompatibilityFixtures.describe(
                            fixture[0],
                            fixture[1],
                            fixture[2]
                    )
            );
        }
    }

    @Test
    void shoulderSurfingLegacyGenerationIsDetectedAsLegacy() {
        Path jar = shouldersurfingLegacy();
        ApiShapeProbe.ClassBytes source = jarSource(jar);

        assertEquals(
                ShoulderSurfingGeneration.LEGACY,
                ShoulderSurfingGeneration.detect(true, source),
                "4.1.5 must be the legacy generation, never the addon backend"
        );
        assertFalse(
                ShoulderSurfingDispatch.resolve(
                        ShoulderSurfingGeneration.detect(true, source),
                        false
                ) == ShoulderSurfingDispatch.USE_ADDON_V5,
                "legacy generation must keep TaCZ's own compatibility path"
        );
        /*
         * TaCZ 1.1.8-hotfix links exactly this legacy contract, which must
         * therefore be complete for the native path to work.
         */
        assertTrue(ApiShapeProbe.hasMethod(
                source,
                "com.github.exopandora.shouldersurfing.api.model.Perspective",
                "current",
                "()Lcom/github/exopandora/shouldersurfing/api/model/"
                        + "Perspective;"
        ));
        assertTrue(ApiShapeProbe.hasField(
                source,
                SSR_INPUT_HANDLER,
                "FREE_LOOK",
                "Lnet/minecraft/client/KeyMapping;"
        ));
    }

    @Test
    void shoulderSurfingNewGenerationIsDetectedAsApiV5() {
        Path jar = shouldersurfingV5();
        ApiShapeProbe.ClassBytes source = jarSource(jar);

        /*
         * Both generations ship api/model/Perspective, so the old anchor must
         * not be what selects the addon backend.
         */
        assertTrue(ApiShapeProbe.hasClass(
                source,
                SSR_PACKAGE + "api.model.Perspective"
        ));
        assertEquals(
                ShoulderSurfingGeneration.API_V5,
                ShoulderSurfingGeneration.detect(true, source)
        );
        assertTrue(
                ShoulderSurfingDispatch.resolve(
                        ShoulderSurfingGeneration.detect(true, source),
                        false
                ) == ShoulderSurfingDispatch.USE_ADDON_V5
        );
    }

    @Test
    void shoulderSurfingCurrentV5GenerationIsDetectedAsApiV5() {
        Path jar = shouldersurfingV5Current();
        ApiShapeProbe.ClassBytes source = jarSource(jar);

        assertEquals(
                ShoulderSurfingGeneration.API_V5,
                ShoulderSurfingGeneration.detect(true, source),
                "5.3.0 must remain in the verified v5 API generation"
        );

        assertEquals(
                ShoulderSurfingDispatch.USE_ADDON_V5,
                ShoulderSurfingDispatch.resolve(
                        ShoulderSurfingGeneration.detect(true, source),
                        false
                ),
                "5.3.0 must use the addon v5 backend"
        );
    }

    @Test
    void shoulderSurfingPreviousBaselineStillUsesV5Backend() {
        var source = jarSource(CompatibilityFixtures.jar(
                "shouldersurfing", "v5.previous", "shouldersurfing"));
        assertEquals(ShoulderSurfingGeneration.API_V5,
                ShoulderSurfingGeneration.detect(true, source));
        assertEquals(ShoulderSurfingDispatch.USE_ADDON_V5,
                ShoulderSurfingDispatch.resolve(ShoulderSurfingGeneration.detect(true, source), false));
    }

    private static Path shouldersurfingLegacy() {
        return CompatibilityFixtures.jar(
                "shouldersurfing",
                "legacy",
                "shouldersurfing"
        );
    }

    private static Path shouldersurfingV5() {
        return CompatibilityFixtures.jar(
                "shouldersurfing",
                "v5",
                "shouldersurfing"
        );
    }

    private static Path shouldersurfingV5Current() {
        return CompatibilityFixtures.jar(
                "shouldersurfing",
                "v5.current",
                "shouldersurfing"
        );
    }

    @Test
    void leawindTwoHasNoPerspectiveApiDependency() throws IOException {
        Path jar = leawindTwo();
        assertNotNull(readEntry(jar, "META-INF/mods.toml"));
        assertFalse(
                CompatibilityFixtures.declaresDependency(
                        jar,
                        "perspective_api"
                ),
                "Leawind 2.x must not declare a Perspective API dependency"
        );
        assertFalse(
                hasEntry(
                        jar,
                        "io/github/leawind/perspectiveapi/api/"
                                + "PerspectiveAPI.class"
                ),
                "Leawind 2.x must not ship the Perspective API"
        );
        assertEquals(
                PerspectiveIntegrationProfile.LEAWIND_2_UNSUPPORTED,
                PerspectiveIntegrationProfile.detect(
                        true,
                        false,
                        jarSource(jar)
                ),
                "Leawind 2.x must be coexist-only, never Perspective API "
                        + "compatible"
        );
        /*
         * Leawind 2.x plus an independently installed Perspective API must not
         * be mistaken for the 3.x generation.
         */
        assertEquals(
                PerspectiveIntegrationProfile.LEAWIND_2_UNSUPPORTED,
                PerspectiveIntegrationProfile.detect(
                        true,
                        true,
                        jarSource(jar)
                ),
                "Leawind 2.x + Perspective API must stay unsupported"
        );
        assertEquals(
                LeawindGeneration.LEAWIND_2,
                LeawindGeneration.detect(true, jarSource(jar))
        );
    }

    @Test
    void leawindThreeDeclaresThePerspectiveApiDependency()
            throws IOException {
        Path jar = leawindThree();
        assertNotNull(readEntry(jar, "META-INF/mods.toml"));
        assertTrue(
                CompatibilityFixtures.declaresDependency(
                        jar,
                        "perspective_api"
                ),
                "Leawind 3.x is the Perspective API generation"
        );
        ApiShapeProbe.ClassBytes source = jarSource(jar);
        assertEquals(
                LeawindGeneration.LEAWIND_3,
                LeawindGeneration.detect(true, source)
        );
        assertEquals(
                PerspectiveIntegrationProfile
                        .LEAWIND_3_MISSING_PERSPECTIVE_API,
                PerspectiveIntegrationProfile.detect(
                        true,
                        false,
                        source
                ),
                "Leawind 3.x without Perspective API is unavailable but safe"
        );
        assertEquals(
                PerspectiveIntegrationProfile
                        .LEAWIND_3_PERSPECTIVE_API_SUPPORTED,
                PerspectiveIntegrationProfile.detect(
                        true,
                        true,
                        combined(jar, perspectiveApi())
                ),
                "Leawind 3.x + verified Perspective API is the supported "
                        + "generation"
        );
    }

    /**
     * Combines the Leawind 3.x fixture with the real development-baseline
     * Perspective API classes so the full supported profile can be verified
     * without installing both mods.
     */
    /**
     * Combines two fixture jars into one byte source, so Leawind 3.x can be
     * verified together with the real Perspective API release.
     */
    private static ApiShapeProbe.ClassBytes combined(
            Path first,
            Path second
    ) {
        ApiShapeProbe.ClassBytes left = jarSource(first);
        ApiShapeProbe.ClassBytes right = jarSource(second);
        return binaryName -> {
            byte[] bytes = left.read(binaryName);
            return bytes != null ? bytes : right.read(binaryName);
        };
    }

    private static Path leawindTwo() {
        return CompatibilityFixtures.jar("leawind", "2", "leawind");
    }

    private static Path leawindThree() {
        return CompatibilityFixtures.jar("leawind", "3", "leawind");
    }

    private static Path perspectiveApi() {
        return CompatibilityFixtures.jar(
                "perspectiveapi",
                "1.5.0",
                "perspectiveapi"
        );
    }

    private static boolean hasEntry(Path jar, String entry)
            throws IOException {
        try (JarFile file = new JarFile(jar.toFile())) {
            return file.getJarEntry(entry) != null;
        }
    }

    private static String readEntry(Path jar, String entry)
            throws IOException {
        try (JarFile file = new JarFile(jar.toFile())) {
            JarEntry jarEntry = file.getJarEntry(entry);
            if (jarEntry == null) {
                return null;
            }
            try (InputStream input = file.getInputStream(jarEntry)) {
                return new String(
                        input.readAllBytes(),
                        StandardCharsets.UTF_8
                );
            }
        }
    }

    private static ApiShapeProbe.ClassBytes jarSource(Path jar) {
        return binaryName -> {
            String entryName = binaryName.replace('.', '/')
                    + ".class";
            try (JarFile file = new JarFile(jar.toFile())) {
                JarEntry entry = file.getJarEntry(entryName);
                if (entry == null) {
                    return null;
                }
                try (InputStream input = file.getInputStream(entry)) {
                    return input.readAllBytes();
                }
            } catch (IOException unreadable) {
                return null;
            }
        };
    }
}
