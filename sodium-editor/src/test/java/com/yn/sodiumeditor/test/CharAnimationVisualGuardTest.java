package com.yn.sodiumeditor.test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;

/** Static guard that typed character animation is fade-only. */
public class CharAnimationVisualGuardTest {

    @Test
    public void drawTextSegmentWithFade_shouldFadeWithoutVerticalOffset() throws Exception {
        String src = readSource("sodium-editor/src/main/java/com/yn/sodiumeditor/renderer/draw/TextLineDraw.java");

        assertTrue(
                "BUG: typed char animation should still isolate the animated glyph and apply alpha.",
                src.contains("getCharAnimOffsetY(fadeAlpha, segmentPaint)")
                        && src.contains("getCharAnimOffsetY(alphaMultiplier, segmentPaint)")
                        && src.contains("charAnimTmpPaint.setAlpha((int) (baseAlpha * Math.max(0f, Math.min(1f, fadeAlpha))))"));
        assertFalse(
                "BUG: typed char animation must be fade-only; do not move the glyph vertically.",
                src.contains("paint.getTextSize() * 0.35f"));
        assertTrue(
                "BUG: fade-only animation should return zero vertical offset.",
                src.contains("private float getCharAnimOffsetY(float alpha, Paint paint) {\n        return 0f;\n    }"));
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
