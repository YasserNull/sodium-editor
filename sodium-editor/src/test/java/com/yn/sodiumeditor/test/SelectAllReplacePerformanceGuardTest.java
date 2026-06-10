package com.yn.sodiumeditor.test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;

/** Guards select-all delete/paste from doing UI-thread full-file work. */
public class SelectAllReplacePerformanceGuardTest {

  @Test
  public void selectAllReplace_shouldNotLogSnapshotsByDefault() throws Exception {
    String src =
        readSource(
            "sodium-editor/src/main/java/com/yn/sodiumeditor/core/selection/SelectionActionHandler.java");
    String handle = methodBody(src, "private void handleSelectAllReplace");

    assertTrue(
        "BUG: selection edit debug logging must be off by default.",
        src.contains("public static boolean DEBUG_SELECTION_EDIT_LOGS = false"));
    assertFalse(
        "BUG: select-all replace logging must not build a full text snapshot on the UI thread.",
        handle.contains("editor.fileIO.getTextSnapshot()"));
  }

  @Test
  public void selectAllPaste_shouldUseBoundedPreviewForLargeText() throws Exception {
    String src =
        readSource(
            "sodium-editor/src/main/java/com/yn/sodiumeditor/core/selection/SelectionActionHandler.java");
    String handle = methodBody(src, "private void handleSelectAllReplace");

    assertTrue(
        "BUG: large select-all paste must avoid split() over the full pasted text for UI preview.",
        handle.contains("shouldUseBoundedSelectAllPreview(insertText, insNl)"));
    assertTrue(
        "BUG: large select-all paste should populate only a bounded window preview.",
        src.contains("populateBoundedSelectAllPreview(insertText, insertedEndLine)"));
  }

  @Test
  public void selectAllReplace_shouldNotReadRemovedTextBeforeClearingScreen() throws Exception {
    String src =
        readSource(
            "sodium-editor/src/main/java/com/yn/sodiumeditor/core/selection/SelectionActionHandler.java");
    String body = methodBody(src, "public void replaceSelectionWithText(String insertText)");

    int selectAllLike = body.indexOf("final boolean selectAllLike");
    int readRangeText = body.indexOf("editor.fileIO.readRangeText");
    int handleSelectAll = body.indexOf("handleSelectAllReplace(");

    assertTrue("Expected select-all branch in replaceSelectionWithText.", selectAllLike >= 0);
    assertTrue(
        "Expected non-select-all replacement to read removed text for undo.", readRangeText >= 0);
    assertTrue("Expected select-all replacement handler.", handleSelectAll >= 0);
    assertTrue(
        "BUG: select-all delete/paste must clear the screen before any full-range file read.",
        selectAllLike < readRangeText && handleSelectAll < readRangeText);
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
