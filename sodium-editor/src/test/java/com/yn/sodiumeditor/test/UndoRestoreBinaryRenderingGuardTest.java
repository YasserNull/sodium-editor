package com.yn.sodiumeditor.test;

import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;

/**
 * Regression guard: undoing an entire-file delete must restore binary rendering.
 *
 * <p>The entire-file select-all delete/replace fast path disables the binary feature policy (see
 * {@code SelectionActionHandler.resetBinaryStateForEntireFileReplace()}). When the delete is later
 * undone, the restored file bytes come back on disk, but if binary rendering is not re-established
 * the window reload uses text line semantics while the cursor/scroll still reference binary row
 * indices, so the load lands past EOF and the editor stays blank even though the file is intact.
 */
public class UndoRestoreBinaryRenderingGuardTest {

  @Test
  public void backupRestore_mustResyncBinaryRendering() throws Exception {
    String src = readSource("sodium-editor/src/main/java/com/yn/sodiumeditor/io/Undo.java");
    String body = methodBody(src, "private boolean restoreDeletedSelectionFromBackup(");
    assertTrue(
        "BUG: undo restore from backup must re-establish binary rendering for the restored file,"
            + " otherwise the editor stays blank after undoing a binary select-all delete.",
        body.contains("resyncBinaryRenderingForSourceFile()"));
  }

  @Test
  public void sourceRestore_mustResyncBinaryRendering() throws Exception {
    String src = readSource("sodium-editor/src/main/java/com/yn/sodiumeditor/io/Undo.java");
    String body = methodBody(src, "private boolean restoreDeletedFileBackedSelection(");
    assertTrue(
        "BUG: undo restore from the source file must re-establish binary rendering for the"
            + " restored file.",
        body.contains("resyncBinaryRenderingForSourceFile()"));
  }

  @Test
  public void resyncHelper_mustReapplyPolicyAndReopenBinaryDocument() throws Exception {
    String src = readSource("sodium-editor/src/main/java/com/yn/sodiumeditor/io/Undo.java");
    String body = methodBody(src, "private void resyncBinaryRenderingForSourceFile(");
    assertTrue(
        "BUG: the resync helper must re-apply the binary feature policy from the current file.",
        body.contains("applyBinaryFileFeaturePolicy("));
    assertTrue(
        "BUG: the resync helper must reopen the BinaryDocument so its cached file length is fresh.",
        body.contains("openBinaryDocument("));
    assertTrue(
        "BUG: the resync helper must consult current file metadata before enabling binary rendering.",
        body.contains("isBinaryFile("));
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
