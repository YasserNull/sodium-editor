package com.yn.sodiumeditor.view;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import androidx.annotation.Nullable;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

final class AutoSuggestionManager {
  private static final long SUGGESTION_UPDATE_DEBOUNCE_MS = 40L;

  private final SodiumEditorView view;
  private final Paint suggestionPaint = new Paint();
  private final RectF activeSuggestionRect = new RectF(); // For tap-to-accept
  private final Trie suggestionTrie = new Trie();

  private String activeSuggestion = null;
  private int activeSuggestionLine = 0;
  private int activeSuggestionCharStart = 0; // character index where the word fragment starts
  private String activeSuggestionWordFragment = ""; // the part user typed
  private boolean activeSuggestionIsPath = false;

  private boolean isSuggestionTextSizeCustom = false; // size follows main text by default
  private float suggestionTextSizeScale = 1f;

  private boolean isAutoCompletionEnabled = false;
  private boolean isAutoPathCompletionEnabled = false;

  private long lastSuggestionUpdateUptime = 0L;
  private boolean suggestionUpdateScheduled = false;
  private final Runnable suggestionUpdateRunnable =
      () -> {
        suggestionUpdateScheduled = false;
        lastSuggestionUpdateUptime = SystemClock.uptimeMillis();
        updateSuggestionInternal();
      };

  private boolean suggestionAcceptedThisTouch = false;

  private String lastPathQuery = null;
  private String lastPathSuggestion = null;

  AutoSuggestionManager(SodiumEditorView view) {
    this.view = view;
  }

  void initPaints(Paint basePaint) {
    suggestionPaint.set(basePaint);
    suggestionPaint.setColor(0xFFAAAAAA); // Default faint gray
    suggestionPaint.setAntiAlias(true);
    suggestionPaint.setSubpixelText(true);
    suggestionPaint.setHinting(Paint.HINTING_ON);
    isSuggestionTextSizeCustom = false;
    suggestionTextSizeScale = 1f;
  }

  void onEditorTypefaceChanged(@Nullable android.graphics.Typeface typeface) {
    suggestionPaint.setTypeface(typeface);
  }

  void onTextSizeChanged(float sizePx) {
    if (!isSuggestionTextSizeCustom) {
      suggestionTextSizeScale = 1f;
    }
    suggestionPaint.setTextSize(sizePx * suggestionTextSizeScale);
  }

  void setAutoCompletionEnabled(boolean enabled) {
    isAutoCompletionEnabled = enabled;
    if (!enabled && (!activeSuggestionIsPath || !isAutoPathCompletionEnabled)) {
      clearActiveSuggestion();
    }
    view.invalidate();
  }

  void setAutoPathCompletionEnabled(boolean enabled) {
    isAutoPathCompletionEnabled = enabled;
    if (!enabled && (activeSuggestionIsPath || !isAutoCompletionEnabled)) {
      clearActiveSuggestion();
    }
    view.invalidate();
  }

  boolean isAutoCompletionEnabled() {
    return isAutoCompletionEnabled;
  }

  boolean isAutoPathCompletionEnabled() {
    return isAutoPathCompletionEnabled;
  }

  boolean isSuggestionAcceptedThisTouch() {
    return suggestionAcceptedThisTouch;
  }

  void clearSuggestionAcceptedThisTouch() {
    suggestionAcceptedThisTouch = false;
  }

  void setSuggestions(@Nullable List<String> keywords, int color) {
    suggestionTrie.clear();
    if (keywords != null) {
      for (String word : keywords) {
        suggestionTrie.insert(word);
      }
    }
    // Only set the color. Size and style are synced automatically.
    suggestionPaint.setColor(color);
    clearActiveSuggestion();
  }

