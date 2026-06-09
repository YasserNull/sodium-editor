package com.yn.sodiumeditor.test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;

/** Guards save/undo/redo corruption risks in file-backed editing. */
public class SaveUndoRedoCorruptionGuardTest {

  @Test
  public void largePasteDoesNotRewriteSourceFileBeforeSave() throws Exception {
    String src = readSource("sodium-editor/src/main/java/com/yn/sodiumeditor/io/EditorActions.java");
    String body = methodBody(src, "public void insertTextAtCursor(");
    assertFalse(
        "BUG: large paste must not rewrite the source file and then also enter pendingEdits.",
        body.contains("rewriteReplaceRangeAsync"));
    assertTrue(
        "BUG: large paste should flow through the in-memory pending edit path for explicit Save.",
        body.contains("insertTextAtCursorBatch(text)"));
  }

  @Test
  public void redoOfSavedUndoOnlyRemovesPendingInverse() throws Exception {
    String redo = readSource("sodium-editor/src/main/java/com/yn/sodiumeditor/io/Redo.java");
    String body = methodBody(redo, "public void execute(");
    assertTrue(body.contains("pendingUndoOfSavedOp"));
    assertTrue(body.contains("originalOp == op"));
    assertTrue(body.contains("pendingEdits.removeLast()"));
    assertFalse(
        "BUG: redo of a saved edit must not re-add the original op to pendingEdits.",
        body.contains("restoredUndoDirty"));

    String undo = readSource("sodium-editor/src/main/java/com/yn/sodiumeditor/io/Undo.java");
    String inverse = methodBody(undo, "private EditOp createPendingUndoSaveOp(");
    assertTrue(inverse.contains("pendingUndoOfSavedOp = true"));
    assertTrue(inverse.contains("originalOp = op"));
  }

  @Test
  public void redoEntireFileDeleteClearsRenderWindowImmediately() throws Exception {
    String redo = readSource("sodium-editor/src/main/java/com/yn/sodiumeditor/io/Redo.java");
    String body = methodBody(redo, "public void execute(");
    String helper = methodBody(redo, "private void applyEntireFileDeleteForRedo(");

    assertTrue(
        "BUG: redo of select-all delete must use a dedicated fast path, not a stale selection replace.",
        body.contains("op.entireFileDelete") && body.contains("applyEntireFileDeleteForRedo(op)"));
    assertTrue(helper.contains("editor.windowRender.linesWindow.clear()"));
    assertTrue(helper.contains("editor.windowRender.linesWindow.add(\"\")"));
    assertTrue(helper.contains("editor.windowRender.windowStartLine = 0"));
    assertTrue(helper.contains("editor.fileIO.isEof = true"));
    assertTrue(helper.contains("editor.windowRender.clearModifiedLines()"));
    assertTrue(helper.contains("editor.fileIO.lineOffsets = new long[0]"));
    assertTrue(helper.contains("editor.fileIO.isIndexReady = false"));
    assertTrue(helper.contains("editor.invalidate()"));
  }

  @Test
  public void saveSuccessDoesNotClearNewEditsMadeDuringSave() throws Exception {
    String src = readSource("sodium-editor/src/main/java/com/yn/sodiumeditor/io/FileEditHandler.java");
    String body = methodBody(src, "public void applyPendingEditsToFileAsync(");
    assertTrue(body.contains("saveStartVersion"));
    assertTrue(body.contains("computeDeltaForOps"));
    assertTrue(body.contains("noNewEdits"));
    assertTrue(body.contains("operators.lineCountDelta -= savedDelta"));
    assertFalse(
        "BUG: save success must not always clear modifiedLines regardless of new edits.",
        body.contains("editor.windowRender.clearModifiedLines();\n                    }\n                    synchronized"));
  }

  @Test
  public void byteRangesUseCharsetAwareCharToByteOffsets() throws Exception {
    String src = readSource("sodium-editor/src/main/java/com/yn/sodiumeditor/io/ByteRangeLocator.java");
    String indexed = methodBody(src, "private EditOp.RangeBytes computeByteRangeFromReadyIndex(");
    assertTrue(indexed.contains("byteOffsetForChar"));
    assertFalse("BUG: UTF-8 char indexes are not byte offsets.", indexed.contains("offsets[sL] + Math.max(0, sC)"));
    assertTrue(src.contains("CharsetDecoder"));
  }

  @Test
  public void fileWritesUseEditorFileCharsetAndFullChannelWrites() throws Exception {
    String src = readSource("sodium-editor/src/main/java/com/yn/sodiumeditor/io/FileEditHandler.java");
    assertFalse("BUG: save path must not hard-code UTF-8 for inserted text.", src.contains("StandardCharsets.UTF_8"));
    assertTrue(src.contains("editor.fileIO.fileCharset"));
    assertTrue(src.contains("readFully("));
    assertTrue(src.contains("writeFully("));
  }

  private static String readSource(String rel) throws Exception {
    return new String(Files.readAllBytes(findPath(rel)), StandardCharsets.UTF_8);
  }

  private static String methodBody(String src, String signaturePrefix) {
    int method = src.indexOf(signaturePrefix);
    if (method < 0) throw new IllegalStateException("Method not found: " + signaturePrefix);
    int start = src.indexOf('{', method);
    if (start < 0) throw new IllegalStateException("Method body not found: " + signaturePrefix);
    int depth = 0;
    for (int i = start; i < src.length(); i++) {
      char c = src.charAt(i);
      if (c == '{') depth++;
      if (c == '}') {
        depth--;
        if (depth == 0) return src.substring(start, i + 1);
      }
    }
    throw new IllegalStateException("Unclosed method body: " + signaturePrefix);
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
