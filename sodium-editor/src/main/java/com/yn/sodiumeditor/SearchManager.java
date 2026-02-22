package com.yn.sodiumeditor;

import android.graphics.Canvas;
import android.graphics.Paint;
import androidx.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class SearchManager {
  private final SodiumEditorView view;

  private String searchQuery = "";
  private boolean searchUseRegex = false;
  private boolean searchCaseSensitive = false;
  private boolean searchWrap = true;
  private boolean searchHighlightEnabled = true;
  private int searchHighlightColor = 0x66FFD54F;
  private Pattern searchPattern = null;
  private String searchCacheKey = null;
  private int searchCacheEditVersion = -1;
  private final HashMap<Integer, int[]> searchMatchCache = new HashMap<>();
  private final Paint searchHighlightPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private boolean highlightCurrentSearchMatch = false;
  private int currentSearchMatchColor = 0x9933B5E5;
  private final Paint currentSearchMatchPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

  SearchManager(SodiumEditorView view) {
    this.view = view;
    searchHighlightPaint.setStyle(Paint.Style.FILL);
    searchHighlightPaint.setColor(searchHighlightColor);
    currentSearchMatchPaint.setColor(currentSearchMatchColor);
  }

  private boolean isSearchActive() {
    if (searchQuery == null || searchQuery.isEmpty()) return false;
    if (searchUseRegex) return searchPattern != null;
    return true;
  }

  private void clearSearchMatchCache() {
    searchMatchCache.clear();
    searchCacheEditVersion = -1;
    searchCacheKey = null;
  }

  private String getSearchCacheKey() {
    return searchQuery
        + "|"
        + (searchUseRegex ? "r" : "t")
        + "|"
        + (searchCaseSensitive ? "c" : "i");
  }

  int[] getSearchMatchSpansForLine(String line, int globalLine) {
    if (!searchHighlightEnabled || !isSearchActive() || line == null || line.isEmpty()) {
      return new int[0];
    }

    int version = view.getEditVersionForSearch();
    String key = getSearchCacheKey();
    if (searchCacheEditVersion != version
        || (searchCacheKey != null && !searchCacheKey.equals(key))) {
      searchMatchCache.clear();
      searchCacheEditVersion = version;
      searchCacheKey = key;
    }

    int[] cached = searchMatchCache.get(globalLine);
    if (cached != null) return cached;

    ArrayList<Integer> tmp = null;
    if (searchUseRegex && searchPattern != null) {
      Matcher matcher = searchPattern.matcher(line);
      while (matcher.find()) {
        if (matcher.start() == matcher.end()) continue;
        if (tmp == null) tmp = new ArrayList<>();
        tmp.add(matcher.start());
        tmp.add(matcher.end());
      }
    } else {
      String haystack = searchCaseSensitive ? line : line.toLowerCase(Locale.ROOT);
      String needle = searchCaseSensitive ? searchQuery : searchQuery.toLowerCase(Locale.ROOT);
      if (!needle.isEmpty()) {
        int idx = haystack.indexOf(needle, 0);
        while (idx >= 0) {
          if (tmp == null) tmp = new ArrayList<>();
          tmp.add(idx);
          tmp.add(idx + needle.length());
          idx = haystack.indexOf(needle, idx + Math.max(1, needle.length()));
        }
      }
    }

    int[] spans;
    if (tmp == null || tmp.isEmpty()) {
      spans = new int[0];
    } else {
      spans = new int[tmp.size()];
      for (int i = 0; i < tmp.size(); i++) spans[i] = tmp.get(i);
    }
    searchMatchCache.put(globalLine, spans);
    return spans;
  }

  public void drawSearchHighlightsForLine(
      Canvas canvas, String line, int globalLine, float top, float bottom) {
    int[] spans = getSearchMatchSpansForLine(line, globalLine);
    if (spans.length == 0) return;
    for (int i = 0; i + 1 < spans.length; i += 2) {
      int start = spans[i];
      int end = spans[i + 1];
      if (end <= start) continue;
      float left = view.measureTextForSearch(line, start, globalLine);
      float right = view.measureTextForSearch(line, end, globalLine);

      boolean isCurrentMatch =
          highlightCurrentSearchMatch
              && !view.selectionManager.hasSelection()
              && globalLine == view.cursorManager.getLine()
              && view.cursorManager.getChar() >= start
              && view.cursorManager.getChar() <= end;

      Paint paintToUse = isCurrentMatch ? currentSearchMatchPaint : searchHighlightPaint;
      canvas.drawRect(left, top, right, bottom, paintToUse);
    }
  }

  public void drawSearchHighlightsForSegment(
      Canvas canvas,
      String line,
      int globalLine,
      int segStart,
      int segEnd,
      float top,
      float bottom) {
    int[] spans = getSearchMatchSpansForLine(line, globalLine);
    if (spans.length == 0) return;
    for (int i = 0; i + 1 < spans.length; i += 2) {
      int start = spans[i];
      int end = spans[i + 1];
      if (end <= start) continue;
      int s = Math.max(segStart, start);
      int e = Math.min(segEnd, end);
      if (e <= s) continue;
      float left = view.measureTextWithVisualSpacesForSearch(line, segStart, s);
      float right = left + view.measureTextWithVisualSpacesForSearch(line, s, e);

      boolean isCurrentMatch =
          highlightCurrentSearchMatch
              && !view.selectionManager.hasSelection()
              && globalLine == view.cursorManager.getLine()
              && view.cursorManager.getChar() >= start
              && view.cursorManager.getChar() <= end;

      Paint paintToUse = isCurrentMatch ? currentSearchMatchPaint : searchHighlightPaint;
      canvas.drawRect(left, top, right, bottom, paintToUse);
    }
  }

  boolean goToSearchMatch(boolean forward) {
    if (!isSearchActive()) return false;
    int total = view.getLinesCount();
    if (total <= 0) return false;

    int startLine = Math.max(0, view.cursorManager.getLine());
    int startChar = Math.max(0, view.cursorManager.getChar());

    SearchMatch match =
        forward
            ? findNextSearchMatchFrom(startLine, startChar)
            : findPrevSearchMatchFrom(startLine, startChar);
    if (match == null) return false;

    view.ensureLineInWindowForSearch(match.line, true);
    view.cursorManager.setPosition(match.line, match.start);
    return true;
  }

  private SearchMatch findNextSearchMatchFrom(int line, int charIndex) {
    int total = view.getLinesCount();
    if (total <= 0) return null;

    HashMap<Integer, String> direct = new HashMap<>();

    SearchMatch m = findNextSearchMatchInRange(line, total - 1, charIndex + 1, null, direct);
    if (m != null) return m;
    if (searchWrap && line > 0) {
      return findNextSearchMatchInRange(0, line, 0, charIndex, direct);
    }
    return null;
  }

  private SearchMatch findPrevSearchMatchFrom(int line, int charIndex) {
    int total = view.getLinesCount();
    if (total <= 0) return null;

    HashMap<Integer, String> direct = new HashMap<>();

    SearchMatch m = findPrevSearchMatchInRange(line, 0, charIndex - 1, null, direct);
    if (m != null) return m;
    if (searchWrap && line < total - 1) {
      return findPrevSearchMatchInRange(total - 1, line, Integer.MAX_VALUE, charIndex - 1, direct);
    }
    return null;
  }

  private SearchMatch findNextSearchMatchInRange(
      int startLine,
      int endLine,
      int startCharExclusive,
      @Nullable Integer maxStartInclusive,
      HashMap<Integer, String> direct) {
    int step = (startLine <= endLine) ? 1 : -1;
    int line = startLine;
    int total = view.getLinesCount();
    int chunkSize = 200;
    while (true) {
      if (line < 0 || line >= total) break;
      if (line < view.getWindowStartLineForSearch()
          || line >= view.getWindowStartLineForSearch() + view.getWindowSizeForSearch()) {
        if (view.isIndexReadyForSearch() && view.getSourceFileForSearchExists()) {
          int rangeStart = Math.max(0, Math.min(line, line + (step * (chunkSize - 1))));
          int rangeEnd = Math.min(total - 1, Math.max(line, line + (step * (chunkSize - 1))));
          view.populateDirectLinesForRangeForSearch(rangeStart, rangeEnd, direct);
        }
      }
      String lineText = view.getLineTextForRenderWithDirectForSearch(line, direct);
      if (lineText == null) lineText = "";
      int from =
          (line == startLine)
              ? Math.max(0, Math.min(startCharExclusive, lineText.length()))
              : 0;
      Integer limit = (maxStartInclusive != null && line == endLine) ? maxStartInclusive : null;
      SearchMatch m = findMatchForwardInLine(lineText, from, limit);
      if (m != null) {
        m.line = line;
        return m;
      }
      if (line == endLine) break;
      line += step;
    }
    return null;
  }

  private SearchMatch findPrevSearchMatchInRange(
      int startLine,
      int endLine,
      int startCharExclusive,
      @Nullable Integer minStartInclusive,
      HashMap<Integer, String> direct) {
    int step = (startLine >= endLine) ? -1 : 1;
    int line = startLine;
    int total = view.getLinesCount();
    int chunkSize = 200;
    while (true) {
      if (line < 0 || line >= total) break;
      if (line < view.getWindowStartLineForSearch()
          || line >= view.getWindowStartLineForSearch() + view.getWindowSizeForSearch()) {
        if (view.isIndexReadyForSearch() && view.getSourceFileForSearchExists()) {
          int rangeStart = Math.max(0, Math.min(line, line + (step * (chunkSize - 1))));
          int rangeEnd = Math.min(total - 1, Math.max(line, line + (step * (chunkSize - 1))));
          view.populateDirectLinesForRangeForSearch(rangeStart, rangeEnd, direct);
        }
      }
      String lineText = view.getLineTextForRenderWithDirectForSearch(line, direct);
      if (lineText == null) lineText = "";
      int from =
          (line == startLine)
              ? Math.max(0, Math.min(startCharExclusive, lineText.length()))
              : lineText.length();
      Integer limit = (minStartInclusive != null && line == endLine) ? minStartInclusive : null;
      SearchMatch m = findMatchBackwardInLine(lineText, from, limit);
      if (m != null) {
        m.line = line;
        return m;
      }
      if (line == endLine) break;
      line += step;
    }
    return null;
  }

  private SearchMatch findMatchForwardInLine(
      String line, int fromIndex, @Nullable Integer maxStartInclusive) {
    if (line == null || line.isEmpty()) return null;
    if (searchUseRegex && searchPattern != null) {
      Matcher m = searchPattern.matcher(line);
      if (m.find(fromIndex)) {
        if (maxStartInclusive != null && m.start() > maxStartInclusive) return null;
        return new SearchMatch(-1, m.start(), m.end());
      }
      return null;
    }
    String haystack = searchCaseSensitive ? line : line.toLowerCase(Locale.ROOT);
    String needle = searchCaseSensitive ? searchQuery : searchQuery.toLowerCase(Locale.ROOT);
    if (needle.isEmpty()) return null;
    int idx = haystack.indexOf(needle, fromIndex);
    if (idx < 0) return null;
    if (maxStartInclusive != null && idx > maxStartInclusive) return null;
    return new SearchMatch(-1, idx, idx + needle.length());
  }

  private SearchMatch findMatchBackwardInLine(
      String line, int fromIndex, @Nullable Integer minStartInclusive) {
    if (line == null || line.isEmpty()) return null;
    if (searchUseRegex && searchPattern != null) {
      Matcher m = searchPattern.matcher(line);
      SearchMatch last = null;
      while (m.find()) {
        if (m.start() > fromIndex) break;
        if (minStartInclusive != null && m.start() < minStartInclusive) continue;
        last = new SearchMatch(-1, m.start(), m.end());
      }
      return last;
    }
    String haystack = searchCaseSensitive ? line : line.toLowerCase(Locale.ROOT);
    String needle = searchCaseSensitive ? searchQuery : searchQuery.toLowerCase(Locale.ROOT);
    if (needle.isEmpty()) return null;
    int idx = haystack.lastIndexOf(needle, Math.min(fromIndex, haystack.length()));
    if (idx < 0) return null;
    if (minStartInclusive != null && idx < minStartInclusive) return null;
    return new SearchMatch(-1, idx, idx + needle.length());
  }

  boolean selectSearchMatch(boolean forward) {
    if (!isSearchActive()) return false;
    int total = view.getLinesCount();
    if (total <= 0) return false;

    int startLine = Math.max(0, view.cursorManager.getLine());
    int startChar = Math.max(0, view.cursorManager.getChar());

    SearchMatch match =
        forward
            ? findNextSearchMatchFrom(startLine, startChar)
            : findPrevSearchMatchFrom(startLine, startChar);
    if (match == null) return false;

    view.ensureLineInWindowForSearch(match.line, true);
    view.setSelectionInternal(match.line, match.start, match.line, match.end);
    view.cursorManager.setPositionNoClear(match.line, match.end);
    return true;
  }

  boolean selectSearchMatchInclusive(boolean forward) {
    if (!isSearchActive()) return false;
    int total = view.getLinesCount();
    if (total <= 0) return false;

    int startLine = Math.max(0, view.cursorManager.getLine());
    int startChar = Math.max(0, view.cursorManager.getChar());
    if (forward) {
      startChar = Math.max(-1, startChar - 1);
    } else {
      startChar = startChar + 1;
    }

    SearchMatch match =
        forward
            ? findNextSearchMatchFrom(startLine, startChar)
            : findPrevSearchMatchFrom(startLine, startChar);
    if (match == null) return false;

    if (view.comparePos(match.line, match.start, startLine, startChar) > 0
        || view.comparePos(match.line, match.end, startLine, startChar) >= 0) {
      view.ensureLineInWindowForSearch(match.line, true);
      view.setSelectionInternal(match.line, match.start, match.line, match.end);
      view.cursorManager.setPositionNoClear(match.line, match.end);
      return true;
    }
    return false;
  }

  boolean selectSearchMatchAtCursorOrNext() {
    SearchMatch atCursor = findSearchMatchAtCursor();
    if (atCursor != null) {
      view.ensureLineInWindowForSearch(atCursor.line, true);
      view.setSelectionInternal(atCursor.line, atCursor.start, atCursor.line, atCursor.end);
              view.cursorManager.setPositionNoClear(atCursor.line, atCursor.end);      return true;
    }
    return selectSearchMatchInclusive(true);
  }

  private SearchMatch findSearchMatchAtCursor() {
    if (!isSearchActive()) return null;
    int line = view.cursorManager.getLine();
    int ch = view.cursorManager.getChar();
    String lineText = view.getLineTextForRender(line);
    if (lineText == null) lineText = "";

    if (searchUseRegex) {
      if (searchPattern == null) return null;
      Matcher matcher = searchPattern.matcher(lineText);
      while (matcher.find()) {
        int s = matcher.start();
        int e = matcher.end();
        if (ch >= s && ch < e) {
          return new SearchMatch(line, s, e);
        }
      }
      return null;
    }

    String needle = searchQuery == null ? "" : searchQuery;
    if (needle.isEmpty()) return null;
    if (!searchCaseSensitive) {
      lineText = lineText.toLowerCase(Locale.ROOT);
      needle = needle.toLowerCase(Locale.ROOT);
    }
    int idx = lineText.lastIndexOf(needle, Math.min(ch, lineText.length()));
    if (idx < 0) return null;
    int end = idx + needle.length();
    if (ch >= idx && ch < end) {
      return new SearchMatch(line, idx, end);
    }
    return null;
  }

  void setSearchQuery(
      @Nullable String query, boolean useRegex, boolean caseSensitive, boolean wrapAround) {
    String safe = (query == null) ? "" : query;
    if (safe.equals(searchQuery)
        && searchUseRegex == useRegex
        && searchCaseSensitive == caseSensitive
        && searchWrap == wrapAround) {
      return;
    }
    searchQuery = safe;
    searchUseRegex = useRegex;
    searchCaseSensitive = caseSensitive;
    searchWrap = wrapAround;
    searchPattern = null;
    if (searchUseRegex && !searchQuery.isEmpty()) {
      int flags = Pattern.MULTILINE;
      if (!searchCaseSensitive) flags |= (Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
      try {
        searchPattern = Pattern.compile(searchQuery, flags);
      } catch (Exception e) {
        searchPattern = null;
      }
    }
    clearSearchMatchCache();
    view.invalidate();
  }

  public void setSearchHighlightEnabled(boolean enabled) {
    if (searchHighlightEnabled == enabled) return;
    searchHighlightEnabled = enabled;
    view.invalidate();
  }

  public void setSearchHighlightColor(int color) {
    searchHighlightColor = color;
    searchHighlightPaint.setColor(color);
    view.invalidate();
  }

  public void setHighlightCurrentSearchMatchEnabled(boolean enabled) {
    if (highlightCurrentSearchMatch == enabled) return;
    highlightCurrentSearchMatch = enabled;
    view.invalidate();
  }

  public void setCurrentSearchMatchColor(int color) {
    if (currentSearchMatchColor == color) return;
    currentSearchMatchColor = color;
    currentSearchMatchPaint.setColor(color);
    if (highlightCurrentSearchMatch) {
      view.invalidate();
    }
  }

  boolean goToNextSearchMatch() {
    return goToSearchMatch(true);
  }

  boolean goToPrevSearchMatch() {
    return goToSearchMatch(false);
  }

  boolean selectNextSearchMatch() {
    return selectSearchMatch(true);
  }

  boolean selectPrevSearchMatch() {
    return selectSearchMatch(false);
  }

  boolean selectNextSearchMatchInclusive() {
    return selectSearchMatchInclusive(true);
  }

  boolean selectPrevSearchMatchInclusive() {
    return selectSearchMatchInclusive(false);
  }

  public boolean goToNextSearchMatch(SodiumEditorView view) {
    return goToSearchMatch(true);
  }

  public boolean goToPrevSearchMatch(SodiumEditorView view) {
    return goToSearchMatch(false);
  }

  public boolean selectNextSearchMatch(SodiumEditorView view) {
    return selectSearchMatch(true);
  }

  public boolean selectPrevSearchMatch(SodiumEditorView view) {
    return selectSearchMatch(false);
  }

  public boolean selectNextSearchMatchInclusive(SodiumEditorView view) {
    return selectSearchMatchInclusive(true);
  }

  public boolean selectPrevSearchMatchInclusive(SodiumEditorView view) {
    return selectSearchMatchInclusive(false);
  }

  public boolean selectSearchMatchAtCursorOrNext(SodiumEditorView view) {
    return selectSearchMatchAtCursorOrNext();
  }

  static final class SearchMatch {
    int line;
    int start;
    int end;

    SearchMatch(int line, int start, int end) {
      this.line = line;
      this.start = start;
      this.end = end;
    }
  }
}
