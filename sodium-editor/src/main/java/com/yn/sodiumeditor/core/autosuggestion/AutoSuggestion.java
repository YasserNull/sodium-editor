package com.yn.sodiumeditor.core.autosuggestion;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.Looper;
import android.os.SystemClock;
import com.yn.sodiumeditor.SodiumEditor;
import java.util.ArrayList;
import java.util.List;

/**
 * Auto-suggestion functionality for SodiumEditor. Handles word-based suggestions using a Trie data
 * structure.
 */
public class AutoSuggestion {

  private final SodiumEditor editor;

  // Auto-suggestion state
  public boolean isAutoSuggestionEnabled = true;
  public final Trie suggestionTrie = new Trie();
  private final ArrayList<String> suggestionList = new ArrayList<>();

  // Active suggestion state
  public String activeSuggestion = null;
  public int activeSuggestionLine;
  public int activeSuggestionCharStart;
  public String activeSuggestionWordFragment = "";
  public boolean activeSuggestionIsPath = false;
  public final RectF activeSuggestionRect = new RectF();

  // Suggestion paint
  public final Paint suggestionPaint = new Paint();
  public int suggestionColor = 0xFFAAAAAA;

  // Suggestion text size
  public boolean isSuggestionTextSizeCustom = false;
  public float suggestionTextSizeScale = 1f;

  // Debounce constants
  public static final long SUGGESTION_UPDATE_DEBOUNCE_MS = 40L;
  public long lastSuggestionUpdateUptime = 0L;
  public boolean suggestionUpdateScheduled = false;
  public final Runnable suggestionUpdateRunnable =
      () -> {
        suggestionUpdateScheduled = false;
        lastSuggestionUpdateUptime = SystemClock.uptimeMillis();
        updateSuggestionInternal();
      };

  // Touch flag
  public boolean suggestionAcceptedThisTouch = false;

  public AutoSuggestion(SodiumEditor editor) {
    this.editor = editor;
    initSuggestionPaint();
  }

  private void initSuggestionPaint() {
    suggestionPaint.set(editor.textRender.paint);
    suggestionPaint.setColor(suggestionColor);
    suggestionPaint.setAntiAlias(true);
    suggestionPaint.setSubpixelText(true);
    suggestionPaint.setHinting(Paint.HINTING_ON);
  }

  /** Set auto-suggestion enabled state. */
  public void setAutoSuggestionEnabled(boolean enabled) {
    this.isAutoSuggestionEnabled = enabled;
    if (!enabled && !activeSuggestionIsPath) {
      clearActiveSuggestion();
    }
    editor.invalidate();
  }

  /** Get auto-suggestion enabled state. */
  public boolean isAutoSuggestionEnabled() {
    return isAutoSuggestionEnabled;
  }

  /** Set suggestions from a list of keywords. */
  public void setSuggestions(List<String> keywords) {
    suggestionList.clear();
    suggestionTrie.clear();
    if (keywords != null) {
      for (String word : keywords) {
        if (word == null || word.isEmpty()) continue;
        suggestionList.add(word);
        suggestionTrie.insert(word);
      }
    }
    clearActiveSuggestion();
    editor.invalidate();
  }

  public List<String> getSuggestionList() {
    return new ArrayList<>(suggestionList);
  }

  public void removeSuggestion(String keyword) {
    if (keyword == null || keyword.isEmpty()) return;
    if (!suggestionList.remove(keyword)) return;
    rebuildSuggestionTrie();
    clearActiveSuggestion();
    editor.invalidate();
  }

  public void setSuggestionColor(int color) {
    if (suggestionColor == color) return;
    suggestionColor = color;
    suggestionPaint.setColor(color);
    editor.invalidate();
  }

  public int getSuggestionColor() {
    return suggestionColor;
  }

  private void rebuildSuggestionTrie() {
    suggestionTrie.clear();
    for (String word : suggestionList) {
      suggestionTrie.insert(word);
    }
  }

  /** Accept the current auto-suggestion suggestion. */
  public void acceptAutoSuggestion() {
    if (activeSuggestion == null) {
      return;
    }
    if (activeSuggestionIsPath) {
      return;
    }
    if (!isAutoSuggestionEnabled) {
      return;
    }

    editor.ime.commitComposing(false);
    suggestionAcceptedThisTouch = true;

    String textToInsert = activeSuggestion;
    clearActiveSuggestion();
    editor.selection.hasSelection = false;
    editor.selection.isSelectAllActive = false;
    editor.selection.isEntireFileSelected = false;
    editor.editOperators.insertStringAtCursor(textToInsert);

    editor.view.restartInput();
  }

