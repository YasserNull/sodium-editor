package com.yn.sodiumeditor.test;

import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;

/** Guards keyboard/IME select-all delete so it clears the rendered window immediately. */
public class SelectAllKeyboardDeleteFastPathGuardTest {

  @Test
  public void keyDownDelete_shouldUseSelectAllFastDeletePath() throws Exception {
    String src =
        readSource("sodium-editor/src/main/java/com/yn/sodiumeditor/input/events/OnKeyDown.java");
    String body = methodBody(src, "private boolean handleNormalKey(int keyCode, KeyEvent event)");

    int delCase = body.indexOf("case KeyEvent.KEYCODE_DEL:");
    int fast = body.indexOf("deleteEntireFileSelectionFast()", delCase);
    int replace = body.indexOf("replaceSelectionWithText(\"\")", delCase);

    assertTrue("Expected KEYCODE_DEL branch.", delCase >= 0);
    assertTrue(
        "BUG: select-all keyboard delete must clear the render window before the generic replace"
            + " path.",
        fast >= 0 && fast < replace);
  }

  @Test
  public void imeDeleteSurroundingText_shouldUseSelectAllFastDeletePath() throws Exception {
    String src = readSource("sodium-editor/src/main/java/com/yn/sodiumeditor/input/Ime.java");
    String body =
        methodBody(
            src, "private boolean deleteSurroundingCodePoints(int beforeLength, int afterLength)");

    int selection = body.indexOf("if (editor.selection.hasSelection)");
    int fast = body.indexOf("deleteEntireFileSelectionFast()", selection);
    int replace = body.indexOf("replaceSelectionWithText(\"\")", selection);

    assertTrue("Expected IME selection delete branch.", selection >= 0);
    assertTrue(
        "BUG: select-all IME delete must clear the render window before the generic replace path.",
        fast >= 0 && fast < replace);
  }

  @Test
  public void fastDelete_shouldNotReadRemovedTextOrStartLoadingBeforeClearingWindow()
      throws Exception {
    String src =
        readSource(
            "sodium-editor/src/main/java/com/yn/sodiumeditor/core/selection/SelectionActionHandler.java");
    String body = methodBody(src, "public void deleteEntireFileSelectionFast()");

    int clearWindow = body.indexOf("editor.windowRender.linesWindow.clear()");
    assertTrue("Expected fast delete to clear linesWindow.", clearWindow >= 0);
    assertTrue(
        "BUG: fast delete must not read selected file text on the UI thread.",
        !body.contains("readRangeText("));
    assertTrue(
        "BUG: fast delete should not show large-edit loading before clearing the screen.",
        !body.contains("beginLargeEditUiIfNeeded"));
  }

  @Test
  public void selectAllTyping_shouldUseImmediateFileBackedFastReplace() throws Exception {
    String src =
        readSource(
            "sodium-editor/src/main/java/com/yn/sodiumeditor/core/selection/SelectionActionHandler.java");
    String replace = methodBody(src, "public void replaceSelectionWithText(String insertText)");
    String fast = methodBody(src, "public void replaceEntireFileSelectionFast(String insertText)");

    int selectAllLike = replace.indexOf("final boolean selectAllLike");
    int fastCall = replace.indexOf("replaceEntireFileSelectionFast(insertText)", selectAllLike);
    int loading = replace.indexOf("beginLargeEditUiIfNeeded", selectAllLike);

    assertTrue("Expected select-all branch in replaceSelectionWithText.", selectAllLike >= 0);
    assertTrue(
        "BUG: typing over file-backed select-all must clear the render window immediately.",
        fastCall >= 0 && fastCall < loading);
    assertTrue(
        "Expected fast replace to clear linesWindow.",
        fast.contains("editor.windowRender.linesWindow.clear()"));
    assertTrue(
        "Expected fast replace to invalidate immediately.", fast.contains("editor.invalidate()"));
    assertTrue(
        "BUG: fast select-all typing must not use large-edit UI or read selected file text.",
        !fast.contains("beginLargeEditUiIfNeeded") && !fast.contains("readRangeText("));
    assertTrue(
        "BUG: fast select-all typing must prevent file-backed reload/render until save.",
        fast.contains("editor.fileIO.isIndexReady = false")
            && fast.contains("editor.fileIO.lineOffsets = new long[0]")
            && fast.contains("op.entireFileDelete = true"));
  }

