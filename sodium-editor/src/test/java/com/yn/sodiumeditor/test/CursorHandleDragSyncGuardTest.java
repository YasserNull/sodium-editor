package com.yn.sodiumeditor.test;

import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;

/** Regression guard for keeping the cursor handle synchronized with the caret while dragging. */
public class CursorHandleDragSyncGuardTest {

  @Test
  public void cursorHandleAndCaret_shouldUseSameAnimationSourceWhileDragging() throws Exception {
    String handleSrc =
        readSource("sodium-editor/src/main/java/com/yn/sodiumeditor/core/cursor/CursorHandle.java");
    int handleAt = handleSrc.indexOf("public void updateCursorHandlePosition()");
    assertTrue("Expected updateCursorHandlePosition in CursorHandle.", handleAt >= 0);
    String handleAround =
        handleSrc.substring(handleAt, Math.min(handleSrc.length(), handleAt + 1600));

    assertTrue(
        "BUG: CursorHandle should keep following cursorAnimation during dragging unless zoom/scale"
            + " makes animation state unsafe.",
        !handleAround.contains(
                "boolean draggingCursorHandle = editor.selectionHandles.draggingHandle == 3;")
            && handleAround.contains("editor.cursorAnimation.isCursorAnimationEnabled")
            && handleAround.contains("editor.cursorAnimation.cursorAnimValid")
            && handleAround.contains("docX = editor.cursorAnimation.cursorDrawX;")
            && handleAround.contains("docY = editor.cursorAnimation.cursorDrawY;"));

    String caretSrc =
        readSource("sodium-editor/src/main/java/com/yn/sodiumeditor/core/cursor/Caret.java");
    int caretAt = caretSrc.indexOf("public void drawCaret(Canvas canvas)");
    assertTrue("Expected drawCaret in Caret.", caretAt >= 0);
    String caretAround = caretSrc.substring(caretAt, Math.min(caretSrc.length(), caretAt + 1800));
    assertTrue(
        "BUG: Caret should use the same cursorAnimation draw coordinates as CursorHandle so both"
            + " stay visually synchronized.",
        caretAround.contains("editor.cursorAnimation.isCursorAnimationEnabled")
            && caretAround.contains("editor.cursorAnimation.cursorAnimValid")
            && caretAround.contains("cx = editor.cursorAnimation.cursorDrawX;")
            && caretAround.contains("cy = editor.cursorAnimation.cursorDrawY;"));

    String animSrc =
        readSource(
            "sodium-editor/src/main/java/com/yn/sodiumeditor/renderer/animation/CursorAnimation.java");
    int animAt = animSrc.indexOf("if (targetChanged)");
    assertTrue("Expected redirect logic in CursorAnimation.", animAt >= 0);
    String animAround = animSrc.substring(animAt, Math.min(animSrc.length(), animAt + 1800));
    assertTrue(
        "BUG: fast cursor redirect must be limited to active cursor-handle dragging, not every"
            + " normal tap move.",
        animAround.contains(
                "boolean fastDragAnimationActive = editor.selectionHandles.draggingHandle == 3;")
            && animAround.contains("cursorAnimActiveDurationMs =")
            && animAround.contains("fastDragAnimationActive")
            && animAround.contains("cursorAnimFastRedirectMaxDurationMs")
            && animAround.contains("cursorAnimFastRedirectMinDurationMs")
            && animAround.contains("cursorAnimDurationMs;"));
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
