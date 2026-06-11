package com.yn.sodiumeditor.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.yn.sodiumeditor.core.autosuggestion.AutoPathSuggestion;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import org.junit.Test;

/** Guards path suggestion integration with normal auto-suggestion updates and taps. */
public class AutoPathSuggestionGuardTest {

  @Test
  public void autoSuggestionUpdate_shouldTryPathSuggestionBeforeWordSuggestion() throws Exception {
    String src =
        readSource(
            "sodium-editor/src/main/java/com/yn/sodiumeditor/core/autosuggestion/AutoSuggestion.java");
    String body = methodBody(src, "public void updateSuggestionInternal()");

    assertTrue(
        "BUG: typing should route through AutoPathSuggestion before word suggestion can clear path"
            + " suggestions.",
        body.indexOf("updatePathSuggestionFromAutoSuggestion()") >= 0
            && body.indexOf("updatePathSuggestionFromAutoSuggestion()")
                < body.indexOf("String line = editor.windowRender.getLineTextForRender"));
    assertTrue(
        "BUG: path suggestion must stop word suggestion when the cursor context is a path.",
        body.contains("if (handledPathSuggestion)") && body.contains("return;"));
  }

  @Test
  public void drawAutoSuggestion_shouldMeasureCursorXWithTextRender() throws Exception {
    String src =
        readSource(
            "sodium-editor/src/main/java/com/yn/sodiumeditor/core/autosuggestion/AutoSuggestion.java");
    String body =
        methodBody(
            src,
            "drawAutoSuggestion(Canvas canvas, String lineContent, int globalLine, float"
                + " textBaselineY)");

    assertTrue(
        "BUG: drawAutoSuggestion must not pass globalLine as Paint.measureText end index; that"
            + " crashes with IndexOutOfBoundsException.",
        body.contains(
            "editor.textRender.measureText(lineContent, cursorPositionInLine, globalLine)"));
    assertTrue(
        "BUG: drawAutoSuggestion should only use Paint.measureText for the suggestion text itself.",
        !body.contains(
            "suggestionPaint.measureText(lineContent, cursorPositionInLine, globalLine)"));
  }

  @Test
  public void pathSuggestionUpdate_shouldReportWhetherItHandledAPathContext() throws Exception {
    String src =
        readSource(
            "sodium-editor/src/main/java/com/yn/sodiumeditor/core/autosuggestion/AutoPathSuggestion.java");
    String entry = methodBody(src, "updatePathSuggestionFromAutoSuggestion()");
    String body = methodBody(src, "updatePathSuggestionInternal(boolean clearNonPathSuggestion)");

    assertTrue(
        "BUG: shared auto-suggestion path must call AutoPathSuggestion without clearing normal word"
            + " suggestions in non-path contexts.",
        entry.contains("updatePathSuggestionInternal(false)"));
    assertTrue(
        "BUG: non-path cursor contexts must return false so word suggestion can still run.",
        body.contains("if (pathFragment.isEmpty())") && body.contains("return false;"));
    assertTrue(
        "BUG: path cursor contexts must return true after resolving a path suggestion.",
        body.contains("String suggestion = findPathSuggestion(pathFragment)")
            && body.lastIndexOf("return true;")
                > body.indexOf("String suggestion = findPathSuggestion(pathFragment)"));
  }

  @Test
  public void pathSuggestionUpdate_shouldAllowPathsInsideSyntaxSpans() throws Exception {
    String src =
        readSource(
            "sodium-editor/src/main/java/com/yn/sodiumeditor/core/autosuggestion/AutoPathSuggestion.java");
    String body = methodBody(src, "updatePathSuggestionInternal(boolean clearNonPathSuggestion)");
    int pathContext = body.indexOf("String pathFragment = getCurrentPathFragment()");
    int suggestion = body.indexOf("String suggestion = findPathSuggestion(pathFragment)");

    assertTrue(
        "Expected path fragment and suggestion lookup in path suggestion.",
        pathContext >= 0 && suggestion > pathContext);
    assertTrue(
        "BUG: path suggestion must work inside string/comment syntax spans; only word suggestion"
            + " should be blocked there.",
        !body.substring(pathContext, suggestion).contains("calculateSpansForLine")
            && !body.substring(pathContext, suggestion).contains("HighlightSpan span"));
  }

  @Test
  public void pathSuggestion_shouldFallbackForAndroidPublicStorageRoot() throws Exception {
    String src =
        readSource(
            "sodium-editor/src/main/java/com/yn/sodiumeditor/core/autosuggestion/AutoPathSuggestion.java");
    String body = methodBody(src, "public String findPathSuggestion(String fragment)");

    assertTrue(
        "BUG: /storage/emulated/0 may return null or empty entries under scoped storage; path"
            + " suggestion needs an Android public-directory fallback before returning null.",
        body.contains("getAndroidPublicDirectoryFallbackNames")
            && body.indexOf("getAndroidPublicDirectoryFallbackNames")
                < body.indexOf("entries == null || entries.length == 0"));
    assertTrue(
        "BUG: path suggestion should match Download for /storage/emulated/0/Do and also tolerate"
            + " /do.",
        src.contains("startsWithIgnoreCase"));
  }

  @Test
  public void pathSuggestionFallback_shouldCompleteAndroidDownloadIgnoringCase() {
    AutoPathSuggestion suggestion = new AutoPathSuggestion(null);

    assertEquals(
        "/storage/emulated/0/Download/",
        suggestion.buildSuggestionFromNames(
            "/storage/emulated/0/Do", "Do", Arrays.asList("Documents/", "Download/")));
    assertEquals(
        "/storage/emulated/0/download/",
        suggestion.buildSuggestionFromNames(
            "/storage/emulated/0/do", "do", Arrays.asList("Documents/", "Download/")));
  }

  @Test
  public void drawTextContent_shouldRenderAutoSuggestionAfterHighlightedLine() throws Exception {
    String src =
        readSource("sodium-editor/src/main/java/com/yn/sodiumeditor/renderer/ViewRender.java");
    String body = methodBody(src, "void drawTextContent(Canvas canvas, int firstVisibleIndex");
    int highlightCall = body.indexOf("editor.textRender.drawHighlightedLine(canvas, line, i, y)");
    int suggestionCall =
        body.indexOf("editor.autoSuggestion.drawAutoSuggestion(canvas, line, i, y)");

    assertTrue(
        "BUG: drawAutoSuggestion must be called after drawHighlightedLine in drawTextContent.",
        highlightCall >= 0 && suggestionCall > highlightCall);
  }

  @Test
  public void touchSuggestionTap_shouldAcceptPathSuggestionWithPathHandler() throws Exception {
    String src =
        readSource("sodium-editor/src/main/java/com/yn/sodiumeditor/input/events/OnTouch.java");
    String body = methodBody(src, "handleSuggestionTap(float ex, float ey)");

    assertTrue(
        "BUG: tapping a path suggestion must call acceptPathSuggestion; acceptAutoSuggestion"
            + " rejects path suggestions.",
        body.contains("if (editor.autoSuggestion.activeSuggestionIsPath)")
            && body.contains("editor.autoPathSuggestion.acceptPathSuggestion()")
            && body.contains("editor.autoSuggestion.acceptAutoSuggestion()"));
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
