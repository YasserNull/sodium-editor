package com.yn.sodiumeditor.test;

import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;

/**
 * Regression guards for stale extra line numbers after line deletion.
 */
public class LineNumberVisibleClampGuardTest {

    @Test
    public void viewRender_shouldClampVisibleLinesToDocumentLineCount() throws Exception {
        String src = readSource("sodium-editor/src/main/java/com/yn/sodiumeditor/renderer/ViewRender.java");
        int at = src.indexOf("} else {");
        assertTrue("Expected normal branch in ViewRender.", at >= 0);
        String around = src.substring(at, Math.min(src.length(), at + 700));
        assertTrue(
                "BUG: ViewRender should clamp visible indices using getLinesCount().",
                around.contains("editor.view.getLinesCount()")
                        && around.contains("Math.min(lastVisibleIndex, totalLines - 1)"));
    }

    @Test
    public void lineNumber_shouldClampCachedUnwrappedRangeToDocumentLineCount() throws Exception {
        String src = readSource("sodium-editor/src/main/java/com/yn/sodiumeditor/core/linenumber/LineNumber.java");
        int at = src.indexOf("drawLineNumbersCachedUnwrapped");
        assertTrue("Expected drawLineNumbersCachedUnwrapped in LineNumber.", at >= 0);
        String around = src.substring(at, Math.min(src.length(), at + 1200));
        assertTrue(
                "BUG: LineNumber cached unwrapped draw range should clamp to getLinesCount().",
                around.contains("editor.view.getLinesCount()")
                        && around.contains("Math.min(lI, totalLines - 1)")
                        && around.contains("Math.min(lL, totalLines - 1)")
                        || around.contains("editor.view.getLinesCount()")
                        && around.contains("int drawLastI = Math.min(lI, totalLines - 1)")
                        && around.contains("int drawLastL = Math.min(lL, totalLines - 1)"));
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
