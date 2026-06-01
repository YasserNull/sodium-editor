package com.yn.sodiumeditor.test;

import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;

public class StringHighlightRuleRegressionTest {

  @Test
  public void stringHighlightApi_shouldRegisterDelimiterMultiLineColorAndStyle() throws Exception {
    String highlite =
        readSource("sodium-editor/src/main/java/com/yn/sodiumeditor/core/highlight/Highlite.java");
    String parser =
        readSource("sodium-editor/src/main/java/com/yn/sodiumeditor/core/highlight/HighlightParser.java");
    String editor = readSource("sodium-editor/src/main/java/com/yn/sodiumeditor/SodiumEditor.java");

    assertTrue(highlite.contains("setStringsHighlite(String delimiter, boolean multiLine, int color, int style)"));
    assertTrue(highlite.contains("public final LinkedHashMap<String, StringHighlightConfig> stringHighlightConfigs"));
    assertTrue(highlite.contains("public final boolean multiLine;"));
    assertTrue(highlite.contains("HighliteRender.HighlightRuleType.STRING"));
    assertTrue(parser.contains("configuredString.multiLine ? configuredString.state : 0"));
    assertTrue(parser.contains("findStringEndForConfig"));
    assertTrue(editor.contains("setStringsHighlite(String delimiter, boolean multiLine, int color, int style)"));
  }

  @Test
  public void shellFiles_shouldHighlightSingleAndDoubleQuotedStringsGreen() throws Exception {
    String app = readSource("app/src/main/java/com/yn/sodiumeditordemo/MainActivity.java");

    assertTrue(app.contains("editor.setStringsHighlite(\"\\\"\", true, 0xFF00FF00, FontStyle.STYLE_NORMAL)"));
    assertTrue(app.contains("editor.setStringsHighlite(\"'\", true, 0xFF00FF00, FontStyle.STYLE_NORMAL)"));
  }

  @Test
  public void visibleRangeCache_shouldPropagateMultilineStringStateAcrossLines() throws Exception {
    String cache =
        readSource("sodium-editor/src/main/java/com/yn/sodiumeditor/renderer/HighlightCacheManager.java");
    String body = methodBody(cache, "ensureHighlightCacheForVisibleRange");

    assertTrue(
        "BUG: visible range parsing must start from the real state before the first visible line.",
        body.contains("HighliteRender.HighlightLineState rangeStartState = getLineStateAtStart(startLine)")
            && body.contains("int strState = rangeStartState.stringState"));
    assertTrue(
        "BUG: cached lines must not be skipped before updating multiline string state.",
        !body.contains("if (cached != null && !needRegex) continue"));
    assertTrue(
        "BUG: each line must parse with the propagated previous-line string state.",
        body.contains("parseLineForSyntax(line, inBlock, strState, sRule, bRule, true)")
            && body.contains("strState = res.endsInStringState"));
  }

  @Test
  public void customStringMultiline_shouldNotBeDisabledByLegacyGlobalFlag() throws Exception {
    String parser =
        readSource("sodium-editor/src/main/java/com/yn/sodiumeditor/core/highlight/HighlightParser.java");
    String body = methodBody(parser, "parseLineForSyntax");

    assertTrue(
        "BUG: custom setStringsHighlite multiLine=true must be checked before legacy global flags clear strState.",
        body.indexOf("Highlite.StringHighlightConfig activeStateConfig = highlite.getStringHighlightConfigForState(strState)")
            < body.indexOf("!highlite.isMultiLineStringsEnabled"));
    assertTrue(
        "BUG: legacy multiline/backtick/triple flags must only clear states that do not belong to setStringsHighlite.",
        body.contains("activeStateConfig == null\n                && strState != 0")
            && body.contains("activeStateConfig == null\n                && strState == com.yn.sodiumeditor.core.highlight.Highlite.STRING_STATE_BACKTICK")
            && body.contains("activeStateConfig == null\n                && strState == com.yn.sodiumeditor.core.highlight.Highlite.STRING_STATE_TRIPLE"));
    assertTrue(
        "BUG: setStringsHighlite multiLine=false should still stop cross-line string state.",
        body.contains("if (activeStateConfig != null && !activeStateConfig.multiLine) strState = 0;"));
  }

  @Test
  public void invalidatingLine_shouldDropFollowingHighlightStateForMultilineStrings() throws Exception {
    String highlite =
        readSource("sodium-editor/src/main/java/com/yn/sodiumeditor/core/highlight/Highlite.java");
    String invalidateBody = methodBody(highlite, "invalidateHighlightCacheForLine");
    String helperBody = methodBody(highlite, "removeCachedHighlightStateFromLine");

    assertTrue(
        "BUG: editing an opening quote line must invalidate following cached lines too.",
        invalidateBody.contains("removeCachedHighlightStateFromLine(line)"));
    assertTrue(
        "BUG: multiline string/comment state caches after the edited line must be removed.",
        helperBody.contains("cache.highlightCache.keySet().iterator()")
            && helperBody.contains("cache.blockCommentEndStateCache.keySet().iterator()")
            && helperBody.contains("cache.stringEndStateCache.keySet().iterator()")
            && helperBody.contains(">= line"));
  }

  @Test
  public void loadingFileWindow_shouldClearStaleHighlightCacheBeforeFirstDraw() throws Exception {
    String loader = readSource("sodium-editor/src/main/java/com/yn/sodiumeditor/io/FileWindowLoader.java");
    String body = methodBody(loader, "private void loadWindowInternal");

    assertTrue(
        "BUG: opening a file can reuse highlight spans cached for the previous/empty window.",
        body.contains("editor.highlite.clearHighlightCaches()"));
    assertTrue(
        "BUG: highlight cache must be cleared after replacing linesWindow content.",
        body.indexOf("editor.windowRender.linesWindow.clear()")
            < body.indexOf("editor.highlite.clearHighlightCaches()"));
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
}
