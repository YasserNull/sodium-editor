package com.yn.sodiumeditor.test;

import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;

/** Static guard that normal character deletion starts delete animation. */
public class CharAnimationDeleteGuardTest {

    @Test
    public void deleteCharAtCursor_shouldStartDeleteAnimationForRemovedText() throws Exception {
        String src = readSource("sodium-editor/src/main/java/com/yn/sodiumeditor/io/EditorActions.java");
        String body = methodBody(src, "deleteCharAtCursor()");

        assertTrue(
                "BUG: backspace should animate the removed character, not only typed characters.",
                body.contains("editor.charAnimation.startDeleteAnimation(")
                        && body.contains("safeStart")
                        && body.contains("removed"));
    }

    @Test
    public void deleteForwardAtCursor_shouldStartDeleteAnimationForRemovedText() throws Exception {
        String src = readSource("sodium-editor/src/main/java/com/yn/sodiumeditor/io/EditorActions.java");
        String body = methodBody(src, "deleteForwardAtCursor()");

        assertTrue(
                "BUG: forward delete should animate the removed character, not only typed characters.",
                body.contains("editor.charAnimation.startDeleteAnimation(")
                        && body.contains("safeCursorChar")
                        && body.contains("removed"));
    }

    @Test
    public void highliteRender_shouldDrawDeleteAnimationForEmptyAndBinaryLines() throws Exception {
        String src = readSource("sodium-editor/src/main/java/com/yn/sodiumeditor/renderer/HighliteRender.java");
        String body = methodBody(src, "drawHighlightedLine(Canvas canvas, String line, int globalLine, float y)");

        assertTrue(
                "BUG: deleting the last character leaves an empty line, which must still draw the delete ghost.",
                body.contains("if (line.isEmpty())")
                        && body.contains("drawDeleteAnimationGhost(canvas, line, globalLine, y);"));
        assertTrue(
                "BUG: binary-safe rendering must not return before drawing the delete ghost.",
                body.indexOf("drawDeleteAnimationGhost(canvas, line, globalLine, y);")
                        < body.indexOf("return;", body.indexOf("editor.binaryRender.isBinarySafeRenderingEnabled()")));
    }

    private static String methodBody(String src, String signature) {
        int method = src.indexOf(signature);
        if (method < 0) throw new IllegalStateException("Method not found: " + signature);
        int start = src.indexOf('{', method);
        if (start < 0) throw new IllegalStateException("Method body not found: " + signature);
        int depth = 0;
        for (int i = start; i < src.length(); i++) {
            char c = src.charAt(i);
            if (c == '{') depth++;
            if (c == '}') {
                depth--;
                if (depth == 0) return src.substring(start, i + 1);
            }
        }
        throw new IllegalStateException("Unclosed method body: " + signature);
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
