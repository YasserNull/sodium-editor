package com.yn.sodiumeditor.test;

import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;

/** Static guard that character animation is driven by view frames, not global animator scale. */
public class CharAnimationFrameClockGuardTest {

  @Test
  public void charAnimation_shouldUsePostOnAnimationFrameClock() throws Exception {
    String src =
        readSource(
            "sodium-editor/src/main/java/com/yn/sodiumeditor/renderer/animation/CharAnimation.java");

    assertTrue(
        "BUG: char animation should use postOnAnimation so Android animator scale cannot skip it.",
        src.contains("editor.postOnAnimation(step)")
            && src.contains("SystemClock.uptimeMillis() - startUptime")
            && src.contains("charAnimToken")
            && src.contains("delAnimToken"));
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
