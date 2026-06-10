package com.yn.sodiumeditor.test;

import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;

/** Regression guard for stale cursor handle position during repeated deletes. */
public class CursorHandleInvalidateGuardTest {

  @Test
  public void invalidateCursorArea_shouldInvalidateOldAndNewHandleRects() throws Exception {
    String src =
        readSource("sodium-editor/src/main/java/com/yn/sodiumeditor/core/cursor/Cursor.java");
    int at = src.indexOf("public void invalidateCursorArea()");
    assertTrue("Expected invalidateCursorArea in Cursor.", at >= 0);
    String around = src.substring(at, Math.min(src.length(), at + 3200));

    assertTrue(
        "BUG: invalidateCursorArea should snapshot old cursor handle rect before updating target"
            + " position.",
        around.contains("RectF oldHandleRect = new RectF(editor.cursorHandle.cursorHandleRect);"));
    assertTrue(
        "BUG: invalidateCursorArea should update cursor handle position before computing dirty"
            + " region.",
        around.contains("editor.cursorHandle.updateCursorHandlePosition();"));
    assertTrue(
        "BUG: invalidateCursorArea should union old handle rect into dirty area.",
        around.contains("dirty.union(\n            (int) Math.floor(oldHandleRect.left)"));
    assertTrue(
        "BUG: invalidateCursorArea should union new handle rect into dirty area.",
        around.contains("RectF newHandleRect = editor.cursorHandle.cursorHandleRect;")
            && around.contains("dirty.union(\n            (int) Math.floor(newHandleRect.left)"));
    assertTrue(
        "BUG: invalidateCursorArea should invalidate the combined dirty rect.",
        around.contains("editor.invalidate(dirty);"));
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
