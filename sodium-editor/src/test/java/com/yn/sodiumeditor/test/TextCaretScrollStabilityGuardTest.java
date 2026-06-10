package com.yn.sodiumeditor.test;

import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;

/** Regression guard for keeping the text caret stable while scrolling. */
public class TextCaretScrollStabilityGuardTest {

  @Test
  public void textCaret_shouldRemainDocumentAnchoredAndApplyScrollAtDrawTime() throws Exception {
    String src =
        readSource("sodium-editor/src/main/java/com/yn/sodiumeditor/core/cursor/Caret.java");
    int at = src.indexOf("public void drawCaret(Canvas canvas)");
    assertTrue("Expected drawCaret in Caret.", at >= 0);
    String around = src.substring(at, Math.min(src.length(), at + 2400));

    assertTrue(
        "BUG: text caret should either use animated document coordinates or fall back to real caret"
            + " document coordinates before applying scroll.",
        around.contains("float cx;")
            && around.contains("float cy;")
            && around.contains("cx = editor.cursorAnimation.cursorDrawX;")
            && around.contains("cy = editor.cursorAnimation.cursorDrawY;")
            && around.contains("cx = getCaretDocumentX();")
            && around.contains("cy = getCaretDocumentY();"));

    assertTrue(
        "BUG: text caret should apply current scroll directly at draw time for LTR.",
        around.contains("float top = cy - editor.scroll.scrollY;")
            && around.contains("float left = cx - editor.scroll.scrollX;")
            && around.contains("left += editor.layout.getTextStartX();"));

    assertTrue(
        "BUG: text caret should apply current scroll directly at draw time for RTL.",
        around.contains(
            "left = (editor.getWidth() - editor.lineNumber.lineNumbersGutterWidth) - cx +"
                + " editor.scroll.scrollX;"));
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
