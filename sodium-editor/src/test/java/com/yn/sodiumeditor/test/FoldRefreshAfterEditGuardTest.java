package com.yn.sodiumeditor.test;

import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;

/** Guards fold marker recalculation after edits that add/remove brackets or lines. */
public class FoldRefreshAfterEditGuardTest {

  @Test
  public void codeFold_shouldRefreshFoldRangesForEditedRange() throws Exception {
    String src = readSource("sodium-editor/src/main/java/com/yn/sodiumeditor/core/fold/CodeFold.java");
    String body = methodBody(src, "refreshFoldRangesAroundRange(int startLine, int endLine)");

    assertTrue(
        "BUG: edited fold ranges must reparse bracket cache for the changed range.",
        body.contains("editor.bracketCache.invalidateLines(start, end)")
            && body.contains("editor.bracketCache.getLineInfo(line)"));
    assertTrue(
        "BUG: edited ranges must create fold ranges from both opening and closing brackets.",
        body.contains("findMatchingBracket(bp)")
            && body.contains("findMatchingOpeningBracket(bp)")
            && body.contains("putConfirmedBracketFoldRange"));
    assertTrue(
        "BUG: creating a new fold range must invalidate fold marker/interval caches.",
        body.contains("invalidateFoldCaches()")
            && body.contains("rebuildFoldIntervalsIfNeeded()"));
  }

  @Test
  public void newlineEdit_shouldRefreshFoldRangesAroundSplitLines() throws Exception {
    String src = readSource("sodium-editor/src/main/java/com/yn/sodiumeditor/io/EditorActions.java");
    String body = methodBody(src, "handleCodeFoldNewline(int beforeLine, int beforeChar)");

    assertTrue(
        "BUG: inserting a newline around braces must recalculate whether a fold marker should appear.",
        body.contains("editor.codeFold.refreshFoldRangesAroundRange(beforeLine, beforeLine + 1)"));
    assertTrue(
        "BUG: some newline side effects can clear fold ranges after the immediate refresh; schedule a final refresh after the edit settles.",
        body.contains("editor.post(() -> refreshFoldRangesAroundEdit(beforeLine))"));
  }

  @Test
  public void newlineInsert_shouldExposeNewLastLineBeforeFoldRefresh() throws Exception {
    String src = readSource("sodium-editor/src/main/java/com/yn/sodiumeditor/io/EditorActions.java");
    String body = methodBody(src, "insertCharAtCursor(char c)");

    assertTrue(
        "BUG: for indexed files, the inserted final line must be counted before fold refresh scans it.",
        body.indexOf("operators.lineCountDelta += 1")
            < body.indexOf("handleCodeFoldNewline(beforeLine, beforeChar)"));
  }

  @Test
  public void newlineDelete_shouldRefreshFoldRangesAfterLineCountUpdate() throws Exception {
    String src = readSource("sodium-editor/src/main/java/com/yn/sodiumeditor/io/EditorActions.java");
    String deleteForward = methodBody(src, "deleteForwardAtCursor()");
    String deleteBackward = methodBody(src, "deleteCharAtCursor()");

    assertTrue(
        "BUG: forward-deleting a newline can make the closing bracket the final line; fold ranges must be refreshed after lineCountDelta changes.",
        containsAfter(deleteForward, "operators.lineCountDelta -= 1", "refreshFoldRangesAroundRange("));
    assertTrue(
        "BUG: backspacing a newline can make the closing bracket the final line; fold ranges must be refreshed after lineCountDelta changes.",
        containsAfter(deleteBackward, "operators.lineCountDelta -= 1", "refreshFoldRangesAroundRange("));
  }

  @Test
  public void bracketDelete_shouldRefreshFoldRangesAfterInvalidation() throws Exception {
    String src = readSource("sodium-editor/src/main/java/com/yn/sodiumeditor/io/EditorActions.java");
    String deleteForward = methodBody(src, "deleteForwardAtCursor()");
    String deleteBackward = methodBody(src, "deleteCharAtCursor()");

    assertTrue(
        "BUG: deleting a bracket must rebuild fold ranges so an outer block marker reappears after inner braces are removed.",
        deleteForward.contains("TextUtils.containsBracketChars(removed)")
            && deleteForward.contains("refreshFoldRangesAroundEdit(editor.cursor.cursorLine"));
    assertTrue(
        "BUG: backspacing a bracket must rebuild fold ranges so an outer block marker reappears after inner braces are removed.",
        deleteBackward.contains("TextUtils.containsBracketChars(removed)")
            && deleteBackward.contains("refreshFoldRangesAroundEdit(editor.cursor.cursorLine"));
  }

