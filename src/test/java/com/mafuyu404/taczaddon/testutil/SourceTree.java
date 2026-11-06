package com.mafuyu404.taczaddon.testutil;

import java.nio.file.*;

public final class SourceTree {
    private SourceTree() {}

    public static Path path(String relative) {
        for (Path root = Path.of("").toAbsolutePath(); root != null; root = root.getParent()) {
            Path candidate = root.resolve(relative);
            if (Files.isRegularFile(candidate)) return candidate;
        }
        throw new IllegalStateException("Source file unavailable: " + relative);
    }
}