  void acceptAutoCompletion() {
    Log.d("SodiumEditorView", "acceptAutoCompletion: Entered.");
    if (activeSuggestion == null) {
      Log.d("SodiumEditorView", "acceptAutoCompletion: Bailed out (disabled or no active suggestion).");
      return;
    }
    if (activeSuggestionIsPath && !isAutoPathCompletionEnabled) {
      Log.d("SodiumEditorView", "acceptAutoCompletion: Bailed out (path disabled).");
      return;
    }
    if (!activeSuggestionIsPath && !isAutoCompletionEnabled) {
      Log.d("SodiumEditorView", "acceptAutoCompletion: Bailed out (word disabled).");
      return;
    }

    view.cursorManager.commitComposing(false);

    // Set a flag to ignore subsequent gesture events from this touch sequence.
    suggestionAcceptedThisTouch = true;

    String textToInsert = activeSuggestion;
    clearActiveSuggestion();
    view.selectionManager.clearSelectionKeepLineNumberState(); // Clear selection after accepting suggestion
    Log.d("SodiumEditorView", "acceptAutoCompletion: Cleared selection flags, inserting text.");
    view.insertStringAtCursorForSuggestion(textToInsert);
    Log.d("SodiumEditorView", "acceptAutoCompletion: Text inserted.");

    view.restartInputForSuggestion(); // Force IME to resync
  }

  void setSuggestionTextSize(float sizeSp) {
    isSuggestionTextSizeCustom = true;
    float px = sizeSp * view.getResources().getDisplayMetrics().scaledDensity;
    float base = view.paint.getTextSize();
    if (base > 0f) {
      suggestionTextSizeScale = px / base;
    } else {
      suggestionTextSizeScale = 1f;
    }
    suggestionPaint.setTextSize(base * suggestionTextSizeScale);
    view.invalidate();
  }

  void clearActiveSuggestion() {
    if (activeSuggestion != null) {
      activeSuggestion = null;
      activeSuggestionRect.setEmpty();
      activeSuggestionIsPath = false;
      view.invalidate();
    }
  }

  void updateSuggestion() {
    if (Looper.myLooper() != Looper.getMainLooper()) {
      view.post(this::updateSuggestion);
      return;
    }
    view.imeManager.updateImeSelection();
    long now = SystemClock.uptimeMillis();
    if (now - lastSuggestionUpdateUptime < SUGGESTION_UPDATE_DEBOUNCE_MS) {
      if (!suggestionUpdateScheduled) {
        suggestionUpdateScheduled = true;
        view.mainHandler.postDelayed(suggestionUpdateRunnable, SUGGESTION_UPDATE_DEBOUNCE_MS);
      }
      return;
    }
    lastSuggestionUpdateUptime = now;
    updateSuggestionInternal();
  }

  boolean maybeAcceptSuggestionTap(float ex, float ey, int line, boolean isEmptyArea) {
    boolean allowSuggestionTap =
        activeSuggestionIsPath ? isAutoPathCompletionEnabled : isAutoCompletionEnabled;
    if (!allowSuggestionTap || activeSuggestion == null || activeSuggestionRect.isEmpty()) {
      return false;
    }

    if (activeSuggestionRect.contains(ex, ey)) {
      Log.d(
          "SodiumEditorView",
          "onTouchEvent.ACTION_UP: Suggestion tap detected. Calling acceptAutoCompletion.");
      acceptAutoCompletion();
      return true;
    } else if (isEmptyArea && line == activeSuggestionLine) {
      Log.d(
          "SodiumEditorView",
          "onTouchEvent.ACTION_UP: Suggestion tap detected. Calling acceptAutoCompletion.");
      acceptAutoCompletion();
      return true;
    }
    return false;
  }

  void drawAutoSuggestion(Canvas canvas, String lineContent, int globalLine, float textBaselineY) {
    boolean allowSuggestion =
        activeSuggestionIsPath ? isAutoPathCompletionEnabled : isAutoCompletionEnabled;
    if (!allowSuggestion || activeSuggestion == null || globalLine != activeSuggestionLine) {
      return;
    }
    if (lineContent == null) lineContent = "";

    int cursorPositionInLine = activeSuggestionCharStart + activeSuggestionWordFragment.length();
    if (cursorPositionInLine < 0 || cursorPositionInLine > lineContent.length()) return;

    float suggestionStartX =
        view.whitespaceGuideManager.measureTextWithVisualSpaces(
            view, lineContent, 0, cursorPositionInLine, view.paint);
    canvas.drawText(activeSuggestion, suggestionStartX, textBaselineY, suggestionPaint);

    float suggestionTextWidth = suggestionPaint.measureText(activeSuggestion);
    float leftView = suggestionStartX + view.getTextStartX() - view.getEffectiveScrollX();
    float rightView = leftView + suggestionTextWidth;
    float topView = view.scrollManager.getDrawLineTop(globalLine);
    float bottomView = topView + view.lineHeight;

    activeSuggestionRect.set(leftView, topView, rightView, bottomView);
  }

