package com.yn.sodiumeditor.test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;

/** Guards paste/multi-character insert from taking the per-character typing path. */
public class PasteBatchInsertGuardTest {

  @Test
  public void insertTextAtCursor_shouldBatchMultiCharacterPaste() throws Exception {
    String src =
        readSource("sodium-editor/src/main/java/com/yn/sodiumeditor/io/EditorActions.java");
    String body = methodBody(src, "public void insertTextAtCursor(String text)");

    assertFalse(
        "BUG: paste must not loop through insertCharAtCursor for every character; that repeats"
            + " highlight, width, invalidate, and autocomplete work.",
        body.contains("for (char c : text.toCharArray()) insertCharAtCursor(c)"));
    assertTrue(
        "BUG: multi-character insert should use a batch window edit path.",
        body.contains("insertTextAtCursorBatch(text)"));
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
    throw new IllegalStateException("Could not locate source file: " + relativePath);
  }
}
