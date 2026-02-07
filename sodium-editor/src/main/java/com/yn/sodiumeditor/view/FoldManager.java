package com.yn.sodiumeditor.view;

import android.animation.ValueAnimator;
import android.graphics.Paint;
import android.graphics.RectF;
import java.util.ArrayList;
import java.util.HashMap;

final class FoldManager {
  private final SodiumEditorView view;
  final Paint foldPlaceholderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  final Paint foldMarkerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  final Paint foldRipplePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  float foldMarkerGutterWidth = 0f;
  float foldMarkerTextScale = 1f;
  float foldMarkerSpacing = 0f;
  float foldMarkerEdgePadding = 4f;
  ValueAnimator foldRippleAnimator;
  int foldRippleLine = -1;
  float foldRippleRadius = 0f;
  float foldRippleAlpha = 0f;
  float foldRippleMaxRadius = 0f;
  final RectF foldPlaceholderRect = new RectF();
  float foldPlaceholderCorner = 3f;
  float foldPlaceholderPadX = 3f;
  float foldPlaceholderPadY = 2f;
  final HashMap<Integer, FoldRange> foldRanges = new HashMap<>();
  final ArrayList<int[]> foldIntervals = new ArrayList<>();
  boolean foldIntervalsDirty = true;
  boolean isCodeFoldingEnabled = false;

  FoldManager(SodiumEditorView view) {
    this.view = view;
  }

  void setCodeFoldingEnabled(boolean enabled) {
    if (isCodeFoldingEnabled == enabled) return;
    isCodeFoldingEnabled = enabled;
    view.invalidateLineNumberCacheForFold();
    if (!enabled) {
      foldRanges.clear();
      clearFoldRipple();
    }
    view.markIndentGuideIntervalsDirtyForFold();
    foldIntervalsDirty = true;
    view.invalidate();
  }

  void setFoldPlaceholderColor(int color) {
    foldPlaceholderPaint.setColor(color);
    if (isCodeFoldingEnabled) view.invalidate();
  }

  void setFoldMarkerColor(int color) {
    foldMarkerPaint.setColor(color);
    if (isCodeFoldingEnabled) view.invalidate();
  }

  void setFoldMarkerTextSize(float size) {
    float base = view.paint.getTextSize();
    if (base <= 0f) return;
    foldMarkerTextScale = size / base;
    foldMarkerPaint.setTextSize(base * foldMarkerTextScale);
    view.requestLayout();
    if (view.isWordWrapEnabled) view.invalidateWrapMetricsForFold(true);
    view.invalidate();
  }

  String buildFoldDisplayLine(String line, FoldRange range, int[] placeholderBoundsOut) {
    return view.buildFoldDisplayLineInternal(line, range, placeholderBoundsOut);
  }

  String getFoldMarkerForLine(int line, String lineText) {
    if (!isCodeFoldingEnabled) return null;
    FoldRange range = foldRanges.get(line);
    if (range != null) return range.collapsed ? ">" : "v";
    if (lineText == null) return null;
    boolean isIndentCandidate =
        view.isIndentationBlocksEnabledForFold() && isIndentFoldCandidate(lineText);
    if (!isIndentCandidate && !shouldShowFoldMarkerFromLine(lineText)) return null;
    FoldRange found = findFoldRangeForLine(line);
    if (found == null) return null;
    foldRanges.put(found.startLine, found);
    if (found.isIndentFold) view.markIndentGuideIntervalsDirtyForFold();
    foldIntervalsDirty = true;
    return "v";
  }

  boolean isIndentFoldCandidate(String line) {
    if (line == null || line.isEmpty()) return false;
    String trimmed = rstripWhitespace(line);
    return !trimmed.isEmpty() && trimmed.endsWith(":");
  }

  void removeIndentFolds() {
    foldRanges.entrySet().removeIf(e -> e.getValue().isIndentFold);
    foldIntervalsDirty = true;
  }

  void markIntervalsDirty() {
    foldIntervalsDirty = true;
  }

  void clearAllFolds() {
    foldRanges.clear();
    foldIntervalsDirty = true;
  }