  void drawAutoSuggestionWrapped(
      Canvas canvas,
      String lineContent,
      int globalLine,
      int segStart,
      int segEnd,
      int visualIndex,
      float textBaselineY) {
    boolean allowSuggestion =
        activeSuggestionIsPath ? isAutoPathCompletionEnabled : isAutoCompletionEnabled;
    if (!allowSuggestion || activeSuggestion == null || globalLine != activeSuggestionLine) {
      return;
    }

    int cursorPositionInLine = activeSuggestionCharStart + activeSuggestionWordFragment.length();
    if (cursorPositionInLine < segStart || cursorPositionInLine > segEnd) return;

    float suggestionStartX_canvas =
        view.whitespaceGuideManager.measureTextWithVisualSpaces(
            view, lineContent, segStart, cursorPositionInLine, view.paint);
    canvas.drawText(activeSuggestion, suggestionStartX_canvas, textBaselineY, suggestionPaint);

    float suggestionTextWidth = suggestionPaint.measureText(activeSuggestion);

    float left_view = suggestionStartX_canvas + view.getTextStartX() - view.getEffectiveScrollX();
    float right_view = left_view + suggestionTextWidth;
    if (view.isRtl) {
      float baseX = view.getRtlSegmentBaseX(lineContent, globalLine, segStart, segEnd);
      left_view += baseX;
      right_view += baseX;
    }
    float top_view = visualIndex * view.lineHeight - view.scrollManager.scrollY;
    float bottom_view = top_view + view.lineHeight;

    activeSuggestionRect.set(left_view, top_view, right_view, bottom_view);
  }

  private void updateSuggestionInternal() {
    String line = view.getLineTextForRender(view.cursorManager.getLine());
    if (line == null) {
      clearActiveSuggestion();
      return;
    }

    if (!isAutoPathCompletionEnabled && !isAutoCompletionEnabled) {
      clearActiveSuggestion();
      return;
    }

    // Do not show suggestions if the cursor is in the middle of a word
    if (view.cursorManager.getChar() < line.length()
        && Character.isLetterOrDigit(line.charAt(view.cursorManager.getChar()))) {
      clearActiveSuggestion();
      return;
    }

    // Do not show suggestions if there is non-whitespace text after the cursor
    if (view.cursorManager.getChar() < line.length()
        && !line.substring(view.cursorManager.getChar()).trim().isEmpty()) {
      clearActiveSuggestion();
      return;
    }

    String pathFragment = "";
    String wordFragment = "";
    if (isAutoPathCompletionEnabled) {
      pathFragment = getCurrentPathFragment();
    }
    if (isAutoCompletionEnabled && pathFragment.isEmpty()) {
      wordFragment = getCurrentWordFragment();
    }
    if (pathFragment.isEmpty() && wordFragment.isEmpty()) {
      clearActiveSuggestion();
      return;
    }

    // Prevent suggestions inside syntax highlighting (expensive).
    List<HighlightManager.HighlightSpan> spans =
        view.highlightManager.highlightCache.get(view.cursorManager.getLine());
    if (spans == null) {
      spans = view.highlightManager.calculateSpansForLine(line, view.cursorManager.getLine());
      view.highlightManager.highlightCache.put(view.cursorManager.getLine(), spans);
    }
    for (HighlightManager.HighlightSpan span : spans) {
      if (view.cursorManager.getChar() > span.start
          && view.cursorManager.getChar() <= span.end) {
        clearActiveSuggestion();
        return;
      }
    }

    if (!pathFragment.isEmpty()) {
      String suggestion = findPathSuggestion(pathFragment);
      if (suggestion != null && suggestion.length() > pathFragment.length()) {
        activeSuggestion = suggestion.substring(pathFragment.length());
        activeSuggestionLine = view.cursorManager.getLine();
        activeSuggestionCharStart = view.cursorManager.getChar() - pathFragment.length();
        activeSuggestionWordFragment = pathFragment;
        activeSuggestionIsPath = true;
      } else {
        clearActiveSuggestion();
      }
      view.invalidate();
      return;
    }

    if (wordFragment.isEmpty()) {
      clearActiveSuggestion();
      return;
    }

    String suggestion = suggestionTrie.findFirstSuggestion(wordFragment);
    if (suggestion != null && suggestion.length() > wordFragment.length()) {
      activeSuggestion = suggestion.substring(wordFragment.length());
      activeSuggestionLine = view.cursorManager.getLine();
      activeSuggestionCharStart = view.cursorManager.getChar() - wordFragment.length();
      activeSuggestionWordFragment = wordFragment;
      activeSuggestionIsPath = false;
    } else {
      clearActiveSuggestion();
    }
    view.invalidate();
  }

