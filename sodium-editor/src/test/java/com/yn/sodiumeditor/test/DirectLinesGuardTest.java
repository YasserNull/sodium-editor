package com.yn.sodiumeditor.test;

import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Files;
import org.junit.Test;

/**
 * Regression test for "phantom render" bug:
 * ViewRender may fetch "direct lines" from disk even when there are pending in-memory edits,
 * which can cause deleted text to reappear visually.
 *
 * This is a source-level (static) test so it can run in plain JVM unit tests on Termux.
 * Once the bug is fixed, ViewRender should contain a clear guard that prevents calling
 * populateDirectLinesForRange(...) while there are pending edits (e.g. modifiedLines not empty
 * or lineCountDelta != 0).
 */
public class DirectLinesGuardTest {

    @Test
    public void viewRender_shouldGuardDirectLineReadsWhenPendingEdits() throws Exception {
        Path viewRender = findViewRenderPath();
        String src = new String(Files.readAllBytes(viewRender), StandardCharsets.UTF_8);

        boolean callsPopulate = src.contains("populateDirectLinesForRange");
        assertTrue("Expected ViewRender to call populateDirectLinesForRange()", callsPopulate);

        // Expect a guard near the call site mentioning pending edits (modifiedLines/lineCountDelta).
        // This should FAIL until the runtime bug is fixed.
        int at = src.indexOf("populateDirectLinesForRange");
        int from = Math.max(0, at - 900);
        int to = Math.min(src.length(), at + 200);
        String around = src.substring(from, to);

        boolean hasGuard =
                around.contains("modifiedLines")
                        || around.contains("lineCountDelta")
                        || around.contains("pendingEdits")
                        || around.contains("hasPendingEdits");
        assertTrue(
                "BUG: ViewRender lacks a guard to stop direct-line disk reads while edits are pending (phantom render).",
                hasGuard);
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
}