  boolean hasFoldRanges() {
    return !foldRanges.isEmpty();
  }

  java.lang.Iterable<FoldRange> getFoldRanges() {
    return foldRanges.values();
  }

  private static String rstripWhitespace(String text) {
    if (text == null || text.isEmpty()) return "";
    int end = text.length();
    while (end > 0) {
      char c = text.charAt(end - 1);
      if (c != ' ' && c != '\t') break;
      end--;
    }
    return (end <= 0) ? "" : text.substring(0, end);
  }

  void startFoldMarkerRipple(int line) {
    if (!isCodeFoldingEnabled || !view.lineNumberManager.isShowLineNumbers()) return;
    foldRippleLine = line;
    float gutterWidth = foldMarkerGutterWidth;
    if (gutterWidth <= 0f) {
      gutterWidth =
          foldMarkerPaint.measureText("v") + foldMarkerSpacing + foldMarkerEdgePadding;
    }
    foldRippleMaxRadius =
        Math.max(view.lineHeight * 0.35f, Math.min(view.lineHeight * 0.6f, gutterWidth * 0.6f));
    if (foldRippleAnimator != null) foldRippleAnimator.cancel();
    foldRippleAnimator = ValueAnimator.ofFloat(0f, 1f);
    foldRippleAnimator.setDuration(220);
    foldRippleAnimator.addUpdateListener(
        a -> {
          float t = (float) a.getAnimatedValue();
          foldRippleRadius = foldRippleMaxRadius * t;
          foldRippleAlpha = 0.35f * (1f - t);
          view.invalidate();
        });
    foldRippleAnimator.addListener(
        new android.animation.AnimatorListenerAdapter() {
          @Override
          public void onAnimationEnd(android.animation.Animator animation) {
            foldRippleAlpha = 0f;
            foldRippleRadius = 0f;
            foldRippleLine = -1;
            view.invalidate();
          }

          @Override
          public void onAnimationCancel(android.animation.Animator animation) {
            foldRippleAlpha = 0f;
            foldRippleRadius = 0f;
            foldRippleLine = -1;
            view.invalidate();
          }
        });
    foldRippleAnimator.start();
  }

  void clearFoldRipple() {
    if (foldRippleAnimator != null) {
      foldRippleAnimator.cancel();
      foldRippleAnimator = null;
    }
    foldRippleAlpha = 0f;
    foldRippleRadius = 0f;
    foldRippleLine = -1;
  }

  boolean shouldShowFoldMarkerFromLine(String line) {
    if (line == null || line.isEmpty()) return false;
    int blockStart = line.indexOf("/*");
    if (blockStart >= 0) {
      int blockEnd = line.indexOf("*/", blockStart + 2);
      if (blockEnd < 0) return true;
    }

    int idx = line.indexOf('{');
    if (idx >= 0) return true;
    idx = line.indexOf('(');
    if (idx >= 0) return true;
    idx = line.indexOf('[');
    return idx >= 0;
  }

  void drawFoldMarkersForVisibleLines(
      android.graphics.Canvas canvas, int firstVisibleIndex, int lastVisibleIndex) {
    if (!isCodeFoldingEnabled) return;

    float markerX =
        view.isRtl
            ? (view.getGutterStartX()
                + view.lineNumberManager.getGutterSeparatorWidth()
                + foldMarkerEdgePadding)
            : (view.lineNumberManager.getSeparatorLeft(view.getGutterStartX())
                - foldMarkerEdgePadding);

    for (int v = firstVisibleIndex; v <= lastVisibleIndex; v++) {
      int line = view.mapVisibleIndexToGlobal(v);
      String marker = getFoldMarkerForLine(line, view.getLineTextForRender(line));
      if (marker == null) continue;
      float y =
          Math.round(
              v * view.lineHeight - view.scrollManager.scrollY + view.lineHeight - view.paint.descent());
      if (line == foldRippleLine && foldRippleAlpha > 0f) {
        int base = foldMarkerPaint.getColor();
        int alpha = Math.min(255, Math.max(0, (int) (255f * foldRippleAlpha)));
        foldRipplePaint.setColor((base & 0x00FFFFFF) | (alpha << 24));
        float centerY =
            Math.round(v * view.lineHeight - view.scrollManager.scrollY + view.lineHeight * 0.5f);
        canvas.drawCircle(markerX, centerY, foldRippleRadius, foldRipplePaint);
      }
      canvas.drawText(marker, markerX, y, foldMarkerPaint);
    }
  }

