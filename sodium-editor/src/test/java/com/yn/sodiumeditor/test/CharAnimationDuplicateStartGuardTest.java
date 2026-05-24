package com.yn.sodiumeditor.test;

import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;

/**
 * Static guard that duplicate start calls do not reset the same typed-char animation.
 */
public class CharAnimationDuplicateStartGuardTest {

    @Test
    public void startCharAnimation_shouldIgnoreDuplicateActiveRange() throws Exception {
        String src = readSource("sodium-editor/src/main/java/com/yn/sodiumeditor/renderer/animation/CharAnimation.java");

        assertTrue(
                "BUG: duplicate start calls for the same inserted range should not reset/shorten the animation.",
                src.contains("targetStartChar")
                        && src.contains("skip start: duplicate active range")
                        && src.contains("charAnimStartChar == targetStartChar")
                        && src.contains("charAnimAlpha < 1f"));
    }

    private static String readSource(String relativePath) throws Exception {
        Path cwd = new File(System.getProperty("user.dir", ".")).toPath().toAbsolutePath().normalize();
        for (int i = 0; i < 8; i++) {
            Path candidate = cwd.resolve(relativePath);
            if (Files.exists(candidate)) {
                return new String(Files.readAllBytes(candidate), StandardCharsets.UTF_8);
            }
            Path parent = cwd.getParent();
            if (parent == null) break;
            cwd = parent;
        }

        throw new IllegalStateException("Could not locate source file: " + relativePath);
    }
}