  private String getCurrentWordFragment() {
    String line = view.getLineTextForRender(view.cursorManager.getLine());
    if (view.cursorManager.getChar() == 0
        || view.cursorManager.getChar() > line.length()) {
      return "";
    }
    int start = view.cursorManager.getChar();
    // A word character is a letter or a digit.
    while (start > 0 && Character.isLetterOrDigit(line.charAt(start - 1))) {
      start--;
    }
    return line.substring(start, view.cursorManager.getChar());
  }

  private String getCurrentPathFragment() {
    String line = view.getLineTextForRender(view.cursorManager.getLine());
    if (view.cursorManager.getChar() == 0
        || view.cursorManager.getChar() > line.length()) {
      return "";
    }
    int start = view.cursorManager.getChar();
    while (start > 0 && isPathChar(line.charAt(start - 1))) {
      start--;
    }
    String fragment = line.substring(start, view.cursorManager.getChar());
    if (fragment.isEmpty()) return "";
    if (fragment.startsWith("/")
        || fragment.startsWith("~")
        || fragment.startsWith("./")
        || fragment.startsWith("../")
        || fragment.contains("/")) {
      return fragment;
    }
    return "";
  }

  private boolean isPathChar(char c) {
    return Character.isLetterOrDigit(c)
        || c == '/'
        || c == '.'
        || c == '_'
        || c == '-'
        || c == '~';
  }

  @Nullable
  private String findPathSuggestion(String fragment) {
    if (fragment.equals(lastPathQuery)) {
      return lastPathSuggestion;
    }

    String expanded = fragment;
    String home = getHomeDir();
    if (fragment.startsWith("~") && home != null) {
      if (fragment.equals("~")) {
        expanded = home;
      } else if (fragment.startsWith("~/")) {
        expanded = home + fragment.substring(1);
      }
    }

    int lastSlash = expanded.lastIndexOf('/');
    String dirPart = lastSlash >= 0 ? expanded.substring(0, lastSlash) : "";
    String prefix = lastSlash >= 0 ? expanded.substring(lastSlash + 1) : expanded;
    File dir = resolveBaseDir(expanded, fragment, dirPart, home);
    if (dir == null || !dir.exists() || !dir.isDirectory()) {
      lastPathQuery = fragment;
      lastPathSuggestion = null;
      return null;
    }

    File[] entries = dir.listFiles();
    if (entries == null || entries.length == 0) {
      lastPathQuery = fragment;
      lastPathSuggestion = null;
      return null;
    }

    List<String> matches = new ArrayList<>();
    boolean allowHidden = prefix.startsWith(".");
    for (File entry : entries) {
      String name = entry.getName();
      if (!allowHidden && name.startsWith(".")) continue;
      if (name.startsWith(prefix)) {
        matches.add(entry.isDirectory() ? name + "/" : name);
      }
    }
    String suggestion = null;
    if (!matches.isEmpty()) {
      Collections.sort(matches);
      String chosen = chooseClosestPrefix(matches);
      suggestion = fragment + chosen.substring(prefix.length());
    } else {
      String fallback = chooseClosestByCommonPrefix(entries, prefix, allowHidden);
      if (fallback != null) {
        suggestion = fragment + fallback;
      }
    }

    lastPathQuery = fragment;
    lastPathSuggestion = suggestion;
    return suggestion;
  }

