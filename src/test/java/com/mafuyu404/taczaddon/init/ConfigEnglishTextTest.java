package com.mafuyu404.taczaddon.init;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ConfigEnglishTextTest {
    @Test
    void forgeConfigSourceTextContainsNoCjkCharacters()
            throws IOException {
        assertNoCjk(
                "src/main/java/com/mafuyu404/taczaddon/config/Config.java"
        );
        assertNoCjk(
                "src/main/java/com/mafuyu404/taczaddon/config/CommonConfig.java"
        );
    }

    private static void assertNoCjk(String relativePath)
            throws IOException {
        String source = Files.readString(
                Path.of(relativePath),
                StandardCharsets.UTF_8
        );

        for (int offset = 0;
             offset < source.length();) {
            int codePoint = source.codePointAt(offset);
            if (isCjk(codePoint)) {
                int failureOffset = offset;
                assertEquals(
                        -1,
                        failureOffset,
                        () -> relativePath
                                + " contains CJK config text at offset "
                                + failureOffset
                                + ": U+"
                                + Integer.toHexString(codePoint)
                );
            }
            offset += Character.charCount(codePoint);
        }
    }

    private static boolean isCjk(int codePoint) {
        if (Character.UnicodeScript.of(codePoint)
                == Character.UnicodeScript.HAN) {
            return true;
        }

        return codePoint >= 0x3000 && codePoint <= 0x303F
                || codePoint >= 0xFF00 && codePoint <= 0xFFEF;
    }
}
