package com.yn.sodiumeditor.test;

import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;

/** Regression guard: gutter line selection must select whole lines, not only line text. */
public class LineNumberWholeLineSelectionGuardTest {

  @Test
  public void lineNumberSelection_shouldExtendAcrossLineBreaks() throws Exception {
    String src =
        readSource(
            "sodium-editor/src/main/java/com/yn/sodiumeditor/core/linenumber/LineNumberSelection.java");

    assertTrue(
        "BUG: gutter selection should route through whole-line selection helper.",
        src.contains("applyWholeLineSelection(clamped, clamped, total)")
            && src.contains("applyWholeLineSelection(startLine, endLine, total)"));

    assertTrue(
        "BUG: whole-line selection should include next line start for non-last lines.",
        src.contains("editor.selection.setSelectionInternal(safeStart, 0, safeEnd + 1, 0)"));

    assertTrue(
        "BUG: whole-line selection should include previous line newline for last-line deletion.",
        src.contains(
            "editor.selection.setSelectionInternal(safeStart - 1, prevLen, safeEnd, endLen)"));
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