  @Test
  public void fastDelete_shouldInvalidateBeforeDeferredCleanup() throws Exception {
    String src =
        readSource(
            "sodium-editor/src/main/java/com/yn/sodiumeditor/core/selection/SelectionActionHandler.java");
    String body = methodBody(src, "public void deleteEntireFileSelectionFast()");

    int clearWindow = body.indexOf("editor.windowRender.linesWindow.clear()");
    int invalidate = body.indexOf("editor.invalidate()");
    int deferred = body.indexOf("postDelayed");
    int invalidateStaleIo = body.indexOf("invalidatePendingIOVersionForEdit");
    int clearStreamed = body.indexOf("clearStreamedLineCaches");

    assertTrue("Expected fast delete to clear linesWindow.", clearWindow >= 0);
    assertTrue("Expected fast delete to invalidate immediately.", invalidate >= 0);
    assertTrue("Expected fast delete to defer cleanup.", deferred >= 0);
    assertTrue(
        "BUG: fast delete must request redraw before deferred cleanup can stall the UI.",
        clearWindow < invalidate && invalidate < deferred);
    assertTrue(
        "BUG: stale IO invalidation and streamed cache clearing must be deferred until after"
            + " immediate redraw request.",
        deferred < invalidateStaleIo && deferred < clearStreamed);
    assertTrue(
        "BUG: deferred fast delete cleanup must not remove queued Save IO.",
        !body.substring(deferred).contains("invalidatePendingIOForEdit"));
  }

  @Test
  public void fastDelete_shouldRemainUndoableWithoutFullTextSnapshot() throws Exception {
    String selection =
        readSource(
            "sodium-editor/src/main/java/com/yn/sodiumeditor/core/selection/SelectionActionHandler.java");
    String fastDelete = methodBody(selection, "public void deleteEntireFileSelectionFast()");
    String undo = readSource("sodium-editor/src/main/java/com/yn/sodiumeditor/io/Undo.java");

    assertTrue(
        "BUG: fast select-all delete must push an undo op instead of recordEditNoUndo().",
        fastDelete.contains("editor.editOperators.undoStack.addLast(op)")
            && fastDelete.contains("editor.editOperators.pendingEdits.addLast(op)")
            && !fastDelete.contains("recordReplaceSelectionEdit("));
    assertTrue(
        "BUG: undo for fast select-all delete must restore from the unchanged source file without a"
            + " full removedText snapshot.",
        undo.contains("restoreDeletedFileBackedSelection(op)"));
  }

  @Test
  public void saveAfterFastDelete_shouldPreserveBackupForLaterUndo() throws Exception {
    String editOp = readSource("sodium-editor/src/main/java/com/yn/sodiumeditor/io/EditOp.java");
    String save =
        readSource("sodium-editor/src/main/java/com/yn/sodiumeditor/io/FileEditHandler.java");
    String undo = readSource("sodium-editor/src/main/java/com/yn/sodiumeditor/io/Undo.java");

    assertTrue(
        "BUG: fast delete undo after save needs a file-backed removed-text backup, not a RAM"
            + " snapshot.",
        editOp.contains("removedTextBackupFile"));
    assertTrue(
        "BUG: fast delete must mark the op as an entire-file delete even if selection end was"
            + " stale.",
        editOp.contains("entireFileDelete"));
    assertTrue(
        "BUG: save must copy the removed range before rewriting the source file.",
        save.contains("ensureRemovedTextBackupForUndo(op)")
            && save.contains("op.entireFileDelete")
            && save.contains("sourceFile.length()"));
    assertTrue(
        "BUG: saving an entire-file delete/replace op must replace the whole source file, not a"
            + " stale window range.",
        save.contains("rewriteEntireFileReplaceBlocking") && save.contains("op.entireFileDelete"));
    assertTrue(
        "BUG: undo after save must restore from the backup file because sourceFile was already"
            + " rewritten.",
        undo.contains("restoreDeletedSelectionFromBackup(op)"));
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
