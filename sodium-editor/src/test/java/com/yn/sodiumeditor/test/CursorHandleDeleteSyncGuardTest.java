package com.yn.sodiumeditor.test;

import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;

/**
 * Regression guard for cursor handle lag during repeated backspace.
 */
public class CursorHandleDeleteSyncGuardTest {

    @Test
    public void deleteCharAtCursor_shouldInvalidateCursorAreaForBackspaceAndMerge() throws Exception {
        String src = readSource("sodium-editor/src/main/java/com/yn/sodiumeditor/io/EditorActions.java");
        int at = src.indexOf("public void deleteCharAtCursor()");
        assertTrue("Expected deleteCharAtCursor in EditorActions.", at >= 0);
        String around = src.substring(at, Math.min(src.length(), at + 9000));

        assertTrue(
                "BUG: character backspace branch should invalidate cursor area after cursorChar changes.",
                around.contains("editor.cursor.cursorChar = safeStart;")
                        && around.contains("editor.view.invalidateLineGlobal(editor.cursor.cursorLine);")
                        && around.contains("editor.cursor.invalidateCursorArea();"));
        assertTrue(
                "BUG: merge-lines backspace branch should invalidate cursor area after cursor line/char changes.",
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
