package com.yn.sodiumeditor.test;

import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;

/**
 * Regression guards for cursor-handle lag behind caret.
 */
public class CursorHandleRegressionGuardTest {

    @Test
    public void cursorHandle_shouldNotLagWhenCursorAnimationIsDisabled() throws Exception {
        String src = readSource("sodium-editor/src/main/java/com/yn/sodiumeditor/core/cursor/CursorHandle.java");
        int at = src.indexOf("public void updateCursorHandlePosition()");
        assertTrue("Expected updateCursorHandlePosition in CursorHandle.", at >= 0);
        String around = src.substring(at, Math.min(src.length(), at + 1600));

        assertTrue(
                "BUG: CursorHandle must ignore stale cursorAnimation coordinates when animation is disabled.",
                around.contains("boolean zoomOrScaleTransition")
                        && around.contains("editor.cursorAnimation.isCursorAnimationEnabled")
                        && around.contains("docX = caret.getCaretDocumentX();")
                        && around.contains("docY = caret.getCaretDocumentY();"));
    }

    @Test
    public void deleteCharAtCursor_shouldRefreshHandleDuringRepeatedBackspace() throws Exception {
        String src = readSource("sodium-editor/src/main/java/com/yn/sodiumeditor/io/EditorActions.java");
        int at = src.indexOf("public void deleteCharAtCursor()");
        assertTrue("Expected deleteCharAtCursor in EditorActions.", at >= 0);
        String around = src.substring(at, Math.min(src.length(), at + 9000));

        assertTrue(
                "BUG: Backspace branch should invalidate cursor area right after cursorChar changes.",
                around.contains("editor.cursor.cursorChar = safeStart;")
                        && around.contains("editor.view.invalidateLineGlobal(editor.cursor.cursorLine);")
                        && around.contains("editor.cursor.invalidateCursorArea();"));
        assertTrue(
                "BUG: Merge-lines branch should invalidate cursor area after moving cursor to previous line.",
                around.contains("editor.cursor.cursorLine = prevGlobal;")
                        && around.contains("editor.cursor.cursorChar = prev.length();")
                        && around.contains("editor.lineNumber.invalidateLineNumberCache();")
                        && around.contains("editor.cursor.invalidateCursorArea();"));
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
