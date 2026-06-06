package com.yn.sodiumeditor.test;

import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;

/** Guards undo restores that grow the document back past 99 lines. */
public class UndoRestoreLineNumberGutterGuardTest {

  @Test
  public void fileBackedUndoRestore_shouldRecomputeGutterWidthBeforeLayout() throws Exception {
    String src = readSource("sodium-editor/src/main/java/com/yn/sodiumeditor/io/Undo.java");

    assertRestoreUpdatesGutter(src, "undo.restore.backup.loaded");
    assertRestoreUpdatesGutter(src, "undo.restore.source.loaded");
  }

  private static void assertRestoreUpdatesGutter(String src, String marker) {
    int at = src.indexOf(marker);
    assertTrue("Expected undo restore marker: " + marker, at >= 0);
    String around = src.substring(at, Math.min(src.length(), at + 1400));
    int update = around.indexOf("editor.lineNumber.updateGutterWidth()");
    int requestLayout = around.indexOf("editor.requestLayout()");
    assertTrue(
        "BUG: undo restore must recompute gutter width after line count grows; otherwise 100+ can"
            + " render clipped as 0, 1, 2...",
        update >= 0 && requestLayout >= 0 && update < requestLayout);
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
