package com.yn.sodiumeditor.test;

import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;

/** Guards long-press drag selection from extending into empty space below loaded file lines. */
public class LongPressSelectionEmptyAreaGuardTest {

  @Test
  public void longPressDrag_shouldClampSelectionTargetToLastRealLine() throws Exception {
    String src =
        readSource("sodium-editor/src/main/java/com/yn/sodiumeditor/input/events/DragSelectionHandler.java");
    String body = methodBody(src, "public boolean handleActionMove(MotionEvent event)");

    assertTrue(
        "BUG: long-press drag selection must clamp targets below EOF/loaded lines before updating selection.",
        body.contains("line = clampDragLineToRealContent(line)")
            && body.indexOf("line = clampDragLineToRealContent(line)")
                < body.indexOf("editor.selection.updateLongPressSelection(line, clamped)"));
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
