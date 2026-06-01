package com.yn.sodiumeditor.test;

import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;

public class SelectionKeyboardDeleteRegressionTest {

  @Test
  public void imeBackspaceWithSelection_shouldReplaceOnlySelection() throws Exception {
    String src = readSource("sodium-editor/src/main/java/com/yn/sodiumeditor/input/Ime.java");
    int at = src.indexOf("private boolean deleteSurroundingCodePoints");
    assertTrue("Expected deleteSurroundingCodePoints in Ime.", at >= 0);
    String around = src.substring(at, Math.min(src.length(), at + 1000));

    assertTrue(around.contains("if (editor.selection.hasSelection)"));
    assertTrue(around.contains("editor.selection.replaceSelectionWithText(\"\")"));
    assertTrue(around.contains("return true;"));
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
    Path candidate = new File(".").toPath().toAbsolutePath().normalize().resolve(rel);
    if (Files.exists(candidate)) return candidate;
    throw new IllegalStateException("Could not locate file: " + rel);
  }
}