  void drawFoldedLine(android.graphics.Canvas canvas, String line, int globalLine) {
    FoldRange range = foldRanges.get(globalLine);
    if (range == null) return;
    view.drawFoldedLineForFoldManager(canvas, line, globalLine, range);
  }

  boolean isFoldPlaceholderHit(int globalLine, String line, float localX) {
    if (!isCodeFoldingEnabled) return false;
    FoldRange range = foldRanges.get(globalLine);
    if (range == null || !range.collapsed) return false;
    return view.isFoldPlaceholderHitForFoldManager(globalLine, line, localX, range);
  }

  boolean isLineHiddenByFold(int line) {
    if (!isCodeFoldingEnabled || foldRanges.isEmpty()) return false;
    rebuildFoldIntervalsIfNeeded();
    for (int[] interval : foldIntervals) {
      if (line < interval[0]) return false;
      if (line <= interval[1]) return true;
    }
    return false;
  }

  FoldRange getFoldRangeAtStart(int line) {
    if (!isCodeFoldingEnabled) return null;
    FoldRange range = foldRanges.get(line);
    return (range != null && range.collapsed) ? range : null;
  }

  void rebuildFoldIntervalsIfNeeded() {
    if (!foldIntervalsDirty) return;
    foldIntervalsDirty = false;
    foldIntervals.clear();
    if (!isCodeFoldingEnabled || foldRanges.isEmpty()) return;

    for (FoldRange range : foldRanges.values()) {
      if (!range.collapsed) continue;
      int start = range.startLine + 1;
      int end = range.endLine;
      if (end < start) continue;
      foldIntervals.add(new int[] {start, end});
    }
    if (foldIntervals.isEmpty()) return;

    java.util.Collections.sort(foldIntervals, (a, b) -> Integer.compare(a[0], b[0]));
    int write = 0;
    int[] cur = foldIntervals.get(0);
    for (int i = 1; i < foldIntervals.size(); i++) {
      int[] nxt = foldIntervals.get(i);
      if (nxt[0] <= cur[1] + 1) {
        cur[1] = Math.max(cur[1], nxt[1]);
      } else {
        foldIntervals.set(write++, cur);
        cur = nxt;
      }
    }
    foldIntervals.set(write++, cur);
    while (foldIntervals.size() > write) foldIntervals.remove(foldIntervals.size() - 1);
  }

  int getHiddenLineCount(int totalLines) {
    if (!isCodeFoldingEnabled || foldRanges.isEmpty()) return 0;
    rebuildFoldIntervalsIfNeeded();
    int hidden = 0;
    for (int[] interval : foldIntervals) {
      int s = interval[0];
      int e = Math.min(interval[1], totalLines - 1);
      if (e >= s) hidden += (e - s + 1);
    }
    return hidden;
  }

  int mapVisibleIndexToGlobal(int visibleIndex, int totalLines) {
    if (!isCodeFoldingEnabled) return visibleIndex;
    int visibleTotal = Math.max(1, totalLines - getHiddenLineCount(totalLines));
    int clamped = Math.max(0, Math.min(visibleIndex, Math.max(0, visibleTotal - 1)));
    int global = clamped;
    rebuildFoldIntervalsIfNeeded();
    for (int[] interval : foldIntervals) {
      if (global < interval[0]) break;
      global += (interval[1] - interval[0] + 1);
    }
    return Math.max(0, Math.min(global, totalLines - 1));
  }

