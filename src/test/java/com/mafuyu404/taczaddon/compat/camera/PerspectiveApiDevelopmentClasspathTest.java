package com.mafuyu404.taczaddon.compat.camera;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class PerspectiveApiDevelopmentClasspathTest {
    @Test
    void perspectiveApiClassIsLoadableFromDevelopmentRuntime()
            throws Exception {
        assertNotNull(
                Class.forName(
                        "io.github.leawind.perspectiveapi.api.PerspectiveAPI"
                )
        );
    }
}
