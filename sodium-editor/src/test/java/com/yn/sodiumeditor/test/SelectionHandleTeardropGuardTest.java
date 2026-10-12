package com.yn.sodiumeditor.test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;

/**
 * Regression guard ensuring selection/cursor handles are drawn as teardrops and rendered above the
 * line-number gutter instead of being cut off by the text-area clip.
 */
public class SelectionHandleTeardropGuardTest {

  @Test
  public void selectionHandles_shouldBeDrawnAsTeardropNotCircle() throws Exception {
    String src =
        readSource("sodium-editor/src/main/java/com/yn/sodiumeditor/core/selection/SelectionHandles.java");

    int drawAt = src.indexOf("void drawHandles(");
    int nextMethodAt = src.indexOf("hitTestLeft", drawAt);
    assertTrue("Expected drawHandles method in SelectionHandles.", drawAt >= 0 && nextMethodAt > drawAt);
    String body = src.substring(drawAt, nextMethodAt);

    assertTrue(
        "selection handles must render a teardrop shape (path based)",
        body.contains("TeardropPathBuilder.build") || body.contains("drawTeardropHandle"));
    assertFalse(
        "selection handles must not be drawn as plain circles",
        body.contains("drawCircle("));
  }

  @Test
  public void cursorHandle_shouldBeDrawnAsTeardropNotRoundRect() throws Exception {
    String src =
        readSource("sodium-editor/src/main/java/com/yn/sodiumeditor/core/cursor/CursorHandle.java");

    int drawAt = src.indexOf("void drawCursorHandle(");
    int nextMethodAt = src.indexOf("hitTest", drawAt);
    assertTrue("Expected drawCursorHandle method.", drawAt >= 0 && nextMethodAt > drawAt);
    String body = src.substring(drawAt, nextMethodAt);

    assertTrue(
        "cursor handle must render a teardrop shape (path based)",
        body.contains("TeardropPathBuilder.build") || body.contains("drawTeardropHandle"));
    assertFalse(
        "cursor handle must not be drawn as a rounded rectangle or circle",
        body.contains("drawRoundRect(") || body.contains("drawCircle("));
  }

  @Test
  public void handles_shouldBeDrawnInsideTextAreaClip_soTheyRenderBelowLineNumbers()
      throws Exception {
    String src =
        readSource("sodium-editor/src/main/java/com/yn/sodiumeditor/renderer/ViewRender.java");

    int caretAt = src.indexOf("editor.caret.drawCaret(canvas);");
    assertTrue("Expected caret drawing in ViewRender.", caretAt >= 0);

    int restoreAfterCaret = src.indexOf("canvas.restore();", caretAt);
    assertTrue("Expected a restore after the caret draw.", restoreAfterCaret > caretAt);

    int selectionHandlesAt = src.indexOf("editor.selectionHandles.drawHandles(canvas);");
    int cursorHandleAt = src.indexOf("editor.cursorHandle.drawCursorHandle(canvas);");
    assertTrue("Expected selection handle drawing.", selectionHandlesAt >= 0);
    assertTrue("Expected cursor handle drawing.", cursorHandleAt >= 0);

    assertTrue(
        "Handles must stay inside the text-area clip so they render below the line-number"
            + " gutter instead of covering the line numbers.",
        selectionHandlesAt < restoreAfterCaret);
    assertTrue(
        "Cursor handle must stay inside the text-area clip so it renders below the line-number"
            + " gutter instead of covering the line numbers.",
        cursorHandleAt < restoreAfterCaret);
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
