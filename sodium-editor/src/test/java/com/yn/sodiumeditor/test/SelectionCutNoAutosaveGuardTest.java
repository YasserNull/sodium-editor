package com.yn.sodiumeditor.test;

import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;

/** Guards cut/delete selection so it stays in memory until explicit save. */
public class SelectionCutNoAutosaveGuardTest {

  @Test
  public void replaceSelectionWithText_shouldNotRewriteSourceFileAutomatically() throws Exception {
    String src =
        readSource(
            "sodium-editor/src/main/java/com/yn/sodiumeditor/core/selection/SelectionActionHandler.java");
    String body = methodBody(src, "replaceSelectionWithText(String insertText)");

    assertTrue(
        "BUG: cut/delete selection must not save to sourceFile automatically; saving belongs to"
            + " explicit save.",
        !body.contains("rewriteReplaceRangeAsync("));
    assertTrue(
        "BUG: multi-line selection replacement should update the in-memory window when available.",
        body.contains("applyMultiLineReplaceInWindowNow(sL, sC, eL, eC, insertText, target)"));
  }

  @Test
  public void replaceSelectionWithText_shouldInvalidateFoldStateAfterCut() throws Exception {
    String src =
        readSource(
            "sodium-editor/src/main/java/com/yn/sodiumeditor/core/selection/SelectionActionHandler.java");
    String body = methodBody(src, "invalidateFeatureStateForReplace(int startLine, int endLine)");

    assertTrue(
        "BUG: cutting lines must invalidate bracket/highlight dependent caches.",
        body.contains("editor.highlight.invalidateHighlightEnsureRange()")
            && body.contains("editor.bracketGuides.invalidateBracketGuideCache(true)"));
  }

  @Test
  public void replaceSelectionWithText_shouldClearSelectionThroughFacade() throws Exception {
    String src =
        readSource(
            "sodium-editor/src/main/java/com/yn/sodiumeditor/core/selection/SelectionActionHandler.java");
    String body = methodBody(src, "replaceSelectionWithText(String insertText)");

    assertTrue(
        "BUG: selection replacement must clear through Selection so facade hasSelection stays"
            + " synced with state.",
        body.contains("selection.clearSelectionStateAfterDelete()")
            && !body.contains("selection.state.clearSelectionStateAfterDelete()"));
  }

  @Test
  public void windowReload_shouldNotOverwritePendingInMemoryEdits() throws Exception {
    String src =
        readSource("sodium-editor/src/main/java/com/yn/sodiumeditor/io/FileWindowLoader.java");
    String body = methodBody(src, "private boolean hasPendingInMemoryEdits()");

    assertTrue(
        "BUG: pending modifiedLines must prevent disk reload from bringing cut text back.",
        body.contains("editor.windowRender.hasAnyModifiedLines()"));
    assertTrue(
        "BUG: pending edit history must prevent disk reload before explicit save.",
        body.contains("!editor.editOperators.history.pendingEdits.isEmpty()"));
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

  private static String readSource(String rel) throws Exception {
    return new String(Files.readAllBytes(findPath(rel)), StandardCharsets.UTF_8);
  }

  private static Path findPath(String rel) {
    Path cwd = new File(System.getProperty("user.dir", ".")).toPath().toAbsolutePath().normalize();
    for (int i = 0; i < 8; i++) {
      Path candidate = cwd.resolve(rel);
      if (Files.exists(candidate)) return candidate;
      Path parent = cwd.getParent();
      if (parent == null) break;
      cwd = parent;
    }
    throw new IllegalStateException("Could not locate file: " + rel);
  }
}
