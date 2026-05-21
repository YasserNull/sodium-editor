package com.yn.sodiumeditor.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.yn.sodiumeditor.core.autocompletion.AutoCompletion;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;

/** Guards AutoCompletion word suggestions, update flow, and suggestion drawing. */
public class AutoCompletionGuardTest {

  @Test
  public void trie_shouldReturnFirstLexicographicCompletionAfterPrefix() {
    AutoCompletion.Trie trie = new AutoCompletion.Trie();
    trie.insert("download");
    trie.insert("document");
    trie.insert("done");

    assertEquals("document", trie.findFirstSuggestion("do"));
    assertEquals("download", trie.findFirstSuggestion("down"));
  }

  @Test
  public void trie_shouldNotSuggestExactWordOrMissingPrefix() {
    AutoCompletion.Trie trie = new AutoCompletion.Trie();
    trie.insert("class");
    trie.insert("clear");

    assertNull(trie.findFirstSuggestion("class"));
    assertNull(trie.findFirstSuggestion("clz"));
    assertNull(trie.findFirstSuggestion(""));
    assertNull(trie.findFirstSuggestion(null));
  }

  @Test
  public void updateSuggestionInternal_shouldPreservePathCompletionPriority() throws Exception {
    String src =
        readSource(
            "sodium-editor/src/main/java/com/yn/sodiumeditor/core/autocompletion/AutoCompletion.java");
    String body = methodBody(src, "public void updateSuggestionInternal()");

    assertTrue(
        "BUG: AutoCompletion must ask AutoPathCompletion first so path suggestions like /sdcard/D are not cleared by word completion.",
        body.indexOf("updatePathSuggestionFromAutoCompletion()")
            >= 0
            && body.indexOf("updatePathSuggestionFromAutoCompletion()")
                < body.indexOf("String line = editor.windowRender.getLineTextForRender"));
    assertTrue(
        "BUG: a handled path context must stop normal word completion.",
        body.contains("if (handledPathSuggestion)")
            && body.contains("return;"));
  }

  @Test
  public void updateSuggestionInternal_shouldStoreOnlyCompletionSuffix() throws Exception {
    String src =
        readSource(
            "sodium-editor/src/main/java/com/yn/sodiumeditor/core/autocompletion/AutoCompletion.java");
    String body = methodBody(src, "public void updateSuggestionInternal()");

    assertTrue(
        "BUG: activeSuggestion should store only the suffix drawn after the cursor.",
        body.contains("activeSuggestion = suggestion.substring(wordFragment.length())"));
    assertTrue(
        "BUG: AutoCompletion must remember where the current word started for drawing and tap hit testing.",
        body.contains("activeSuggestionCharStart = editor.cursor.cursorChar - wordFragment.length()"));
    assertTrue(
        "BUG: normal word suggestions must not be marked as path suggestions.",
        body.contains("activeSuggestionIsPath = false"));
  }

  @Test
  public void acceptAutoCompletion_shouldRejectPathSuggestions() throws Exception {
    String src =
        readSource(
            "sodium-editor/src/main/java/com/yn/sodiumeditor/core/autocompletion/AutoCompletion.java");
    String body = methodBody(src, "acceptAutoCompletion()");

    assertTrue(
        "BUG: acceptAutoCompletion must not consume path suggestions; AutoPathCompletion owns that.",
        body.contains("if (activeSuggestionIsPath)")
            && body.indexOf("if (activeSuggestionIsPath)") < body.indexOf("insertStringAtCursor"));
  }

  @Test
  public void drawAutoSuggestion_shouldUseSafeEditorMeasurementForCursorX() throws Exception {
    String src =
        readSource(
            "sodium-editor/src/main/java/com/yn/sodiumeditor/core/autocompletion/AutoCompletion.java");
    String body =
        methodBody(
            src,
            "drawAutoSuggestion(Canvas canvas, String lineContent, int globalLine, float textBaselineY)");

    assertTrue(
        "BUG: drawAutoSuggestion must not pass globalLine as Paint.measureText end index; that crashes with IndexOutOfBoundsException.",
        body.contains("editor.textRender.measureText(lineContent, cursorPositionInLine, globalLine)"));
    assertTrue(
        "BUG: drawAutoSuggestion should only use Paint.measureText for the suggestion text itself.",
        !body.contains("suggestionPaint.measureText(lineContent, cursorPositionInLine, globalLine)"));
  }

  @Test
  public void drawAutoSuggestionWrapped_shouldOnlyDrawInsideCurrentWrapSegment() throws Exception {
    String src =
        readSource(
            "sodium-editor/src/main/java/com/yn/sodiumeditor/core/autocompletion/AutoCompletion.java");
    String body =
        methodBody(
            src,
            "drawAutoSuggestionWrapped(Canvas canvas, String lineContent, int globalLine, int segStart, int segEnd, int visualIndex, float textBaselineY)");

    assertTrue(
        "BUG: wrapped suggestion drawing must skip segments that do not contain the cursor.",
        body.contains("cursorPositionInLine < segStart || cursorPositionInLine > segEnd"));
    assertTrue(
        "BUG: wrapped suggestion drawing must measure from segment start to cursor.",
        body.contains(
            "editor.textRender.measureTextWithVisualSpaces(lineContent, segStart, cursorPositionInLine, editor.textRender.paint)"));
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
