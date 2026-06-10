package com.yn.sodiumeditor.test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;

/** Static guard that binary-safe rendering does not bypass typed character animation. */
public class CharAnimationBinaryRenderGuardTest {

  @Test
  public void binaryRender_shouldReceiveCharFadeRange() throws Exception {
    String highlight =
        readSource("sodium-editor/src/main/java/com/yn/sodiumeditor/renderer/HighlightRender.java");
    String binary =
        readSource(
            "sodium-editor/src/main/java/com/yn/sodiumeditor/renderer/draw/BinaryLineDrawer.java");

    assertTrue(
        "BUG: binary-safe render path must pass char fade range instead of bypassing animation.",
        highlight.contains("drawBinaryLineSliceWithFade(")
            && highlight.contains("charAnimStartChar - sliceStart")
            && highlight.contains("charAnimEndChar - sliceStart"));
    assertTrue(
        "BUG: binary line drawer must draw the overlapping character with animated alpha.",
        binary.contains("drawBinaryTextRunWithFade(")
            && binary.contains("charAnimTmpPaint.setAlpha((int) (baseAlpha * alpha))")
            && binary.contains("getCharAnimOffsetY(alpha, paint)"));
    assertFalse(
        "BUG: binary typed char animation must be fade-only; do not move the glyph vertically.",
        binary.contains("paint.getTextSize() * 0.35f"));
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
