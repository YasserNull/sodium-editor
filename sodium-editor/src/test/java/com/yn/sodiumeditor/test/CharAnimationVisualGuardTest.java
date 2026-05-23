package com.yn.sodiumeditor.test;

import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;

/**
 * Static guard that typed character animation has a visible positional component.
 */
public class CharAnimationVisualGuardTest {

    @Test
    public void drawTextSegmentWithFade_shouldOffsetAnimatingGlyph() throws Exception {
        String src = readSource("sodium-editor/src/main/java/com/yn/sodiumeditor/renderer/draw/TextLineDraw.java");

        assertTrue(
                "BUG: char animation only changes alpha; it should offset the glyph so the animation is visible.",
                src.contains("getCharAnimOffsetY(fadeAlpha, segmentPaint)")
                        && src.contains("getCharAnimOffsetY(alphaMultiplier, segmentPaint)")
                        && src.contains("paint.getTextSize() * 0.35f"));
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
