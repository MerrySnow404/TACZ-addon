package com.mafuyu404.taczaddon.compat.tacz;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Enumeration;

final class TaczVersionReader {
    private static final String TACZ_CLASS_RESOURCE =
            "com/tacz/guns/api/item/IGun.class";
    private static final String EXPECTED_VERSION =
            "1.1.8-hotfix";
    private static final String HOTFIX2_VERSION = "1.1.8-hotfix2";

    private TaczVersionReader() {
    }

    static TaczCompatibilityProfile profileForVersion(String version) {
        if (version != null && "1.2.0".equals(version.trim())) {
            return TaczCompatibilityProfile.TACZ_1_2_0;
        }
        if (version != null
                && (EXPECTED_VERSION.equalsIgnoreCase(version.trim())
                || HOTFIX2_VERSION.equalsIgnoreCase(version.trim()))) {
            // Both published jars expose the same version-bound Mixin contracts.
            return TaczCompatibilityProfile.TACZ_1_1_8_HOTFIX;
        }
        return TaczCompatibilityProfile.UNKNOWN;
    }

    static VersionResolution resolve() {
        ClassLoader loader = TaczVersionReader.class.getClassLoader();
        URL classResource = loader.getResource(TACZ_CLASS_RESOURCE);
        if (classResource == null) {
            return new VersionResolution(
                    false,
                    "unknown",
                    TaczCompatibilityProfile.UNKNOWN
            );
        }

        String version = readVersionFromTaczSource(loader);
        return new VersionResolution(
                true,
                version,
                profileForVersion(version)
        );
    }

    private static String readVersionFromTaczSource(
            ClassLoader loader
    ) {
        try {
            Enumeration<URL> manifests =
                    loader.getResources("META-INF/MANIFEST.MF");
            String fallbackVersion = null;
            while (manifests.hasMoreElements()) {
                URL manifest = manifests.nextElement();
                String version = readManifestVersion(manifest);
                if (version != null) {
                    if (belongsToTaczSource(manifest)) {
                        return version.trim();
                    }
                    if (fallbackVersion == null) {
                        fallbackVersion = version;
                    }
                }
            }
            if (fallbackVersion != null) {
                return fallbackVersion.trim();
            }
        } catch (IOException ignored) {
            return "unknown";
        }
        return "unknown";
    }

    private static boolean belongsToTaczSource(URL manifest)
            throws IOException {
        String external = manifest.toExternalForm();
        int marker = external.lastIndexOf(
                "!/META-INF/MANIFEST.MF"
        );
        if (marker < 0) {
            return false;
        }

        URL sourceRoot = new URL(external.substring(0, marker + 2));
        try (InputStream stream = new URL(
                sourceRoot,
                TACZ_CLASS_RESOURCE
        ).openStream()) {
            return stream != null;
        }
    }

    private static String readManifestVersion(URL manifest)
            throws IOException {
        try (InputStream stream = manifest.openStream();
             BufferedReader reader = new BufferedReader(
                     new InputStreamReader(
                             stream,
                             StandardCharsets.ISO_8859_1
                     )
             )) {
            boolean tacz = false;
            String version = null;
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("Implementation-Title:")) {
                    tacz = "tacz".equalsIgnoreCase(
                            line.substring(
                                    "Implementation-Title:".length()
                            ).trim()
                    );
                } else if (line.startsWith("Implementation-Version:")) {
                    version = line.substring(
                            "Implementation-Version:".length()
                    ).trim();
                }
            }
            return tacz ? version : null;
        }
    }

    record VersionResolution(
            boolean present,
            String implementationVersion,
            TaczCompatibilityProfile profile
    ) {
    }
}
