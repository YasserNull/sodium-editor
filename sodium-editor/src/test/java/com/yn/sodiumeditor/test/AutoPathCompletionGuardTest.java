package com.yn.sodiumeditor.test;

import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;

/** Guards path completion integration with normal auto-completion updates and taps. */
public class AutoPathCompletionGuardTest {

  @Test
  public void autoCompletionUpdate_shouldTryPathCompletionBeforeWordCompletion() throws Exception {
    String src =
        readSource(
            "sodium-editor/src/main/java/com/yn/sodiumeditor/core/autocompletion/AutoCompletion.java");
    String body = methodBody(src, "public void updateSuggestionInternal()");

    assertTrue(
        "BUG: typing should route through AutoPathCompletion before word completion can clear path suggestions.",
        body.indexOf("updatePathSuggestionFromAutoCompletion()")
            >= 0
            && body.indexOf("updatePathSuggestionFromAutoCompletion()")
                < body.indexOf("String line = editor.windowRender.getLineTextForRender"));
    assertTrue(
        "BUG: path completion must stop word completion when the cursor context is a path.",
        body.contains("if (handledPathSuggestion)")
            && body.contains("return;"));
  }

  @Test
  public void drawAutoSuggestion_shouldMeasureCursorXWithTextRender() throws Exception {
    String src =
        readSource(
            "sodium-editor/src/main/java/com/yn/sodiumeditor/core/autocompletion/AutoCompletion.java");
    String body = methodBody(src, "drawAutoSuggestion(Canvas canvas, String lineContent, int globalLine, float textBaselineY)");

    assertTrue(
        "BUG: drawAutoSuggestion must not pass globalLine as Paint.measureText end index; that crashes with IndexOutOfBoundsException.",
        body.contains("editor.textRender.measureText(lineContent, cursorPositionInLine, globalLine)"));
    assertTrue(
        "BUG: drawAutoSuggestion should only use Paint.measureText for the suggestion text itself.",
        !body.contains("suggestionPaint.measureText(lineContent, cursorPositionInLine, globalLine)"));
  }

  @Test
  public void pathCompletionUpdate_shouldReportWhetherItHandledAPathContext() throws Exception {
    String src =
        readSource(
            "sodium-editor/src/main/java/com/yn/sodiumeditor/core/autocompletion/AutoPathCompletion.java");
    String entry = methodBody(src, "updatePathSuggestionFromAutoCompletion()");
    String body = methodBody(src, "updatePathSuggestionInternal(boolean clearNonPathSuggestion)");

    assertTrue(
        "BUG: shared auto-completion path must call AutoPathCompletion without clearing normal word suggestions in non-path contexts.",
        entry.contains("updatePathSuggestionInternal(false)"));
    assertTrue(
        "BUG: non-path cursor contexts must return false so word completion can still run.",
        body.contains("if (pathFragment.isEmpty())")
            && body.contains("return false;"));
    assertTrue(
        "BUG: path cursor contexts must return true after resolving a path suggestion.",
        body.contains("String suggestion = findPathSuggestion(pathFragment)")
            && body.lastIndexOf("return true;") > body.indexOf("String suggestion = findPathSuggestion(pathFragment)"));
  }

  @Test
  public void touchSuggestionTap_shouldAcceptPathCompletionWithPathHandler() throws Exception {
    String src =
        readSource("sodium-editor/src/main/java/com/yn/sodiumeditor/input/events/OnTouch.java");
    String body = methodBody(src, "handleSuggestionTap(float ex, float ey)");

    assertTrue(
        "BUG: tapping a path suggestion must call acceptPathCompletion; acceptAutoCompletion rejects path suggestions.",
        body.contains("if (editor.autoCompletion.activeSuggestionIsPath)")
            && body.contains("editor.autoPathCompletion.acceptPathCompletion()")
            && body.contains("editor.autoCompletion.acceptAutoCompletion()"));
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
