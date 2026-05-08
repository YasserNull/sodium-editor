package com.yn.sodiumeditor.test;

import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;

/**
 * Regression guard for selection handle lag while dragging.
 */
public class SelectionHandleDragAnimationGuardTest {

    @Test
    public void draggedSelectionHandle_shouldBypassMoveAnimation() throws Exception {
        String src = readSource("sodium-editor/src/main/java/com/yn/sodiumeditor/core/selection/SelectionHandles.java");
        int at = src.indexOf("float leftTargetY = startY + editor.textRender.lineHeight;");
        assertTrue("Expected handle position update target block.", at >= 0);
        String around = src.substring(at, Math.min(src.length(), at + 2200));

        assertTrue(
                "BUG: left handle should snap to target while dragging instead of easing from stale animation position.",
                around.contains("boolean bypassLeftAnimation = draggingHandle == 1 || scrollChanged;")
                        && around.contains("if (bypassLeftAnimation)")
                        && around.contains("animation.snapHandlePosition(true, startX, leftTargetY);")
                        && around.contains("bypassLeftAnimation")
                        && around.contains("new float[] {startX, leftTargetY}")
                        && around.contains("animation.getAnimatedHandlePosition(true, startX, leftTargetY);"));

        assertTrue(
                "BUG: right handle should snap to target while dragging instead of easing from stale animation position.",
                around.contains("boolean bypassRightAnimation = draggingHandle == 2 || scrollChanged;")
                        && around.contains("if (bypassRightAnimation)")
                        && around.contains("animation.snapHandlePosition(false, endX, rightTargetY);")
                        && around.contains("bypassRightAnimation")
                        && around.contains("new float[] {endX, rightTargetY}")
                        && around.contains("animation.getAnimatedHandlePosition(false, endX, rightTargetY);"));
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
