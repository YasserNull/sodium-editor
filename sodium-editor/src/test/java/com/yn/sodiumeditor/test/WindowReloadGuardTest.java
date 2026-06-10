package com.yn.sodiumeditor.test;

import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;

/**
 * Regression test for stale async window reload overwriting in-memory structural edits.
 *
 * <p>When a newline inserts an extra line, a previously-started async loadWindowAround() result
 * must not replace linesWindow with stale on-disk content while lineCountDelta != 0.
 *
 * <p>This is a source-level guard test so it remains stable in plain JVM environments.
 */
public class WindowReloadGuardTest {

  @Test
  public void fileWindowLoader_shouldSkipAsyncWindowApplyDuringStructuralEdits() throws Exception {
    Path fileWindowLoader = findFileWindowLoaderPath();
    String src = new String(Files.readAllBytes(fileWindowLoader), StandardCharsets.UTF_8);

    int applyAt = src.indexOf("editor.windowRender.linesWindow.clear();");
    assertTrue(
        "Expected FileWindowLoader to apply async window results into linesWindow.", applyAt >= 0);

    int from = Math.max(0, applyAt - 1200);
    int to = Math.min(src.length(), applyAt + 250);
    String around = src.substring(from, to);

    boolean hasStructuralEditGuard =
        around.contains("editor.editOperators.lineCountDelta != 0")
            || around.contains("lineCountDelta != 0")
            || around.contains("modifiedLines")
            || around.contains("hasPendingInMemoryEdits()");
    assertTrue(
        "BUG: FileWindowLoader may apply stale async window content over in-memory structural"
            + " edits.",
        hasStructuralEditGuard);

    boolean returnsBeforeApply =
        (around.contains("if (editor.editOperators.lineCountDelta != 0)")
                || around.contains("if (hasPendingInMemoryEdits())"))
            && around.contains("return;");
    assertTrue(
        "BUG: FileWindowLoader should return early before replacing linesWindow during structural"
            + " edits.",
        returnsBeforeApply);
  }

  private static Path findFileWindowLoaderPath() {
    Path cwd = new File(System.getProperty("user.dir", ".")).toPath().toAbsolutePath().normalize();
    for (int i = 0; i < 8; i++) {
      Path candidate =
          cwd.resolve("sodium-editor/src/main/java/com/yn/sodiumeditor/io/FileWindowLoader.java");
      if (Files.exists(candidate)) return candidate;
      Path parent = cwd.getParent();
      if (parent == null) break;
      cwd = parent;
    }
    Path candidate =
        new File(".")
            .toPath()
            .toAbsolutePath()
            .normalize()
            .resolve("src/main/java/com/yn/sodiumeditor/io/FileWindowLoader.java");
    if (Files.exists(candidate)) return candidate;
    throw new IllegalStateException(
        "Could not locate FileWindowLoader.java from test working directory.");
  }
}
