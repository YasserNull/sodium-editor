package com.yn.sodiumeditor.test;

import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;

/**
 * Regression guard for avoiding post-scroll stretch motion while selection is active.
 */
public class SelectionStretchReleaseGuardTest {

    @Test
    public void stretchOverscroll_shouldResetInsteadOfAnimatingWhileSelectionIsActive() throws Exception {
        String src = readSource("sodium-editor/src/main/java/com/yn/sodiumeditor/core/scroll/Stretch.java");

        assertTrue(
                "BUG: stretch pull should be ignored while selection is active.",
                src.contains("public void pullStretchX")
                        && src.contains("public void pullStretchY")
                        && src.contains("if (editor.selection.hasSelection) {\n      reset();\n      return;\n    }"));

        int releaseAt = src.indexOf("public void releaseStretch()");
        assertTrue("Expected releaseStretch in Stretch.", releaseAt >= 0);
        String release = src.substring(releaseAt, Math.min(src.length(), releaseAt + 1200));

        assertTrue(
                "BUG: releasing stretch during active selection should clear stretch values without running ValueAnimator.",
                release.contains("if (editor.selection.hasSelection)")
                        && release.contains("stretchX = 0f;")
                        && release.contains("stretchY = 0f;")
                        && release.contains("return;"));
    }

    @Test
    public void popupFade_shouldSkipNoopAnimationWhenAlphaAlreadyMatchesTarget() throws Exception {
        String src = readSource("sodium-editor/src/main/java/com/yn/sodiumeditor/renderer/animation/PopupAnimation.java");
        int at = src.indexOf("public void startFade(float targetAlpha)");
        assertTrue("Expected startFade in PopupAnimation.", at >= 0);
        String around = src.substring(at, Math.min(src.length(), at + 1800));

        assertTrue(
                "BUG: popup fade should not start a ValueAnimator when startAlpha already equals targetAlpha.",
                around.contains("if (Math.abs(startAlpha - targetAlpha) < 0.001f)")
                        && around.contains("popup.popupAlpha = targetAlpha;")
                        && around.contains("return;"));
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

        Path fallback =
                new File(".")
                        .toPath()
                        .toAbsolutePath()
                        .normalize()
                        .resolve(relativePath.replace("sodium-editor/", ""));
        if (Files.exists(fallback)) {
            return new String(Files.readAllBytes(fallback), StandardCharsets.UTF_8);
        }
        throw new IllegalStateException("Could not locate source file: " + relativePath);
    }
}
