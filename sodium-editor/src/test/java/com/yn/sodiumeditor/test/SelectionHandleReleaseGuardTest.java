package com.yn.sodiumeditor.test;

import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;

/** Regression guard for preserving selection after dragging selection handles. */
public class SelectionHandleReleaseGuardTest {

  @Test
  public void actionUp_afterDraggingSelectionHandle_shouldNotFallThroughToTapSelectionClear()
      throws Exception {
    String src =
        readSource("sodium-editor/src/main/java/com/yn/sodiumeditor/input/events/OnTouch.java");
    int at = src.indexOf("case MotionEvent.ACTION_UP:");
    assertTrue("Expected ACTION_UP branch in OnTouch.", at >= 0);
    String around = src.substring(at, Math.min(src.length(), at + 2600));

    assertTrue(
        "BUG: ACTION_UP should snapshot whether a selection handle drag was active before"
            + " handleActionUpOrCancel resets draggingHandle.",
        around.contains("boolean wasDraggingSelectionHandle")
            || around.contains("boolean draggedSelectionHandle")
            || around.contains("boolean hadDraggingSelectionHandle"));

    assertTrue(
        "BUG: when releasing a dragged selection handle, ACTION_UP should bypass gestureDetector"
            + " tap processing so selection is not cleared as a normal tap.",
        around.contains("if (wasDraggingSelectionHandle)")
            || around.contains("if (draggedSelectionHandle)")
            || around.contains("if (hadDraggingSelectionHandle)"));
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
