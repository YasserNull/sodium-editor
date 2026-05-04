package com.yn.sodiumeditor.test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;

/** Guards against fold markers appearing on lines without opening brackets. */
public class CodeFoldMarkerGuardTest {

    @Test
    public void isIndentFoldCandidate_shouldRejectClosingBracketBeforeColon() throws Exception {
        String src = readSource("sodium-editor/src/main/java/com/yn/sodiumeditor/core/fold/CodeFoldDetector.java");
        String body = methodBody(src, "isIndentFoldCandidate(String");

        assertTrue(
                "BUG: isIndentFoldCandidate must reject lines like '}:'. It should check that the character before ':' is not a closing bracket.",
                containsClosingBracketGuard(body));
    }

    @Test
    public void isIndentFoldCandidate_shouldNotShowFoldMarkerOnClosingBracketLines() throws Exception {
        String src = readSource("sodium-editor/src/main/java/com/yn/sodiumeditor/core/fold/CodeFoldDetector.java");
        String body = methodBody(src, "isIndentFoldCandidate(String");

        // Ensure the method doesn't just check for trailing ':' without context
        assertFalse(
                "BUG: isIndentFoldCandidate should not treat '}:', '])', etc. as fold candidates.",
                isTrivialColonCheck(body));
    }

    @Test
    public void handleCodeFoldNewline_shouldNotShiftFoldRangeWhenCursorAfterOpenBracket() throws Exception {
        String src = readSource("sodium-editor/src/main/java/com/yn/sodiumeditor/io/EditorActions.java");
        String body = methodBody(src, "handleCodeFoldNewline(int beforeLine, int beforeChar");

        assertTrue(
                "BUG: handleCodeFoldNewline must check if cursor is before or after the opening bracket. "
                        + "If cursor is after the bracket (beforeChar > openCharIndex), fold range should NOT shift to next line. "
                        + "Otherwise empty lines get fold markers.",
                hasCursorPositionCheck(body));
    }

    private static boolean hasCursorPositionCheck(String methodBody) {
        return (methodBody.contains("beforeChar") || methodBody.contains("cursorChar"))
                && (methodBody.contains("openCharIndex") || methodBody.contains("openChar"));
    }

    private static boolean containsClosingBracketGuard(String methodBody) {
        return methodBody.contains("beforeColon")
                || methodBody.contains("prevChar")
                || methodBody.contains("charAt") && (methodBody.contains("- 1") || methodBody.contains("- 2"));
    }

    private static boolean isTrivialColonCheck(String methodBody) {
        // A trivial check is one that only does: trimmed.endsWith(":")
        // without any additional context validation
        boolean hasEndsWithColon = methodBody.contains("endsWith(\":\")") || methodBody.contains("endsWith(':')");
        boolean hasAdditionalCheck = methodBody.contains("charAt") || methodBody.contains("substring")
                || methodBody.contains("lastIndexOf") || methodBody.contains("matches(");
        return hasEndsWithColon && !hasAdditionalCheck;
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
