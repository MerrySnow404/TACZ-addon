package com.mafuyu404.taczaddon.testutil;

import org.junit.jupiter.api.Assumptions;

import static org.junit.jupiter.api.Assertions.fail;

/**
 * Gate for the real-upstream ABI fixtures.
 *
 * <p>By default a missing fixture is a hard failure: the Gradle verification
 * pipeline must never report ABI compatibility as verified when the upstream
 * jars were not actually inspected. The only escape hatch is the explicit
 * development property
 * {@code -Dtaczaddon.compat.allowMissingFixtures=true}, which is forwarded to
 * the test JVM by {@code build.gradle} and is never enabled by default.
 */
public final class CompatibilityFixtureGate {
    public static final String ALLOW_MISSING_PROPERTY =
            "taczaddon.compat.allowMissingFixtures";

    private CompatibilityFixtureGate() {
    }

    public static boolean allowMissing() {
        return Boolean.parseBoolean(
                System.getProperty(ALLOW_MISSING_PROPERTY, "false")
        );
    }

    /** Fails (or, in explicit development mode, skips) when absent. */
    public static void require(boolean present, String message) {
        if (present) {
            return;
        }
        if (allowMissing()) {
            Assumptions.abort(
                    message + " [skipped: " + ALLOW_MISSING_PROPERTY
                            + "=true]"
            );
        }
        fail(message
                + "\nReal upstream ABI verification is mandatory.\nResolve the fixtures with: gradlew resolveCompatibilityFixtures\n(Use -D" + ALLOW_MISSING_PROPERTY
                + "=true only for local IDE runs.)");
    }
}
