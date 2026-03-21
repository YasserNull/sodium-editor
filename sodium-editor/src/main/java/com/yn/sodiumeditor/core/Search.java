package com.yn.sodiumeditor.core;
import com.yn.sodiumeditor.SodiumEditor;
import android.graphics.Canvas;
import android.graphics.Paint;
import androidx.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Manages search functionality for the SodiumEditor.
 * Handles search query, highlighting, and navigation.
 */
public class Search {

  private final SodiumEditor editor;

  // Search state
  public String searchQuery = "";
  public boolean searchUseRegex = false;
  public boolean searchCaseSensitive = false;
  public boolean searchWrap = true;
  public boolean searchHighlightEnabled = true;
  public int searchHighlightColor = 0x66FFD54F;
  public Pattern searchPattern = null;
  public String searchCacheKey = null;
  public int searchCacheEditVersion = -1;
  public final HashMap<Integer, int[]> searchMatchCache = new HashMap<>();
  public final Paint searchHighlightPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

  // Current search match highlighting
  public boolean mHighlightCurrentSearchMatch = false;
  public int mCurrentSearchMatchColor = 0x9933B5E5;
  public final Paint mCurrentSearchMatchPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

  public Search(SodiumEditor editor) {
    this.editor = editor;
    initPaint();
  }

  private void initPaint() {
    searchHighlightPaint.setStyle(Paint.Style.FILL);
    searchHighlightPaint.setColor(searchHighlightColor);
    mCurrentSearchMatchPaint.setStyle(Paint.Style.FILL);
    mCurrentSearchMatchPaint.setColor(mCurrentSearchMatchColor);
  }

  /**
   * Checks if search is active.
   */
  public boolean isSearchActive() {
    if (searchQuery == null || searchQuery.isEmpty()) return false;
    if (searchUseRegex) return searchPattern != null;
    return true;
  }

  /**
   * Clears search match cache.
   */
  public void clearSearchMatchCache() {
    searchMatchCache.clear();
    searchCacheEditVersion = -1;
    searchCacheKey = null;
  }

  /**
   * Gets the search cache key.
   */
  public String getSearchCacheKey() {
    return searchQuery
        + "|"
        + (searchUseRegex ? "r" : "t")
        + "|"
        + (searchCaseSensitive ? "c" : "i");
  }