  @Test
  public void bracketDeleteRefresh_shouldScanNeighborLinesNotOnlyDeletedLine() throws Exception {
    String src = readSource("sodium-editor/src/main/java/com/yn/sodiumeditor/io/EditorActions.java");
    String body = methodBody(src, "refreshFoldRangesAroundEdit(int line)");

    assertTrue(
        "BUG: after deleting a brace, the edited line can become empty; fold refresh must scan neighboring lines to rebuild the still-valid outer block.",
        body.contains("line -")
            && body.contains("line +")
            && body.contains("refreshFoldRangesAroundRange(start, end)"));
  }

  @Test
  public void selectionReplace_shouldRefreshFoldRangesForCutLines() throws Exception {
    String src =
        readSource(
            "sodium-editor/src/main/java/com/yn/sodiumeditor/core/selection/SelectionActionHandler.java");
    String body = methodBody(src, "invalidateFoldStateForReplace(int startLine, int endLine)");

    assertTrue(
        "BUG: cutting lines must recalculate fold ranges so stale fold markers disappear and new markers appear.",
        body.contains("invalidateFoldRangesIntersectingRange(startLine, endLine)")
            && body.contains("refreshFoldRangesAroundRange(startLine, endLine)"));
  }

  @Test
  public void multiLineSelectionReplace_shouldRefreshInsertedFoldLinesAfterLineCountUpdate()
      throws Exception {
    String src =
        readSource(
            "sodium-editor/src/main/java/com/yn/sodiumeditor/core/selection/SelectionActionHandler.java");
    String body = methodBody(src, "replaceSelectionWithText(String insertText)");

    assertTrue(
        "BUG: replacing text with newly typed/pasted bracket lines must refresh the inserted range, not only the old selected range.",
        body.contains("invalidateFoldStateForReplace(sL, target.line)"));
    assertTrue(
        "BUG: fold refresh for inserted lines must run after lineCountDelta is updated so the new lines are visible to the fold scanner.",
        body.indexOf("finalizeAction(") < body.indexOf("invalidateFoldStateForReplace(sL, target.line)"));
  }

  @Test
  public void bracketCacheScan_shouldNotOverwriteFoldRangesWhenMemoryEditsArePending()
      throws Exception {
    String src =
        readSource(
            "sodium-editor/src/main/java/com/yn/sodiumeditor/core/guides/bracket/BracketCache.java");
    String body = methodBody(src, "scanFileAsync(@Nullable Runnable onComplete)");

    assertTrue(
        "BUG: async full-file bracket scan reads from disk; it must not replace live fold ranges while unsaved in-memory edits exist.",
        body.contains("hasPendingInMemoryEdits()")
            && body.indexOf("hasPendingInMemoryEdits()")
                < body.indexOf("editor.codeFold.foldRanges.clear()"));
  }

  @Test
  public void lineShift_shouldClearBracketCacheBeforeFoldRefresh() throws Exception {
    String src = readSource("sodium-editor/src/main/java/com/yn/sodiumeditor/io/LineCacheShifter.java");
    String body = methodBody(src, "shiftModifiedLines(int startLine, int delta)");

    assertTrue(
        "BUG: inserting/deleting lines changes bracket line numbers; stale BracketCache entries make fold markers disappear or move to the wrong line.",
        body.contains("editor.bracketCache.clear()")
            && body.indexOf("editor.bracketCache.clear()")
                < body.indexOf("editor.bracketGuides.shiftBracketGuideCaches(startLine, delta)"));
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

  private static boolean containsAfter(String src, String before, String after) {
    int beforeIndex = src.indexOf(before);
    return beforeIndex >= 0 && src.indexOf(after, beforeIndex + before.length()) >= 0;
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
    throw new IllegalStateException("Could not locate file: " + rel);
  }
}
