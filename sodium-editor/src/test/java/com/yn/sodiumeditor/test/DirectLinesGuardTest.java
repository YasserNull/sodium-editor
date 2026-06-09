package com.yn.sodiumeditor.test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Files;
import org.junit.Test;

/**
 * Regression test for direct-line disk reads with pending edits.
 *
 * This is a source-level (static) test so it can run in plain JVM unit tests on Termux.
 * Direct reads are allowed for unchanged lines.
 * start/end line text. The guard must live at the line read/cache layer so modified lines never
 * get replaced by stale disk content.
 */
public class DirectLinesGuardTest {

    @Test
    public void viewRender_shouldGuardDirectLineReadsWhenPendingEdits() throws Exception {
        Path viewRender = findViewRenderPath();
        String src = new String(Files.readAllBytes(viewRender), StandardCharsets.UTF_8);

        boolean callsPopulate = src.contains("populateDirectLinesForRange");
        assertFalse("BUG: ViewRender draw path must not perform disk-backed direct reads.", callsPopulate);

        String fileCache = readSource("sodium-editor/src/main/java/com/yn/sodiumeditor/io/FileCache.java");
        String cacheBody = methodBody(fileCache, "populateDirectLinesForRange");
        boolean hasGuard =
                cacheBody.contains("editor.windowRender.hasModifiedLine(l)")
                        && cacheBody.contains("editor.windowRender.hasModifiedLine(cur)")
                        && cacheBody.contains("!editor.windowRender.hasModifiedLine(e.getKey())")
                        && cacheBody.contains("editor.editOperators.lineCountDelta != 0")
                        && cacheBody.contains("getFirstModifiedLine()");
        String windowRender =
                readSource("sodium-editor/src/main/java/com/yn/sodiumeditor/renderer/WindowRender.java");
        String lineBody = methodBody(windowRender, "getLineTextForRenderWithDirect");
        boolean renderGuard =
                lineBody.contains("String mod = getModifiedLine(line)")
                        && lineBody.contains("boolean canUseFileLine = canUseFileBackedLineForRender(line)")
                        && lineBody.indexOf("String mod = getModifiedLine(line)")
                                < lineBody.indexOf("boolean canUseFileLine = canUseFileBackedLineForRender(line)");
        assertTrue(
                "BUG: direct-line reads must skip modified lines and only use disk-backed content when the line is safe.",
                hasGuard && renderGuard);
    }

    private static String readSource(String rel) throws Exception {
        return new String(Files.readAllBytes(findPath(rel)), StandardCharsets.UTF_8);
    }

    private static String methodBody(String src, String methodName) {
        int method = src.indexOf(methodName);
        if (method < 0) throw new IllegalStateException("Method not found: " + methodName);
        int start = src.indexOf('{', method);
        if (start < 0) throw new IllegalStateException("Method body not found: " + methodName);
        int depth = 0;
        for (int i = start; i < src.length(); i++) {
            char c = src.charAt(i);
            if (c == '{') depth++;
            if (c == '}') {
                depth--;
                if (depth == 0) return src.substring(start, i + 1);
            }
        }
        throw new IllegalStateException("Unclosed method body: " + methodName);
    }

    private static Path findViewRenderPath() {
        // Gradle's working directory can vary; walk upwards from user.dir to find the repo root.
        Path cwd = new File(System.getProperty("user.dir", ".")).toPath().toAbsolutePath().normalize();
        for (int i = 0; i < 8; i++) {
            Path candidate =
                    cwd.resolve("sodium-editor/src/main/java/com/yn/sodiumeditor/renderer/ViewRender.java");
            if (Files.exists(candidate)) return candidate;
            Path parent = cwd.getParent();
            if (parent == null) break;
            cwd = parent;
        }
        // Fallback: maybe the module is the working dir.
        Path candidate =
                new File(".")
                        .toPath()
                        .toAbsolutePath()
                        .normalize()
                        .resolve("src/main/java/com/yn/sodiumeditor/renderer/ViewRender.java");
        if (Files.exists(candidate)) return candidate;
        throw new IllegalStateException("Could not locate ViewRender.java from test working directory.");
    }

    private static Path findPath(String rel) {
        Path cwd = new File(System.getProperty("user.dir", ".")).toPath().toAbsolutePath().normalize();
        for (int i = 0; i < 8; i++) {
            Path candidate = cwd.resolve(rel);
            if (Files.exists(candidate)) return candidate;
            Path parent = cwd.getParent();
            if (parent == null) break;
            cwd = parent;
        }
        Path candidate = new File(".").toPath().toAbsolutePath().normalize().resolve(rel);
        if (Files.exists(candidate)) return candidate;
        throw new IllegalStateException("Could not locate file: " + rel);
    }
}