  @Nullable
  private File resolveBaseDir(String expanded, String fragment, String dirPart, @Nullable String home) {
    if (expanded.startsWith("/")) {
      return new File(dirPart.isEmpty() ? "/" : dirPart);
    }
    if (fragment.startsWith("~") && home != null) {
      return new File(dirPart.isEmpty() ? home : dirPart);
    }
    File base = getDefaultBaseDir();
    if (base == null) return null;
    return dirPart.isEmpty() ? base : new File(base, dirPart);
  }

  @Nullable
  private File getDefaultBaseDir() {
    if (view.sourceFile != null) {
      File parent = view.sourceFile.getParentFile();
      if (parent != null) return parent;
    }
    String home = getHomeDir();
    if (home != null) return new File(home);
    return new File("/");
  }

  @Nullable
  private String getHomeDir() {
    String home = System.getenv("HOME");
    if (home == null || home.isEmpty()) {
      home = System.getProperty("user.home");
    }
    return (home == null || home.isEmpty()) ? null : home;
  }

  private String chooseClosestPrefix(List<String> matches) {
    String best = matches.get(0);
    for (String name : matches) {
      if (name.length() < best.length()) {
        best = name;
      } else if (name.length() == best.length() && name.compareTo(best) < 0) {
        best = name;
      }
    }
    return best;
  }

  @Nullable
  private String chooseClosestByCommonPrefix(
      File[] entries, String prefix, boolean allowHidden) {
    int bestScore = -1;
    String bestName = null;
    for (File entry : entries) {
      String name = entry.getName();
      if (!allowHidden && name.startsWith(".")) continue;
      int score = commonPrefixLength(prefix, name);
      if (score > bestScore) {
        bestScore = score;
        bestName = entry.isDirectory() ? name + "/" : name;
      } else if (score == bestScore && bestName != null && name.compareTo(bestName) < 0) {
        bestName = entry.isDirectory() ? name + "/" : name;
      }
    }
    return (bestScore <= 0) ? null : bestName;
  }

  private int commonPrefixLength(String a, String b) {
    int len = Math.min(a.length(), b.length());
    int i = 0;
    while (i < len && a.charAt(i) == b.charAt(i)) i++;
    return i;
  }

  private static final class TrieNode {
    final Map<Character, TrieNode> children = new java.util.TreeMap<>();
    @Nullable String word = null;
  }

  private static final class Trie {
    private final TrieNode root = new TrieNode();

    void clear() {
      root.children.clear();
      root.word = null;
    }

    void insert(String word) {
      if (word == null || word.isEmpty()) return;
      TrieNode current = root;
      for (char l : word.toCharArray()) {
        current = current.children.computeIfAbsent(l, c -> new TrieNode());
      }
      current.word = word;
    }

    @Nullable
    String findFirstSuggestion(String prefix) {
      if (prefix == null || prefix.isEmpty()) return null;
      TrieNode current = root;
      for (char l : prefix.toCharArray()) {
        TrieNode node = current.children.get(l);
        if (node == null) return null;
        current = node;
      }
      String suggestion = findFirstWordFromNode(current);
      // Don't suggest the exact word the user has already typed.
      if (suggestion != null && suggestion.equals(prefix)) {
        return null;
      }
      return suggestion;
    }

    private String findFirstWordFromNode(TrieNode node) {
      if (node.word != null) return node.word;
      // Using TreeMap in TrieNode makes this loop alphabetically deterministic.
      for (TrieNode childNode : node.children.values()) {
        String found = findFirstWordFromNode(childNode);
        if (found != null) return found;
      }
      return null;
    }
  }
}
