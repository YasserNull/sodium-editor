package com.yn.sodiumeditor.test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;

/** Regression guard for stale total line count during structural edits on indexed files. */
public class GetLinesCountStructuralEditGuardTest {

  @Test
  public void viewGetLinesCount_shouldNotUseStaleWindowCountDuringIndexedStructuralEdits()
      throws Exception {
    String src = readSource("sodium-editor/src/main/java/com/yn/sodiumeditor/core/view/View.java");
    int at = src.indexOf("if (editor.fileIO.isIndexReady && editor.fileIO.lineOffsets.length > 0)");
    assertTrue("Expected indexed getLinesCount branch in View.", at >= 0);
    String around = src.substring(at, Math.min(src.length(), at + 1200));

    assertTrue(
        "BUG: getLinesCount should compute count from lineOffsets + lineCountDelta.",
        around.contains("editor.fileIO.lineOffsets.length + editor.editOperators.lineCountDelta"));
    assertFalse(
        "BUG: getLinesCount must not let stale windowCount override indexed structural edit count.",
        around.contains("Math.max(count, windowCount)"));
  }

  private static String readSource(String relativePath) throws Exception {
    Path cwd = new File(System.getProperty("user.dir", ".")).toPath().toAbsolutePath().normalize();
    for (int i = 0; i < 8; i++) {
      Path candidate = cwd.resolve(relativePath);
      if (Files.exists(candidate)) {
        return new String(Files.readAllBytes(candidate), StandardCharsets.UTF_8);
      }
      Path parent = cwd.getParent();
      if (parent == null) break;
      cwd = parent;
    }
    Path fallback =
        new File(".")
            .toPath()
            .toAbsolutePath()
            .normalize()
            .resolve(relativePath.replace("sodium-editor/", ""));
    if (Files.exists(fallback)) {
      return new String(Files.readAllBytes(fallback), StandardCharsets.UTF_8);
    }
    throw new IllegalStateException("Could not locate source file: " + relativePath);
  }
}
