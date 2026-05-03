package com.yn.sodiumeditor.test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;

/** Guards against recursive and line-count-sized code-fold interval rebuilds. */
public class CodeFoldIntervalGuardTest {

    @Test
    public void rebuildFoldIntervals_shouldNotCallVisibleLineCount() throws Exception {
        String src = readSource("sodium-editor/src/main/java/com/yn/sodiumeditor/core/fold/CodeFold.java");
        String body = methodBody(src, "rebuildFoldIntervalsIfNeeded");

        assertFalse(
                "BUG: rebuildFoldIntervalsIfNeeded() must not call getVisibleLineCount(); that recurses until StackOverflowError.",
                body.contains("getVisibleLineCount("));
    }

    @Test
    public void visibleLineCount_shouldUseVisibleIntervalEnd() throws Exception {
        String src = readSource("sodium-editor/src/main/java/com/yn/sodiumeditor/core/fold/CodeFold.java");
        String body = methodBody(src, "getVisibleLineCount");

        assertTrue(
                "BUG: visible line count must return the last interval's visible end plus one, not global end.",
                body.contains("last[3] + 1"));
    }

    @Test
    public void codeFold_shouldNotAllocateLineCountSizedLookupArrays() throws Exception {
        String src = readSource("sodium-editor/src/main/java/com/yn/sodiumeditor/core/fold/CodeFold.java");

        assertFalse(
                "BUG: visible/global lookup arrays allocate memory proportional to document line count.",
                src.contains("visibleToGlobalLookup"));
        assertFalse(
                "BUG: visible/global lookup arrays allocate memory proportional to document line count.",
                src.contains("globalToVisibleLookup"));
        assertTrue(
                "Expected binary-search mapping over fold intervals.",
                src.contains("Binary search over"));
    }

    private static String methodBody(String src, String methodName) {
        int method = src.indexOf(methodName + "()");
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

    private static String readSource(String rel) throws Exception {
        return new String(Files.readAllBytes(findPath(rel)), StandardCharsets.UTF_8);
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