  int getVisibleIndexForGlobalLine(int globalLine) {
    if (!isCodeFoldingEnabled) return globalLine;
    rebuildFoldIntervalsIfNeeded();
    int visible = globalLine;
    for (int[] interval : foldIntervals) {
      if (globalLine < interval[0]) break;
      if (globalLine <= interval[1]) return Math.max(0, interval[0] - 1);
      visible -= (interval[1] - interval[0] + 1);
    }
    return Math.max(0, visible);
  }

  boolean toggleFoldAtLine(int line) {
    if (!isCodeFoldingEnabled) return false;
    FoldRange existing = foldRanges.get(line);
    if (existing != null) {
      existing.collapsed = !existing.collapsed;
      foldIntervalsDirty = true;
      view.invalidate();
      return true;
    }

    FoldRange created = findFoldRangeForLine(line);
    if (created == null) return false;
    created.collapsed = true;
    foldRanges.put(created.startLine, created);
    if (created.isIndentFold) view.markIndentGuideIntervalsDirtyForFold();
    foldIntervalsDirty = true;
    view.invalidate();
    return true;
  }

  FoldRange findFoldRangeForLine(int line) {
    if (!isCodeFoldingEnabled) return null;
    if (line < 0) return null;

    java.io.RandomAccessFile raf = null;
    try {
      if (view.getSourceFileForFold() != null && view.isIndexReadyForFold()) {
        raf = new java.io.RandomAccessFile(view.getSourceFileForFold(), "r");
      }

      String ln = getLineTextForFoldScan(line, raf);
      if (ln == null) return null;

      SodiumEditorView.HighlightLineState startState = view.getLineStateAtStartForFold(line);
      boolean inBlockComment =
          startState.inBlockComment && view.isBlockCommentsEnabledForFold();
      int stringState = startState.stringState;
      if (!view.isBlockCommentsEnabledForFold()) inBlockComment = false;
      if (!view.isMultiLineStringsEnabledForFold()
          && stringState != view.getStringStateTripleForFold()) stringState = 0;
      if (!view.isBacktickStringsEnabledForFold()
          && stringState == view.getStringStateBacktickForFold()) stringState = 0;
      if (!view.isTripleQuoteStringsEnabledForFold()
          && stringState == view.getStringStateTripleForFold()) stringState = 0;

      if (inBlockComment || stringState != 0) return null;

      if (view.isIndentationBlocksEnabledForFold() && isIndentFoldCandidate(ln)) {
        FoldRange indentRange = findIndentFoldRangeForLine(line, raf);
        if (indentRange != null) return indentRange;
      }

      int scanIndex = 0;
      while (true) {
        FoldToken token = findFoldTokenInLine(ln, scanIndex);
        if (token == null) return null;

        if (token.isBlockComment) {
          int endLine = findBlockCommentEndLine(line, token.index, raf);
          if (endLine > line) {
            return new FoldRange(line, endLine, token.index, '/', '/', true, false);
          }
          scanIndex = token.index + 2;
          continue;
        }

        FoldMatch match = findMatchingBracketFrom(line, token.index, token.openChar, raf);
        if (match != null && match.endLine > line) {
          return new FoldRange(
              line, match.endLine, token.index, token.openChar, match.closeChar, false, false);
        }

        scanIndex = token.index + 1;
        if (scanIndex >= ln.length()) return null;
      }
    } catch (Exception ignored) {
      return null;
    } finally {
      if (raf != null) {
        try {
          raf.close();
        } catch (Exception ignored) {
        }
      }
    }
  }

  String getLineTextForFoldScan(int line, @androidx.annotation.Nullable java.io.RandomAccessFile raf) {
    return view.getLineTextForFoldScanInternal(line, raf);
  }

