package com.yn.sodiumeditor.test;

import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;

public class ImeComposingSaveRegressionTest {

  @Test
  public void shrinkingExistingComposingTextToEmpty_shouldRemainPendingDeletionForSave()
      throws Exception {
    String src = readSource("sodium-editor/src/main/java/com/yn/sodiumeditor/input/Ime.java");

    assertTrue(
        "BUG: IME composing replacement must remember original text from disk-backed lines.",
        src.contains("composingOriginalText"));
    assertTrue(
        "BUG: deleting existing composing text must keep a pending replacement op for save.",
        src.contains("if (text.isEmpty() && isEmptyOriginalComposingReplacement())"));
    assertTrue(
        "BUG: composing save op must replace the original composing range, not insert at cursor.",
        src.contains("op.endChar = getComposingOriginalEndChar(startLine, startChar);"));
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
