package com.yn.sodiumeditor.test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;

/**
 * Static guard that Arabic-script text does not use typed character animation.
 */
public class CharAnimationArabicGuardTest {

    @Test
    public void arabicScriptInput_shouldNotStartCharAnimation() throws Exception {
        String src = readSource("sodium-editor/src/main/java/com/yn/sodiumeditor/renderer/animation/CharAnimation.java");

        assertTrue(
                "BUG: Arabic/Persian input should not start typed char animation.",
                src.contains("TextArabicUtils.containsArabicScript(committedText, 0, committedText.length())")
                        && src.contains("skip start: arabic-script"));
        assertTrue(
                "BUG: Arabic/Persian deletion should not start delete animation.",
                src.contains("TextArabicUtils.containsArabicScript(removedText, 0, removedText.length())")
                        && src.contains("skip delete: arabic-script"));
    }

    @Test
    public void arabicScriptLine_shouldUseArabicFadeRenderPath() throws Exception {
        String src = readSource("sodium-editor/src/main/java/com/yn/sodiumeditor/renderer/draw/TextLineDraw.java");

        assertTrue(
                "BUG: Arabic-script lines should route through the Arabic-aware fade renderer.",
                src.contains("TextArabicUtils.containsArabicScript(line, start, end)")
                        && src.contains("return drawArabicTextSegmentWithFade("));
        assertTrue(
                "BUG: Arabic-aware fade renderer must preserve glyph context.",
                src.contains("drawTextRunWithContext("));
        assertFalse(
                "BUG: Arabic typed char animation must be fade-only; do not move the glyph vertically.",
                src.contains("paint.getTextSize() * 0.35f"));
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
