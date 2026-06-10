package com.yn.sodiumeditor.test;

import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;

/** Static regression guard for hardware-key character animation. */
public class OnKeyDownCharAnimationGuardTest {

  @Test
  public void printableHardwareKey_shouldStartCharAnimationAfterInsert() throws Exception {
    String src =
        readSource("sodium-editor/src/main/java/com/yn/sodiumeditor/input/events/OnKeyDown.java");
    int methodStart = src.indexOf("private boolean handleNormalKey");
    assertTrue("Expected handleNormalKey in OnKeyDown.", methodStart >= 0);
    int at = src.indexOf("String text = getPrintingText(event);", methodStart);
    assertTrue("Expected printable key handling in handleNormalKey.", at >= 0);
    String around = src.substring(at, Math.min(src.length(), at + 450));

    assertTrue(
        "BUG: printable hardware key input inserts text without starting char animation.",
        around.contains("editor.editOperators.insertTextAtCursor(text);")
            && around.contains("editor.charAnimation.startCharAnimationFromText(text);"));
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