  /** Clear the active suggestion. */
  public void clearActiveSuggestion() {
    if (activeSuggestion != null) {
      activeSuggestion = null;
      activeSuggestionRect.setEmpty();
      activeSuggestionIsPath = false;
      editor.invalidate();
    }
  }

  /** Update suggestion based on current cursor position. */
  public void updateSuggestion() {
    if (Looper.myLooper() != Looper.getMainLooper()) {
      editor.post(this::updateSuggestion);
      return;
    }
    editor.ime.updateImeSelection();
    long now = SystemClock.uptimeMillis();
    if (now - lastSuggestionUpdateUptime < SUGGESTION_UPDATE_DEBOUNCE_MS) {
      if (!suggestionUpdateScheduled) {
        suggestionUpdateScheduled = true;
        editor.caret.mainHandler.postDelayed(
            suggestionUpdateRunnable, SUGGESTION_UPDATE_DEBOUNCE_MS);
      }
      return;
    }
    lastSuggestionUpdateUptime = now;
    updateSuggestionInternal();
  }

  /** Internal suggestion update logic. */
  public void updateSuggestionInternal() {
    if (editor.autoPathSuggestion != null
        && editor.autoPathSuggestion.isAutoPathSuggestionEnabled()) {
      boolean handledPathSuggestion =
          editor.autoPathSuggestion.updatePathSuggestionFromAutoSuggestion();
      if (handledPathSuggestion) {
        return;
      }
    }

    String line = editor.windowRender.getLineTextForRender(editor.cursor.cursorLine);
    if (line == null) {
      clearActiveSuggestion();
      return;
    }

    if (!isAutoSuggestionEnabled) {
      clearActiveSuggestion();
      return;
    }

    // Do not show suggestions if the cursor is in the middle of a word
    if (editor.cursor.cursorChar < line.length()
        && Character.isLetterOrDigit(line.charAt(editor.cursor.cursorChar))) {
      clearActiveSuggestion();
      return;
    }

    // Do not show suggestions if there is non-whitespace text after the cursor
    if (editor.cursor.cursorChar < line.length()
        && !line.substring(editor.cursor.cursorChar).trim().isEmpty()) {
      clearActiveSuggestion();
      return;
    }

    String wordFragment = getCurrentWordFragment();
    if (wordFragment.isEmpty()) {
      clearActiveSuggestion();
      return;
    }

    // Prevent suggestions inside syntax highlighting
    List<com.yn.sodiumeditor.renderer.HighlightRender.HighlightSpan> spans =
        editor.highlightRender.getHighlightSpansForLine(line, editor.cursor.cursorLine);
    for (com.yn.sodiumeditor.renderer.HighlightRender.HighlightSpan span : spans) {
      if (editor.cursor.cursorChar > span.start && editor.cursor.cursorChar <= span.end) {
        clearActiveSuggestion();
        return;
      }
    }

    String suggestion = suggestionTrie.findFirstSuggestion(wordFragment);
    if (suggestion != null && suggestion.length() > wordFragment.length()) {
      activeSuggestion = suggestion.substring(wordFragment.length());
      activeSuggestionLine = editor.cursor.cursorLine;
      activeSuggestionCharStart = editor.cursor.cursorChar - wordFragment.length();
      activeSuggestionWordFragment = wordFragment;
      activeSuggestionIsPath = false;
    } else {
      clearActiveSuggestion();
    }
    editor.invalidate();
  }

  /** Get the current word fragment before the cursor. */
  public String getCurrentWordFragment() {
    String line = editor.windowRender.getLineTextForRender(editor.cursor.cursorLine);
    if (editor.cursor.cursorChar == 0 || editor.cursor.cursorChar > line.length()) {
      return "";
    }
    int start = editor.cursor.cursorChar;
    while (start > 0 && Character.isLetterOrDigit(line.charAt(start - 1))) {
      start--;
    }
    return line.substring(start, editor.cursor.cursorChar);
  }

  /** Set suggestion text size. */
  public void setSuggestionTextSize(float size) {
    isSuggestionTextSizeCustom = true;
    float px = editor.view.spToPx(size);
    float base = editor.textRender.paint.getTextSize();
    if (base > 0f) {
      suggestionTextSizeScale = px / base;
    } else {
      suggestionTextSizeScale = 1f;
    }
    suggestionPaint.setTextSize(base * suggestionTextSizeScale);
    editor.invalidate();
  }

  public float getSuggestionTextSize() {
    return suggestionPaint.getTextSize();
  }

  /** Trie node for auto-suggestion. */
  public static class TrieNode {
    final java.util.Map<Character, TrieNode> children = new java.util.TreeMap<>();
    String word = null;
  }

