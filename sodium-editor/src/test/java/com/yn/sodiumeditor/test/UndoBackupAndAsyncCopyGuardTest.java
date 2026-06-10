package com.yn.sodiumeditor.test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;

/** Guards cleanup of file-backed undo backups and avoids large UI-thread copies. */
public class UndoBackupAndAsyncCopyGuardTest {

  @Test
  public void undoHistoryDisposesBackupFilesWhenDroppingOps() throws Exception {
    String src =
        readSource("sodium-editor/src/main/java/com/yn/sodiumeditor/io/UndoRedoHistory.java");
    assertTrue(src.contains("dispose("));
    assertTrue(src.contains("removedTextBackupFile.delete()"));
    assertTrue(methodBody(src, "public void clear(").contains("dispose("));
    assertTrue(methodBody(src, "public void pushUndo(").contains("dispose("));
  }

  @Test
  public void rewriteReplaceRangeAsyncDoesNotCopyLargeFileOnUiThread() throws Exception {
    String src =
        readSource("sodium-editor/src/main/java/com/yn/sodiumeditor/io/FileEditHandler.java");
    String body = methodBody(src, "public void rewriteReplaceRangeAsync(");
    int copy = body.indexOf("copyFileReplacing(");
    int uiPost =
        body.indexOf(
            "editor.post(() -> {\n"
                + "                    if (opToken != operators.editVersion.get()) return;",
            copy);
    assertTrue(
        "BUG: async rewrite should copy temp output into source before posting UI updates.",
        copy >= 0);
    assertTrue("BUG: file copy must happen before editor.post UI section.", uiPost > copy);
    assertFalse(
        "BUG: UI section must not open FileInputStream for huge files.",
        body.contains(
            "editor.post(() -> {\n"
                + "                    if (opToken != operators.editVersion.get()) return;\n"
                + "                    editor.fileIO.invalidatePendingIO();\n\n"
                + "                    if (inFile != null) {\n"
                + "                        try (FileInputStream"));
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
