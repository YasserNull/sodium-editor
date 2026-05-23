package com.yn.sodiumeditor.test;

import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;

/**
 * Regression guards for selection rendering behavior.
 */
public class SelectionMultiLineRenderGuardTest {

    @Test
    public void selectionRenderer_shouldComputePerLineSelectionBounds() throws Exception {
        assertRendererContains(
                "sodium-editor/src/main/java/com/yn/sodiumeditor/renderer/ViewRender.java",
                "if (editor.selection.hasSelection && selPaint != null)",
                "int startChar = (i == selStartLine) ? selStartChar : 0;",
                "int endChar = (i == selEndLine) ? selEndChar : line.length();");
    }

    @Test
    public void selectionRenderer_shouldKeepSingleLineSelectionBetweenHandles() throws Exception {
        assertRendererContains(
                "sodium-editor/src/main/java/com/yn/sodiumeditor/renderer/ViewRender.java",
                "if (editor.selection.hasSelection && selPaint != null)",
                "float left = isSingleLine",
                "? startX",
                "float right = (isFirstLine && !isSingleLine) || fillsWholeLine",
                ": endX;");
    }

    @Test
    public void selectionRenderer_shouldStartFirstMultiLineRowAtStartHandleAndFillViewportRight() throws Exception {
        assertRendererContains(
                "sodium-editor/src/main/java/com/yn/sodiumeditor/renderer/ViewRender.java",
                "if (editor.selection.hasSelection && selPaint != null)",
                ": isFirstLine && !isSingleLine",
                "? startX",
                "? viewportRight");
    }

    @Test
    public void selectionRenderer_shouldFillMiddleRowsAcrossVisibleViewportWhileScrolled() throws Exception {
        assertRendererContains(
                "sodium-editor/src/main/java/com/yn/sodiumeditor/renderer/ViewRender.java",
                "if (editor.selection.hasSelection && selPaint != null)",
                "float viewportLeft = editor.scroll.getEffectiveScrollX();",
                "float viewportRight = viewportLeft + editor.getWidth() - editor.lineNumber.lineNumbersGutterWidth;",
                "boolean fillsWholeLine = !isSingleLine && !isFirstLine && !isLastLine;",
                ": fillsWholeLine",
                "? viewportLeft",
                "|| fillsWholeLine",
                "? viewportRight");
    }

    @Test
    public void selectionRenderer_shouldClampLastRowToEndHandle() throws Exception {
        assertRendererContains(
                "sodium-editor/src/main/java/com/yn/sodiumeditor/renderer/ViewRender.java",
                "if (editor.selection.hasSelection && selPaint != null)",
                "float endX = textStartX + editor.textRender.measureTextWithVisualSpaces(line, 0, endChar, editor.textRender.paint);",
                ": endX;");
    }

    @Test
    public void selectionRenderer_shouldPreserveCornerRoundingByRowRole() throws Exception {
        assertRendererContains(
                "sodium-editor/src/main/java/com/yn/sodiumeditor/renderer/ViewRender.java",
                "if (editor.selection.hasSelection && selPaint != null)",
                "if (isSingleLine)",
                "true, true, true, true, selPaint",
                "else if (isFirstLine)",
                "true, true, false, false, selPaint",
                "else if (isLastLine)",
                "false, false, true, true, selPaint");
    }

    private static void assertRendererContains(String path, String anchor, String... snippets) throws Exception {
        String src = readSource(path);
        int at = src.indexOf(anchor);
        assertTrue("Expected selection drawing branch in " + path, at >= 0);
        String around = src.substring(at, Math.min(src.length(), at + 4200));
        for (String snippet : snippets) {
            assertTrue("Expected snippet missing in " + path + ": " + snippet, around.contains(snippet));
        }
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
