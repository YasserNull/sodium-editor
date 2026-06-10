package com.yn.sodiumeditor.test;

import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;

/** Guards save-after-fast-delete from losing the backup/undo op to deferred IO cleanup. */
public class SelectAllDeleteSaveUndoRaceGuardTest {

  @Test
  public void fastSelectAllDeferredCleanup_mustNotRemoveQueuedSaveIo() throws Exception {
    String src =
        readSource(
            "sodium-editor/src/main/java/com/yn/sodiumeditor/core/selection/SelectionActionHandler.java");

    assertDeferredCleanupUsesVersionOnly(src, "public void deleteEntireFileSelectionFast()");
    assertDeferredCleanupUsesVersionOnly(
        src, "public void replaceEntireFileSelectionFast(String insertText)");
  }

  @Test
  public void fileIo_shouldExposeVersionOnlyInvalidationForDeferredEditCleanup() throws Exception {
    String src = readSource("sodium-editor/src/main/java/com/yn/sodiumeditor/io/FileIO.java");
    String body = methodBody(src, "public void invalidatePendingIOVersionForEdit()");

    assertTrue(
        "BUG: deferred fast-delete cleanup may invalidate stale reads, but must not remove queued"
            + " Save IO.",
        body.contains("ioTaskVersion.incrementAndGet()")
            && !body.contains("removeCallbacksAndMessages"));
    assertTrue(
        "Expected edit cleanup to keep highlight caches invalidated.",
        body.contains("editor.highlight.clearHighlightCaches()"));
  }

  private static void assertDeferredCleanupUsesVersionOnly(String src, String signature) {
    String body = methodBody(src, signature);
    int deferred = body.indexOf("postDelayed");
    assertTrue("Expected deferred cleanup in " + signature, deferred >= 0);

    String deferredBody = body.substring(deferred);
    assertTrue(
        "BUG: deferred fast-delete cleanup must not call invalidatePendingIOForEdit(); that removes"
            + " Save's queued file rewrite after pendingEdits was already cleared.",
        !deferredBody.contains("invalidatePendingIOForEdit()"));
    assertTrue(
        "Expected deferred cleanup to invalidate stale file-backed reads by version only.",
        deferredBody.contains("invalidatePendingIOVersionForEdit()"));
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
