package com.yn.sodiumeditor.test;

import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;

/**
 * Regression guard: deleting/replacing a whole file must not leave stale binary document row counts.
 *
 * <p>When a file is auto-detected as binary, {@code ViewMetrics.getLinesCount()} and
 * {@code GutterUtils.estimateLineCountForGutter()} return {@code BinaryDocument.getRowCount()}
 * unconditionally. The entire-file select-all delete/replace fast path clears the render window but
 * never resets the binary document, so after the content is removed (and saved) the editor keeps
 * reporting the old row count and shows thousands of empty rows.
 */
public class SelectAllDeleteBinaryStaleLineCountGuardTest {

  @Test
  public void entireFileReplaceFastPath_mustResetBinaryState() throws Exception {
    String src = normalized("sodium-editor/src/main/java/com/yn/sodiumeditor/core/selection/SelectionActionHandler.java");
    String body = methodBody(src, "void replaceEntireFileSelectionFast(String insertText)");

    boolean resetsBinary =
        body.contains("resetBinaryStateForEntireFileReplace()")
            || body.contains("applyBinaryFileFeaturePolicy(false)")
            || body.contains("clearBinaryDocument()");
    assertTrue(
        "BUG: replacing the entire file must reset stale binary state, otherwise getLinesCount()"
            + " keeps returning the old binaryDocument.getRowCount() and blank rows remain.",
        resetsBinary);
  }

  @Test
  public void selectAllReplaceFallback_mustResetBinaryState() throws Exception {
    String src = normalized("sodium-editor/src/main/java/com/yn/sodiumeditor/core/selection/SelectionActionHandler.java");
    String body = methodBody(src, "void handleSelectAllReplace(");

    boolean resetsBinary =
        body.contains("resetBinaryStateForEntireFileReplace()")
            || body.contains("applyBinaryFileFeaturePolicy(false)")
            || body.contains("clearBinaryDocument()");
    assertTrue(
        "BUG: the select-all replace fallback must reset stale binary state too.", resetsBinary);
  }

  @Test
  public void binaryResetHelper_mustClearDocumentAndFeaturePolicy() throws Exception {
    String src = normalized("sodium-editor/src/main/java/com/yn/sodiumeditor/core/selection/SelectionActionHandler.java");
    String body = methodBody(src, "void resetBinaryStateForEntireFileReplace()");

    assertTrue(
        "BUG: reset helper must clear the binary document (directly or via the feature policy).",
        body.contains("applyBinaryFileFeaturePolicy(false)") || body.contains("clearBinaryDocument()"));
    assertTrue(
        "BUG: reset helper must also consider a lingering document when the policy is already off.",
        body.contains("binaryDocument != null"));
  }

  @Test
  public void binaryLineCountConsumers_guardOnDocumentNotNull() throws Exception {
    String metrics = normalized("sodium-editor/src/main/java/com/yn/sodiumeditor/core/view/ViewMetrics.java");
    String gutter = normalized("sodium-editor/src/main/java/com/yn/sodiumeditor/utils/GutterUtils.java");

    assertTrue(
        "BUG: getLinesCount binary branch must require a non-null document.",
        metrics.contains(
            "binaryFileFeaturePolicyActive && view.editor.binaryRender.binaryDocument != null"));
    assertTrue(
        "BUG: gutter estimate binary branch must require a non-null document.",
        gutter.contains(
            "binaryFileFeaturePolicyActive && editor.binaryRender.binaryDocument != null"));
  }

  private static String normalized(String rel) throws Exception {
    return readSource(rel).replaceAll("\\s+", " ");
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
