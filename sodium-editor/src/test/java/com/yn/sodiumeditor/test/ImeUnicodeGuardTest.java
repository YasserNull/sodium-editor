package com.yn.sodiumeditor.test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;

/** Source-level guards for Unicode-friendly IME behavior. */
public class ImeUnicodeGuardTest {

  @Test
  public void ime_shouldNotAdvertisePasswordInputType() throws Exception {
    String src = readSource("sodium-editor/src/main/java/com/yn/sodiumeditor/input/Ime.java");

    assertFalse(
        "BUG: password input types can make keyboards suppress emoji and symbol input.",
        src.contains("TYPE_TEXT_VARIATION_VISIBLE_PASSWORD"));
    assertTrue(
        "Expected the editor to advertise normal text input to IMEs.",
        src.contains("TYPE_TEXT_VARIATION_NORMAL"));
  }

  @Test
  public void inputConnection_shouldHandleCodePointDeletion() throws Exception {
    String src =
        readSource(
            "sodium-editor/src/main/java/com/yn/sodiumeditor/input/SodiumInputConnection.java");

    assertTrue(
        "BUG: emoji-aware keyboards call deleteSurroundingTextInCodePoints().",
        src.contains("deleteSurroundingTextInCodePoints"));
  }

  @Test
  public void editorActions_shouldDeleteWholeCodePoints() throws Exception {
    String src =
        readSource("sodium-editor/src/main/java/com/yn/sodiumeditor/io/EditorActions.java");

    assertTrue(
        "BUG: deletion must step by Unicode code point, not one UTF-16 char.",
        src.contains("offsetByCodePoints"));
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
