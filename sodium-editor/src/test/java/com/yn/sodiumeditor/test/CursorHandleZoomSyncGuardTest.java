package com.yn.sodiumeditor.test;

import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;

/**
 * Regression guard for cursor-handle stability during and right after zoom/scale changes.
 */
public class CursorHandleZoomSyncGuardTest {

    @Test
    public void updateCursorHandlePosition_shouldBypassCursorAnimationDuringZoomAndScaleTransitions()
            throws Exception {
        String src =
                readSource(
                        "sodium-editor/src/main/java/com/yn/sodiumeditor/core/cursor/CursorHandle.java");
        int at = src.indexOf("public void updateCursorHandlePosition()");
        assertTrue("Expected updateCursorHandlePosition in CursorHandle.", at >= 0);
        String around = src.substring(at, Math.min(src.length(), at + 1800));

        assertTrue(
                "BUG: CursorHandle should detect active zoom / scale transitions before reusing animated cursor coordinates.",
                around.contains("editor.zoom.isScaling")
                        || around.contains("editor.scaleGestureDetector.isInProgress()")
                        || around.contains("editor.onTouch.multiTouchActive")
                        || around.contains("editor.zoom.mJustFinishedScale"));

        assertTrue(
                "BUG: CursorHandle must fall back to caret document coordinates during zoom transitions instead of stale cursorAnimation coordinates.",
                around.contains("docX = caret.getCaretDocumentX();")
                        && around.contains("docY = caret.getCaretDocumentY();"));
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
