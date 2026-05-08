package com.yn.sodiumeditor.test;

import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;

/**
 * Regression guard for preventing selection handles from crossing each other.
 */
public class SelectionHandleNoCrossGuardTest {

    @Test
    public void draggingSelectionHandles_shouldClampBeforeCrossingOppositeHandle() throws Exception {
        String src = readSource("sodium-editor/src/main/java/com/yn/sodiumeditor/input/events/DragSelectionHandler.java");
        int at = src.indexOf("public void updateHandlePosition(float touchX, float touchY)");
        assertTrue("Expected updateHandlePosition in DragSelectionHandler.", at >= 0);
        String around = src.substring(at, Math.min(src.length(), at + 5200));

        assertTrue(
                "BUG: left/start handle must compare proposed position against current end handle before applying.",
                around.contains("draggingHandle == 1")
                        && around.contains("comparePos(line, clamped, editor.selection.selEndLine, editor.selection.selEndChar) >= 0"));

        assertTrue(
                "BUG: right/end handle must compare proposed position against current start handle before applying.",
                around.contains("draggingHandle == 2")
                        && around.contains("comparePos(line, clamped, editor.selection.selStartLine, editor.selection.selStartChar) <= 0"));

        assertTrue(
                "BUG: crossing should leave a one-character gap instead of clamping both handles to the same position.",
                around.contains("getPreviousSelectionPosition(editor.selection.selEndLine, editor.selection.selEndChar)")
                        && around.contains("getNextSelectionPosition(editor.selection.selStartLine, editor.selection.selStartChar)")
                        && around.contains("private int[] getPreviousSelectionPosition")
                        && around.contains("private int[] getNextSelectionPosition"));
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