  FoldRange findIndentFoldRangeForLine(int line, @androidx.annotation.Nullable java.io.RandomAccessFile raf) {
    if (!view.isIndentationBlocksEnabledForFold()) return null;
    String ln = getLineTextForFoldScan(line, raf);
    if (ln == null) return null;
    String trimmed = rstripWhitespace(ln);
    if (trimmed.isEmpty() || !trimmed.endsWith(":")) return null;

    int baseIndent = view.getIndentWidthForFold(ln);
    int totalLines = view.getLinesCount();
    if (totalLines <= 0)
      totalLines =
          Math.max(line + 1, view.getWindowStartLineForFold() + view.getLinesWindowSizeForFold());

    int endLine = -1;
    int scanEnd = Math.min(totalLines, line + view.getIndentFoldScanLimitForFold());
    for (int i = line + 1; i < scanEnd; i++) {
      String next = getLineTextForFoldScan(i, raf);
      if (next == null) break;
      String nextTrimmed = rstripWhitespace(next);
      if (nextTrimmed.isEmpty()) continue;
      int indent = view.getIndentWidthForFold(next);
      if (indent <= baseIndent) {
        endLine = i - 1;
        break;
      }
      endLine = i;
    }

    if (endLine > line) {
      int openIdx = Math.max(0, trimmed.length() - 1);
      return new FoldRange(line, endLine, openIdx, ':', ':', false, true);
    }
    return null;
  }

  FoldToken findFoldTokenInLine(String line, int startIndex) {
    if (line == null || line.isEmpty()) return null;
    int len = line.length();
    int i = Math.max(0, startIndex);
    boolean inLineComment = false;
    boolean inBlockComment = false;
    int stringState = 0;

    while (i < len) {
      if (inLineComment) break;

      if (inBlockComment) {
        int end = view.findBlockCommentEndForFold(line, i);
        if (end < 0) return null;
        i = end + 2;
        inBlockComment = false;
        continue;
      }

      if (stringState != 0) {
        SodiumEditorView.StringEndResult endResult =
            view.findStringEndForStateForFold(line, i, stringState);
        if (!endResult.found) return null;
        i = endResult.endIndex;
        stringState = 0;
        continue;
      }

      if (view.isLineCommentStartForFold(line, i)) {
        inLineComment = true;
        break;
      }

      if (view.isBlockCommentsEnabledForFold()
          && i + 1 < len
          && line.charAt(i) == '/'
          && line.charAt(i + 1) == '*'
          && !view.isTokenEscapedForFold(line, i)) {
        return new FoldToken(i, true, '/');
      }

      if (view.isTripleQuoteStartForFold(line, i) && !view.isEscapedForFold(line, i)) {
        int end = view.findTripleQuoteEndForFold(line, i + 3);
        if (end < 0) return null;
        i = end + 3;
        continue;
      }

      char c = line.charAt(i);
      if (view.isStringDelimiterForFold(c) && !view.isEscapedForFold(line, i)) {
        int end = view.findStringEndForFold(line, i + 1, c);
        if (end < 0) return null;
        i = end + 1;
        continue;
      }

      if (view.isOpeningBracketForFold(c) && !view.isEscapedForFold(line, i)) {
        if (c == '{') return new FoldToken(i, false, c);
      }
      i++;
    }
    for (int j = Math.max(0, startIndex); j < len; j++) {
      char c = line.charAt(j);
      if ((c == '(' || c == '[') && !view.isEscapedForFold(line, j)) {
        return new FoldToken(j, false, c);
      }
    }
    return null;
  }

  int findBlockCommentEndLine(
      int startLine, int startIndex, @androidx.annotation.Nullable java.io.RandomAccessFile raf) {
    int totalLines = view.getLinesCount();
    if (totalLines <= 0)
      totalLines =
          Math.max(
              startLine + 1,
              view.getWindowStartLineForFold() + view.getLinesWindowSizeForFold());

    for (int line = startLine; line < totalLines; line++) {
      String text = getLineTextForFoldScan(line, raf);
      if (text == null) break;
      int from = (line == startLine) ? Math.min(startIndex + 2, text.length()) : 0;
      int end = view.findBlockCommentEndForFold(text, from);
      if (end >= 0) return line;
    }
    return -1;
  }

