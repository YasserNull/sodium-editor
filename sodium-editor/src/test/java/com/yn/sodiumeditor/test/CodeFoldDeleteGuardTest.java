package com.yn.sodiumeditor.test;

import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;

/** Guards destructive delete behavior for collapsed fold closing tokens. */
public class CodeFoldDeleteGuardTest {

  @Test
  public void deletingCollapsedFoldClosingToken_shouldDeleteWholeFoldRange() throws Exception {
    String src =
        readSource("sodium-editor/src/main/java/com/yn/sodiumeditor/io/EditorActions.java");

    String backspaceBody = methodBody(src, "deleteCharAtCursor()");
    assertTrue(
        "BUG: backspace on the closing token of a collapsed fold must delete the whole fold range, not only the hidden close char.",
        backspaceBody.contains("handleCodeFoldBeforeEdit();")
            && backspaceBody.contains("deleteCollapsedFoldIfDeletingClosingToken(false)"));
    assertTrue(
        "BUG: delayed backspace after an unloaded after-end line must restore the original cursor position before retrying; otherwise moving to folded code can delete characters there.",
        backspaceBody.contains("final int requestedLine = editor.cursor.cursorLine")
            && backspaceBody.contains("final int requestedChar = editor.cursor.cursorChar")
            && backspaceBody.contains("editor.cursor.cursorLine = requestedLine")
            && backspaceBody.contains("editor.cursor.cursorChar = requestedChar")
            && backspaceBody.contains("deleteCharAtCursor();")
            && !backspaceBody.contains("editor.post(this::deleteCharAtCursor)"));

    String forwardBody = methodBody(src, "deleteForwardAtCursor()");
    assertTrue(
        "BUG: forward-delete on the closing token of a collapsed fold must delete the whole fold range.",
        forwardBody.contains("deleteCollapsedFoldIfDeletingClosingToken(true)")
            && forwardBody.indexOf("deleteCollapsedFoldIfDeletingClosingToken(true)")
                < forwardBody.indexOf("handleCodeFoldBeforeEdit();"));
    assertTrue(
        "BUG: delayed forward-delete must restore the original cursor position before retrying, matching backspace behavior.",
        forwardBody.contains("final int requestedLine = editor.cursor.cursorLine")
            && forwardBody.contains("final int requestedChar = editor.cursor.cursorChar")
            && forwardBody.contains("editor.cursor.cursorLine = requestedLine")
            && forwardBody.contains("editor.cursor.cursorChar = requestedChar")
            && forwardBody.contains("deleteForwardAtCursor();")
            && !forwardBody.contains("editor.post(this::deleteForwardAtCursor)"));

    String helper = methodBody(src, "deleteCollapsedFoldIfDeletingClosingToken(boolean forwardDelete)");
    assertTrue(
        "BUG: collapsed-fold close-token delete must route through an in-memory pending edit, not selection replacement, because selection replacement can rewrite the backing file immediately when the range is outside the window.",
        helper.contains("resolveCloseCharIndex(range, endText)")
            && helper.contains("closeEnd")
            && helper.contains("applyCollapsedFoldRangeDelete(range, closeEnd)")
            && !helper.contains("replaceSelectionWithText(\"\")")
            && !helper.contains("rewriteReplaceRangeAsync"));
    String applyBody = methodBody(src, "applyCollapsedFoldRangeDelete(CodeFold.FoldRange range, int closeEnd)");
    assertTrue(
        "BUG: whole-fold close-token delete must update modifiedLines and record a pending EditOp only; the file should not be changed until save applies pending edits.",
        applyBody.contains("editor.windowRender.modifiedLines.put(range.startLine, merged)")
            && applyBody.contains("operators.lineCountDelta -= removedLineCount")
            && applyBody.contains("op.startLine = range.startLine")
            && applyBody.contains("op.endLine = range.endLine")
            && applyBody.contains("op.insertedText = \"\"")
            && applyBody.contains("operators.recorder.recordEdit")
            && !applyBody.contains("replaceSelectionWithText")
            && !applyBody.contains("rewriteReplaceRangeAsync"));
    assertTrue(
        "BUG: after deleting a collapsed fold range, stale fold intervals/caches must be cleared so the placeholder cannot keep rendering.",
        helper.contains("editor.codeFold.clearFoldRanges()")
            && helper.contains("editor.cursorAnimation.snapToPosition("));
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