  /**
   * Gets search match spans for a line.
   */
  public int[] getSearchMatchSpansForLine(String line, int globalLine) {
    if (!searchHighlightEnabled || !isSearchActive() || line == null || line.isEmpty())
      return new int[0];

    int version = editor.editOperators.editVersion.get();
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
      String needle =
          searchCaseSensitive ? searchQuery : searchQuery.toLowerCase(Locale.ROOT);
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

  /**
   * Draws search highlights for a line.
   */
  public void drawSearchHighlightsForLine(
      Canvas canvas, String line, int globalLine, float top, float bottom) {
    int[] spans = getSearchMatchSpansForLine(line, globalLine);
    if (spans.length == 0) return;
    for (int i = 0; i + 1 < spans.length; i += 2) {
      int start = spans[i];
      int end = spans[i + 1];
      if (end <= start) continue;
      float left = editor.measureText(line, start, globalLine);
      float right = editor.measureText(line, end, globalLine);

      boolean isCurrentMatch =
          mHighlightCurrentSearchMatch
              && !editor.selection.hasSelection
              && globalLine == editor.cursor.cursorLine
              && editor.cursor.cursorChar >= start
              && editor.cursor.cursorChar <= end;

      Paint paintToUse = isCurrentMatch ? mCurrentSearchMatchPaint : searchHighlightPaint;
      canvas.drawRect(left, top, right, bottom, paintToUse);
    }
  }

  /**
   * Draws search highlights for a segment.
   */
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
      float left = editor.measureTextWithVisualSpaces(line, segStart, s, editor.textRender.paint);
      float right = left + editor.measureTextWithVisualSpaces(line, s, e, editor.textRender.paint);

      boolean isCurrentMatch =
          mHighlightCurrentSearchMatch
              && !editor.selection.hasSelection
              && globalLine == editor.cursor.cursorLine
              && editor.cursor.cursorChar >= start
              && editor.cursor.cursorChar <= end;

      Paint paintToUse = isCurrentMatch ? mCurrentSearchMatchPaint : searchHighlightPaint;
      canvas.drawRect(left, top, right, bottom, paintToUse);
    }
  }

  /**
   * Goes to the next or previous search match.
   */
  public boolean goToSearchMatch(boolean forward) {
    if (!isSearchActive()) return false;
    int total = editor.getLinesCount();
    if (total <= 0) return false;

    int startLine = Math.max(0, editor.cursor.cursorLine);
    int startChar = Math.max(0, editor.cursor.cursorChar);

    SearchMatch match =
        forward
            ? findNextSearchMatchFrom(startLine, startChar)
            : findPrevSearchMatchFrom(startLine, startChar);
    if (match == null) return false;

    editor.fileIO.ensureLineInWindow(match.line, true);
    editor.cursor.setCursorPosition(match.line, match.start);
    return true;
  }

  /**
   * Finds the next search match from the given position.
   */
  public SearchMatch findNextSearchMatchFrom(int line, int charIndex) {
    int total = editor.getLinesCount();
    if (total <= 0) return null;

    HashMap<Integer, String> direct = new HashMap<>();

    SearchMatch m = findNextSearchMatchInRange(line, total - 1, charIndex + 1, null, direct);
    if (m != null) return m;
    if (searchWrap && line > 0) {
      return findNextSearchMatchInRange(0, line, 0, charIndex, direct);
    }
    return null;
  }

  /**
   * Finds the previous search match from the given position.
   */
  public SearchMatch findPrevSearchMatchFrom(int line, int charIndex) {
    int total = editor.getLinesCount();
    if (total <= 0) return null;

    HashMap<Integer, String> direct = new HashMap<>();

    SearchMatch m = findPrevSearchMatchInRange(line, 0, charIndex - 1, null, direct);
    if (m != null) return m;
    if (searchWrap && line < total - 1) {
      return findPrevSearchMatchInRange(total - 1, line, Integer.MAX_VALUE, charIndex - 1, direct);
    }
    return null;
  }

  /**
   * Finds the next search match in a range.
   */
  public SearchMatch findNextSearchMatchInRange(
      int startLine,
      int endLine,
      int startCharExclusive,
      @Nullable Integer maxStartInclusive,
      HashMap<Integer, String> direct) {
    int step = (startLine <= endLine) ? 1 : -1;
    int line = startLine;
    int total = editor.getLinesCount();
    int chunkSize = 200;
    while (true) {
      if (line < 0 || line >= total) break;
      if (line < editor.textRender.windowStartLine || line >= editor.textRender.windowStartLine + editor.textRender.linesWindow.size()) {
        if (editor.fileIO.isIndexReady && editor.fileIO.sourceFile != null && editor.fileIO.sourceFile.exists()) {
          int rangeStart = Math.max(0, Math.min(line, line + (step * (chunkSize - 1))));
          int rangeEnd = Math.min(total - 1, Math.max(line, line + (step * (chunkSize - 1))));
          editor.fileIO.populateDirectLinesForRange(rangeStart, rangeEnd, direct);
        }
      }
      String lineText = editor.getLineTextForRenderWithDirect(line, direct);
      if (lineText == null) lineText = "";
      int from =
          (line == startLine) ? Math.max(0, Math.min(startCharExclusive, lineText.length())) : 0;
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

  /**
   * Finds the previous search match in a range.
   */
  public SearchMatch findPrevSearchMatchInRange(
      int startLine,
      int endLine,
      int startCharExclusive,
      @Nullable Integer minStartInclusive,
      HashMap<Integer, String> direct) {
    int step = (startLine >= endLine) ? -1 : 1;
    int line = startLine;
    int total = editor.getLinesCount();
    int chunkSize = 200;
    while (true) {
      if (line < 0 || line >= total) break;
      if (line < editor.textRender.windowStartLine || line >= editor.textRender.windowStartLine + editor.textRender.linesWindow.size()) {
        if (editor.fileIO.isIndexReady && editor.fileIO.sourceFile != null && editor.fileIO.sourceFile.exists()) {
          int rangeStart = Math.max(0, Math.min(line, line + (step * (chunkSize - 1))));
          int rangeEnd = Math.min(total - 1, Math.max(line, line + (step * (chunkSize - 1))));
          editor.fileIO.populateDirectLinesForRange(rangeStart, rangeEnd, direct);
        }
      }
      String lineText = editor.getLineTextForRenderWithDirect(line, direct);
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

  /**
   * Finds a match forward in a line.
   */
  public SearchMatch findMatchForwardInLine(
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
    String needle =
        searchCaseSensitive ? searchQuery : searchQuery.toLowerCase(Locale.ROOT);
    if (needle.isEmpty()) return null;
    int idx = haystack.indexOf(needle, fromIndex);
    if (idx < 0) return null;
    if (maxStartInclusive != null && idx > maxStartInclusive) return null;
    return new SearchMatch(-1, idx, idx + needle.length());
  }

  /**
   * Finds a match backward in a line.
   */
  public SearchMatch findMatchBackwardInLine(
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
    String needle =
        searchCaseSensitive ? searchQuery : searchQuery.toLowerCase(Locale.ROOT);
    if (needle.isEmpty()) return null;
    int idx = haystack.lastIndexOf(needle, Math.min(fromIndex, haystack.length()));
    if (idx < 0) return null;
    if (minStartInclusive != null && idx < minStartInclusive) return null;
    return new SearchMatch(-1, idx, idx + needle.length());
  }

  /**
   * Sets the search query.
   */
  public void setSearchQuery(String query, boolean useRegex, boolean caseSensitive, boolean wrapAround) {
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

    if (searchUseRegex && !searchQuery.isEmpty()) {
      int flags = Pattern.MULTILINE;
      if (!searchCaseSensitive) flags |= (Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
      try {
        searchPattern = Pattern.compile(searchQuery, flags);
      } catch (Exception e) {
        searchPattern = null;
      }
    } else {
      searchPattern = null;
    }

    clearSearchMatchCache();
    editor.invalidate();
  }

  /**
   * Sets search highlight enabled.
   */
  public void setSearchHighlightEnabled(boolean enabled) {
    if (searchHighlightEnabled == enabled) return;
    searchHighlightEnabled = enabled;
    editor.invalidate();
  }

  /**
   * Sets search highlight color.
   */
  public void setSearchHighlightColor(int color) {
    searchHighlightColor = color;
    searchHighlightPaint.setColor(color);
    editor.invalidate();
  }

  /**
   * Sets current search match highlight enabled.
   */
  public void setCurrentSearchMatchHighlightEnabled(boolean enabled) {
    if (mHighlightCurrentSearchMatch == enabled) return;
    mHighlightCurrentSearchMatch = enabled;
    editor.invalidate();
  }

  /**
   * Sets current search match highlight color.
   */
  public void setCurrentSearchMatchHighlightColor(int color) {
    mCurrentSearchMatchColor = color;
    mCurrentSearchMatchPaint.setColor(color);
    editor.invalidate();
  }

  /**
   * Goes to next search match.
   */
  public boolean goToNextSearchMatch() {
    return goToSearchMatch(true);
  }

  /**
   * Goes to previous search match.
   */
  public boolean goToPreviousSearchMatch() {
    return goToSearchMatch(false);
  }

  /**
   * Selects a search match.
   */
  public boolean selectSearchMatch(boolean forward) {
    if (!isSearchActive()) return false;
    int total = editor.getLinesCount();
    if (total <= 0) return false;

    int startLine = Math.max(0, editor.cursor.cursorLine);
    int startChar = Math.max(0, editor.cursor.cursorChar);

    SearchMatch match =
        forward
            ? findNextSearchMatchFrom(startLine, startChar)
            : findPrevSearchMatchFrom(startLine, startChar);
    if (match == null) return false;

    editor.fileIO.ensureLineInWindow(match.line, true);
    editor.selection.setSelectionInternal(match.line, match.start, match.line, match.end);
    editor.setCursorPositionNoClear(match.line, match.end);
    return true;
  }

  /**
   * Selects a search match inclusively.
   */
  public boolean selectSearchMatchInclusive(boolean forward) {
    if (!isSearchActive()) return false;
    int total = editor.getLinesCount();
    if (total <= 0) return false;

    int startLine = Math.max(0, editor.cursor.cursorLine);
    int startChar = Math.max(0, editor.cursor.cursorChar);
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

    editor.fileIO.ensureLineInWindow(match.line, true);
    editor.selection.setSelectionInternal(match.line, match.start, match.line, match.end);
    editor.setCursorPositionNoClear(match.line, match.end);
    return true;
  }

  /**
   * Selects search match at cursor or next.
   */
  public boolean selectSearchMatchAtCursorOrNext() {
    SearchMatch atCursor = findSearchMatchAtCursor();
    if (atCursor != null) {
      editor.fileIO.ensureLineInWindow(atCursor.line, true);
      editor.selection.setSelectionInternal(atCursor.line, atCursor.start, atCursor.line, atCursor.end);
      editor.setCursorPositionNoClear(atCursor.line, atCursor.end);
      return true;
    }
    return selectSearchMatchInclusive(true);
  }

  /**
   * Finds search match at cursor.
   */
  @Nullable
  public SearchMatch findSearchMatchAtCursor() {
    if (!isSearchActive()) return null;
    int line = Math.max(0, editor.cursor.cursorLine);
    String lineText = editor.getLineTextForRender(line);
    if (lineText == null) lineText = "";
    if (lineText.isEmpty()) return null;

    if (searchUseRegex) {
      if (searchPattern == null) return null;
      try {
        Matcher matcher = searchPattern.matcher(lineText);
        while (matcher.find()) {
          int s = matcher.start();
          int e = matcher.end();
          if (s <= editor.cursor.cursorChar && editor.cursor.cursorChar < e) {
            return new SearchMatch(line, s, e);
          }
        }
      } catch (Exception ignored) {
        return null;
      }
      return null;
    }

    String needle = searchQuery == null ? "" : searchQuery;
    if (needle.isEmpty()) return null;
    String haystack = lineText;
    if (!searchCaseSensitive) {
      haystack = haystack.toLowerCase(Locale.ROOT);
      needle = needle.toLowerCase(Locale.ROOT);
    }
    int idx = 0;
    while (true) {
      idx = haystack.indexOf(needle, idx);
      if (idx < 0) return null;
      int end = idx + needle.length();
      if (idx <= editor.cursor.cursorChar && editor.cursor.cursorChar < end) {
        return new SearchMatch(line, idx, end);
      }
      idx = idx + 1;
    }
  }

  /**
   * Selects all search matches.
   */
  public boolean selectAllSearchMatches() {
    if (!isSearchActive()) return false;
    int total = editor.getLinesCount();
    if (total <= 0) return false;

    int startLine = Math.max(0, editor.cursor.cursorLine);
    int startChar = Math.max(0, editor.cursor.cursorChar);

    SearchMatch first = findNextSearchMatchFrom(startLine, startChar);
    if (first == null) return false;

    SearchMatch last = first;
    SearchMatch current = first;
    int count = 1;
    int maxMatches = 1000; // Limit to avoid performance issues

    while (count < maxMatches) {
      SearchMatch next = findNextSearchMatchFrom(current.line, current.end);
      if (next == null || (next.line == first.line && next.start == first.start)) break;
      last = next;
      current = next;
      count++;
    }

    editor.selection.selStartLine = first.line;
    editor.selection.selStartChar = first.start;
    editor.selection.selEndLine = last.line;
    editor.selection.selEndChar = last.end;
    editor.selection.hasSelection = true;
    editor.selection.isSelectAllActive = false;
    editor.selection.isEntireFileSelected = false;
    editor.cursor.cursorLine = last.line;
    editor.cursor.cursorChar = last.end;
    editor.invalidate();
    return true;
  }

  /**
   * Search match class.
   */
  public static class SearchMatch {
    public int line;
    public int start;
    public int end;

    public SearchMatch(int line, int start, int end) {
      this.line = line;
      this.start = start;
      this.end = end;
    }
  }
}
