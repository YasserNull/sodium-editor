package com.yn.sodiumeditor.test;

import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;

/**
 * Regression guard for stale cursor handle position when cursor animation is disabled.
 */
public class CursorHandleAnimationGateGuardTest {

    @Test
    public void updateCursorHandlePosition_shouldUseAnimatedCoordsOnlyWhenAnimationEnabled() throws Exception {
        String src = readSource("sodium-editor/src/main/java/com/yn/sodiumeditor/core/cursor/CursorHandle.java");
        int at = src.indexOf("public void updateCursorHandlePosition()");
        assertTrue("Expected updateCursorHandlePosition in CursorHandle.", at >= 0);
        String around = src.substring(at, Math.min(src.length(), at + 1600));

        assertTrue(
                "BUG: cursor handle should gate cursorAnimation coordinates behind isCursorAnimationEnabled.",
                around.contains("boolean zoomOrScaleTransition")
                        && around.contains("!zoomOrScaleTransition")
                        && around.contains("editor.cursorAnimation.isCursorAnimationEnabled")
                        && around.contains("editor.cursorAnimation.cursorAnimValid")
                        && around.contains("docX = caret.getCaretDocumentX();")
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