  FoldMatch findMatchingBracketFrom(
      int startLine, int startIndex, char openChar, @androidx.annotation.Nullable java.io.RandomAccessFile raf) {
    int totalLines = view.getLinesCount();
    if (totalLines <= 0)
      totalLines =
          Math.max(
              startLine + 1,
              view.getWindowStartLineForFold() + view.getLinesWindowSizeForFold());

    SodiumEditorView.HighlightLineState startState = view.getLineStateAtStartForFold(startLine);
    boolean inBlockComment =
        startState.inBlockComment && view.isBlockCommentsEnabledForFold();
    int stringState = startState.stringState;
    if (!view.isBlockCommentsEnabledForFold()) inBlockComment = false;
    if (!view.isMultiLineStringsEnabledForFold()
        && stringState != view.getStringStateTripleForFold()) stringState = 0;
    if (!view.isBacktickStringsEnabledForFold()
        && stringState == view.getStringStateBacktickForFold()) stringState = 0;
    if (!view.isTripleQuoteStringsEnabledForFold()
        && stringState == view.getStringStateTripleForFold()) stringState = 0;

    if (inBlockComment || stringState != 0) return null;

    int depth = 1;
    char closeChar = view.matchingBracketForFold(openChar);

    for (int line = startLine; line < totalLines; line++) {
      String text = getLineTextForFoldScan(line, raf);
      if (text == null) break;
      int len = text.length();
      int i = (line == startLine) ? Math.min(startIndex + 1, len) : 0;
      boolean inLineComment = false;

      while (i < len) {
        if (inLineComment) break;

        if (inBlockComment) {
          int end = view.findBlockCommentEndForFold(text, i);
          if (end < 0) break;
          i = end + 2;
          inBlockComment = false;
          continue;
        }

        if (stringState != 0) {
          SodiumEditorView.StringEndResult endResult =
              view.findStringEndForStateForFold(text, i, stringState);
          if (!endResult.found) return null;
          i = endResult.endIndex;
          stringState = 0;
          continue;
        }

        if (view.isLineCommentStartForFold(text, i)) {
          inLineComment = true;
          continue;
        }

        if (view.isBlockCommentsEnabledForFold()
            && i + 1 < len
            && text.charAt(i) == '/'
            && text.charAt(i + 1) == '*'
            && !view.isTokenEscapedForFold(text, i)) {
          inBlockComment = true;
          i += 2;
          continue;
        }

        if (view.isTripleQuoteStartForFold(text, i) && !view.isEscapedForFold(text, i)) {
          int end = view.findTripleQuoteEndForFold(text, i + 3);
          if (end < 0) return null;
          i = end + 3;
          continue;
        }

        char c = text.charAt(i);
        if (view.isStringDelimiterForFold(c) && !view.isEscapedForFold(text, i)) {
          int end = view.findStringEndForFold(text, i + 1, c);
          if (end < 0) return null;
          i = end + 1;
          continue;
        }

        if (c == openChar && !view.isEscapedForFold(text, i)) {
          depth++;
          i++;
          continue;
        }
        if (c == closeChar && !view.isEscapedForFold(text, i)) {
          depth--;
          if (depth == 0) return new FoldMatch(line, closeChar);
        }
        i++;
      }
    }
    return null;
  }

  static final class FoldRange {
    final int startLine;
    final int endLine;
    final int openCharIndex;
    final char openChar;
    final char closeChar;
    final boolean isBlockComment;
    final boolean isIndentFold;
    boolean collapsed;

    FoldRange(
        int startLine,
        int endLine,
        int openCharIndex,
        char openChar,
        char closeChar,
        boolean isBlockComment,
        boolean isIndentFold) {
      this.startLine = startLine;
      this.endLine = endLine;
      this.openCharIndex = openCharIndex;
      this.openChar = openChar;
      this.closeChar = closeChar;
      this.isBlockComment = isBlockComment;
      this.isIndentFold = isIndentFold;
      this.collapsed = false;
    }
  }

  static final class FoldToken {
    final int index;
    final boolean isBlockComment;
    final char openChar;

    FoldToken(int index, boolean isBlockComment, char openChar) {
      this.index = index;
      this.isBlockComment = isBlockComment;
      this.openChar = openChar;
    }
  }

  static final class FoldMatch {
    final int endLine;
    final char closeChar;

    FoldMatch(int endLine, char closeChar) {
      this.endLine = endLine;
      this.closeChar = closeChar;
    }
  }
}
