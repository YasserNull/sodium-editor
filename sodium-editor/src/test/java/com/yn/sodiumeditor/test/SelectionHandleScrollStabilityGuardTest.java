package com.yn.sodiumeditor.test;

import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;

/**
 * Regression guard for selection handles staying visually stable during scroll.
 */
public class SelectionHandleScrollStabilityGuardTest {

    @Test
    public void selectionHandles_shouldBypassMoveAnimationWhenOnlyScrollChanges() throws Exception {
        String src = readSource("sodium-editor/src/main/java/com/yn/sodiumeditor/core/selection/SelectionHandles.java");
        int at = src.indexOf("public void updateHandlesPosition()");
        assertTrue("Expected updateHandlesPosition in SelectionHandles.", at >= 0);
        String around = src.substring(at, Math.min(src.length(), at + 4200));

        assertTrue(
                "BUG: selection handles should track previous scroll position to distinguish scroll from selection movement.",
                around.contains("lastHandleScrollX")
                        && around.contains("lastHandleScrollY"));

        assertTrue(
                "BUG: selection handles should bypass move animation while scroll changes, otherwise handles lag and drift.",
                around.contains("boolean scrollChanged")
                        && around.contains("|| scrollChanged")
                        && around.contains("? new float[] {startX, leftTargetY}")
                        && around.contains("? new float[] {endX, rightTargetY}"));
    }

    @Test
    public void cursorHandle_shouldRemainDocumentAnchoredAndApplyScrollAtDrawTime() throws Exception {
        String src = readSource("sodium-editor/src/main/java/com/yn/sodiumeditor/core/cursor/CursorHandle.java");
        int at = src.indexOf("public void updateCursorHandlePosition()");
        assertTrue("Expected updateCursorHandlePosition in CursorHandle.", at >= 0);
        String around = src.substring(at, Math.min(src.length(), at + 2200));

        assertTrue(
                "BUG: cursor handle should keep document coordinates separate from view scroll.",
                around.contains("docX = caret.getCaretDocumentX()")
                        || around.contains("docX = editor.cursorAnimation.cursorDrawX"));

        assertTrue(
                "BUG: cursor handle should apply current scroll when converting document coordinates to screen coordinates.",
                around.contains("float x = editor.layout.getTextStartX() + docX - editor.scroll.scrollX;")
                        && around.contains("float y = docY - editor.scroll.scrollY;"));
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