  /** Trie data structure for efficient prefix-based suggestions. */
  public static class Trie {
    public final TrieNode root = new TrieNode();

    public void clear() {
      root.children.clear();
      root.word = null;
    }

    public void insert(String word) {
      if (word == null || word.isEmpty()) return;
      TrieNode current = root;
      for (char l : word.toCharArray()) {
        current = current.children.computeIfAbsent(l, c -> new TrieNode());
      }
      current.word = word;
    }

    public String findFirstSuggestion(String prefix) {
      if (prefix == null || prefix.isEmpty()) return null;
      TrieNode current = root;
      for (char l : prefix.toCharArray()) {
        TrieNode node = current.children.get(l);
        if (node == null) {
          return null;
        }
        current = node;
      }
      String suggestion = findFirstWordFromNode(current);
      if (suggestion != null && suggestion.equals(prefix)) {
        return null;
      }
      return suggestion;
    }

    public String findFirstWordFromNode(TrieNode node) {
      if (node == null) return null;
      if (node.word != null) {
        return node.word;
      }
      for (TrieNode childNode : node.children.values()) {
        String found = findFirstWordFromNode(childNode);
        if (found != null) {
          return found;
        }
      }
      return null;
    }
  }

  // Draw auto suggestion
  public void drawAutoSuggestion(Canvas canvas, String lineContent, int globalLine, float textBaselineY) {

    boolean allowSuggestion =
        activeSuggestionIsPath
            ? (editor.autoPathSuggestion.isAutoPathSuggestionEnabled)
            : (isAutoSuggestionEnabled);
    if (!allowSuggestion || activeSuggestion == null || globalLine != activeSuggestionLine) {
      return;
    }

    int cursorPositionInLine = activeSuggestionCharStart + activeSuggestionWordFragment.length();
    if (cursorPositionInLine > lineContent.length()) {
      clearActiveSuggestion();
      return;
    }

    float suggestionStartX_canvas =
        editor.textRender.measureText(lineContent, cursorPositionInLine, globalLine);
    canvas.drawText(activeSuggestion, suggestionStartX_canvas, textBaselineY, suggestionPaint);

    float suggestionTextWidth = suggestionPaint.measureText(activeSuggestion);
    float left_view =
        suggestionStartX_canvas
            + editor.layout.getTextStartX()
            - editor.scroll.getEffectiveScrollX();
    float right_view = left_view + suggestionTextWidth;
    if (editor.textRender.isRtl) {
      float baseX = editor.layout.getRtlLineBaseX(lineContent, globalLine);
      left_view += baseX;
      right_view += baseX;
    }
    float top_view = globalLine * editor.textRender.lineHeight - editor.scroll.scrollY;
    float bottom_view = (globalLine + 1) * editor.textRender.lineHeight - editor.scroll.scrollY;
    activeSuggestionRect.set(left_view, top_view, right_view, bottom_view);
  }

  public void drawAutoSuggestionWrapped(Canvas canvas, String lineContent, int globalLine, int segStart, int segEnd, int visualIndex, float textBaselineY) {

    boolean allowSuggestion =
        activeSuggestionIsPath
            ? (editor.autoPathSuggestion.isAutoPathSuggestionEnabled)
            : (isAutoSuggestionEnabled);
    if (!allowSuggestion || activeSuggestion == null || globalLine != activeSuggestionLine) {
      return;
    }

    int cursorPositionInLine = activeSuggestionCharStart + activeSuggestionWordFragment.length();
    if (cursorPositionInLine < segStart || cursorPositionInLine > segEnd) return;

    float suggestionStartX_canvas = editor.textRender.measureTextWithVisualSpaces(lineContent, segStart, cursorPositionInLine, editor.textRender.paint);
    canvas.drawText(activeSuggestion, suggestionStartX_canvas, textBaselineY, suggestionPaint);

    float suggestionTextWidth = suggestionPaint.measureText(activeSuggestion);
    float left_view =
        suggestionStartX_canvas
            + editor.layout.getTextStartX()
            - editor.scroll.getEffectiveScrollX();
    float right_view = left_view + suggestionTextWidth;
    if (editor.textRender.isRtl) {
      float baseX = editor.layout.getRtlSegmentBaseX(lineContent, globalLine, segStart, segEnd);
      left_view += baseX;
      right_view += baseX;
    }
    float top_view = visualIndex * editor.textRender.lineHeight - editor.scroll.scrollY;
    float bottom_view = top_view + editor.textRender.lineHeight;
    activeSuggestionRect.set(left_view, top_view, right_view, bottom_view);
  }
}
