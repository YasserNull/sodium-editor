package com.yn.sodiumeditor.view;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class TextRender {
  private final SodiumEditorView view;

  TextRender(SodiumEditorView view) {
    this.view = view;
  }

  void onDraw(Canvas canvas) {
    view.superOnDraw(canvas);
    drawEditorBackground(canvas);
    if (view.scrollManager.stretchOverscrollEnabled
        && (view.scrollManager.stretchX != 0f || view.scrollManager.stretchY != 0f)) {
      float sx = 1f + (view.scrollManager.stretchX * 0.12f * view.scrollManager.stretchOverscrollStrength);
      float sy = 1f + (view.scrollManager.stretchY * 0.12f * view.scrollManager.stretchOverscrollStrength);
      float pivotX =
          (view.scrollManager.stretchDirX < 0)
              ? 0f
              : (view.scrollManager.stretchDirX > 0 ? view.getWidth() : view.getWidth() * 0.5f);
      float pivotY =
          (view.scrollManager.stretchDirY < 0)
              ? 0f
              : (view.scrollManager.stretchDirY > 0 ? view.getHeight() : view.getHeight() * 0.5f);
      canvas.save();
      canvas.scale(sx, sy, pivotX, pivotY);
      drawContent(canvas);
      canvas.restore();
    } else {
      drawContent(canvas);
    }
    view.scrollManager.drawScrollBar(canvas);
  }

  void superOnDraw(Canvas canvas) {
    view.superOnDraw(canvas);
  }

  void invalidateLineGlobal(int globalLine) {
    if (view.wordWrapManager.isWordWrapEnabled) {
      view.invalidate();
      return;
    }
    int idx = view.foldManager.isCodeFoldingEnabled ? view.getVisibleIndexForGlobalLine(globalLine) : globalLine;
    float top = (idx * view.lineHeight) - view.scrollManager.scrollY;
    view.invalidate(0, (int) Math.floor(top), view.getWidth(), (int) Math.ceil(top + view.lineHeight));
  }

  boolean isHeavyDrawSuppressed() {
    return false;
  }

  public boolean isWhitespaceAtX(String line, int globalLine, float x) {
    if (line == null || line.isEmpty()) return true;
    if (x <= 0f) return Character.isWhitespace(line.charAt(0));

    List<HighlightManager.HighlightSpan> spans = view.highlightManager.highlightCache.get(globalLine);
    if (spans == null) {
      spans = view.highlightManager.calculateSpansForLine(line, globalLine);
      view.highlightManager.highlightCache.put(globalLine, spans);
    }

    final int len = line.length();
    float currentX = 0f;
    final float eps = 0.25f;

    int pos = 0;
    if (spans != null && !spans.isEmpty()) {
      for (HighlightManager.HighlightSpan span : spans) {
        if (pos >= len) break;
        if (span.end <= pos) continue;
        if (span.start > pos) {
          for (int i = pos; i < Math.min(span.start, len); i++) {
            float adv = view.whitespaceGuideManager.measureTextWithVisualSpaces(view, line, i, i + 1, view.paint);
            if (x >= currentX - eps && x <= currentX + adv + eps) {
              return Character.isWhitespace(line.charAt(i));
            }
            currentX += adv;
          }
        }
        int start = Math.max(pos, span.start);
        int end = Math.min(len, span.end);
        for (int i = start; i < end; i++) {
          float adv = view.whitespaceGuideManager.measureTextWithVisualSpaces(view, line, i, i + 1, view.paint);
          if (x >= currentX - eps && x <= currentX + adv + eps) {
            return Character.isWhitespace(line.charAt(i));
          }
          currentX += adv;
        }
        pos = Math.max(pos, end);
      }
    }

    if (pos < len) {
      for (int i = pos; i < len; i++) {
        float adv = view.whitespaceGuideManager.measureTextWithVisualSpaces(view, line, i, i + 1, view.paint);
        if (x >= currentX - eps && x <= currentX + adv + eps) {
          return Character.isWhitespace(line.charAt(i));
        }
        currentX += adv;
      }
    }

    return true;
  }

  public void drawFoldMarkersForVisibleLines(
      Canvas canvas, int firstVisibleIndex, int lastVisibleIndex) {
    view.foldManager.drawFoldMarkersForVisibleLines(canvas, firstVisibleIndex, lastVisibleIndex);
  }

  public void drawDeleteAnimationForSegment(
      Canvas canvas, String line, int globalLine, int segStart, int segEnd, float y) {
    if (!view.charAnimationManager.isEnabled()) return;
    if (globalLine != view.charAnimationManager.getDelAnimLine()
        || view.charAnimationManager.getDelAnimText() == null
        || view.charAnimationManager.getDelAnimText().isEmpty()
        || view.charAnimationManager.getDelAnimAlpha() <= 0f) return;
    if (line == null) line = "";
    int at = Math.max(0, Math.min(view.charAnimationManager.getDelAnimAtChar(), line.length()));
    if (at < segStart || at > segEnd) return;
    float x = view.whitespaceGuideManager.measureTextWithVisualSpaces(view, line, segStart, at, view.paint);
    Paint ghostPaint = (view.charAnimationManager.getDelAnimPaint() != null) ? view.charAnimationManager.getDelAnimPaint() : view.paint;
    view.charAnimationManager.getTempPaint().set(ghostPaint);
    view.charAnimationManager.getTempPaint().setUnderlineText(false);
    int baseAlpha = ghostPaint.getAlpha();
    view.charAnimationManager.getTempPaint().setAlpha((int) (baseAlpha * Math.max(0f, Math.min(1f, view.charAnimationManager.getDelAnimAlpha()))));
    canvas.drawText(view.charAnimationManager.getDelAnimText(), x, y, view.charAnimationManager.getTempPaint());
  }

  void getVisibleCharRangeForLine(String line, int globalLine, int[] out) {
    if (line == null || out == null || out.length < 2) return;
    int len = view.getLogicalLineLength(globalLine, line);
    if (len <= 0) {
      out[0] = 0;
      out[1] = 0;
      return;
    }
    if (len > view.highlightManager.maxSyntaxLineLength) {
      getVisibleCharRangeForLineFast(line, globalLine, len, out);
      return;
    }
    if (view.isStableGlyphPositionsEnabled) {
      out[0] = 0;
      out[1] = len;
      return;
    }
    float viewLeft = view.lineNumberManager.getContentViewLeft(view.isRtl);
    float viewRight = view.lineNumberManager.getContentViewRight(view.getWidth(), view.isRtl);
    float leftX = viewLeft + view.getEffectiveScrollX() - view.getTextStartX();
    float rightX = viewRight + view.getEffectiveScrollX() - view.getTextStartX();

    int start = getCharIndexForX(line, leftX, globalLine);
    int end = getCharIndexForX(line, rightX, globalLine);
    if (end < start) {
      int t = start;
      start = end;
      end = t;
    }

    int pad = view.visibleCharPadding;
    start = Math.max(0, start - pad);
    end = Math.min(len, end + pad);
    out[0] = start;
    out[1] = end;
  }

  public void getVisibleCharRangeForLineFast(
      String line, int globalLine, int lineLength, int[] out) {
    int len = Math.max(0, lineLength);
    if (len <= 0) {
      out[0] = 0;
      out[1] = 0;
      return;
    }
    float avg = view.highlightManager.getAverageCharWidthForLine(line, globalLine);
    if (avg <= 0f) {
      out[0] = 0;
      out[1] = Math.min(len, Math.max(0, view.prefetchCols));
      return;
    }
    float viewLeft = view.lineNumberManager.getContentViewLeft(view.isRtl);
    float viewRight = view.lineNumberManager.getContentViewRight(view.getWidth(), view.isRtl);
    float leftX = viewLeft + view.getEffectiveScrollX() - view.getTextStartX();
    float rightX = viewRight + view.getEffectiveScrollX() - view.getTextStartX();
    if (view.isRtl) {
      float w = avg * len;
      float baseX = view.getTextAreaWidth() - w;
      float l = leftX - baseX;
      float r = rightX - baseX;
      leftX = w - l;
      rightX = w - r;
    }
    int start = (int) Math.floor(leftX / avg);
    int end = (int) Math.ceil(rightX / avg);
    if (end < start) {
      int t = start;
      start = end;
      end = t;
    }
    int pad = view.visibleCharPadding + Math.max(0, view.prefetchCols);
    start = Math.max(0, start - pad);
    end = Math.min(len, end + pad);
    out[0] = start;
    out[1] = end;
  }

  public int getCharIndexForX(String text, float x, int globalLine) {
    if (text == null || text.isEmpty()) return 0;
    if (view.isRtl) {
      float baseX = view.getRtlLineBaseX(text, globalLine);
      x -= baseX;
      float w =
          view.highlightManager.measureHighlightedSegmentWidth(
              text, globalLine, 0, view.getLogicalLineLength(globalLine, text));
      x = w - x;
    }
    if (x <= 0f) return 0;

    int len = view.getLogicalLineLength(globalLine, text);
    if (len > view.highlightManager.maxSyntaxLineLength) {
      float avg = view.highlightManager.getAverageCharWidthForLine(text, globalLine);
      if (avg <= 0f) return 0;
      int idx = (int) Math.round(x / avg);
      return Math.max(0, Math.min(idx, len));
    }
    int textLen = text.length();
    if (view.getVisualSpaceScale() == 1) {
      int count = view.paint.breakText(text, true, x, null);
      if (count <= 0) return 0;
      if (count >= textLen) return textLen;

      float wPrev = (count > 1) ? view.paint.measureText(text, 0, count - 1) : 0f;
      float wCount = view.paint.measureText(text, 0, count);
      float mid = wPrev + (wCount - wPrev) * 0.5f;
      return (x < mid) ? (count - 1) : count;
    }

    float[] widths = view.whitespaceGuideManager.ensureMeasureWidthBuffer(textLen);
    view.paint.getTextWidths(text, 0, textLen, widths);
    float current = 0f;
    for (int i = 0; i < textLen; i++) {
      float adv = view.whitespaceGuideManager.getCharAdvanceWidth(text.charAt(i), widths[i], view.paint, view.wordWrapManager.DEFAULT_TAB_SIZE_SPACES);
      float mid = current + adv * 0.5f;
      if (x < mid) return i;
      if (x < current + adv) return i + 1;
      current += adv;
    }
    return textLen;
  }

  public void computeWidthForLine(int globalIndex, String line) {
    String safe = (line == null) ? "" : line;
    float w;
    int logicalLen = view.getLogicalLineLength(globalIndex, safe);
    if (logicalLen > view.highlightManager.maxSyntaxLineLength) {
      w = view.highlightManager.getAverageCharWidthForLine(safe, globalIndex) * logicalLen;
    } else {
      w = view.whitespaceGuideManager.measureTextWithVisualSpaces(view, safe, 0, safe.length(), view.paint);
    }
    synchronized (view.lineWidthCache) {
      view.lineWidthCache.put(globalIndex, w);
    }
  }

  public float getWidthForLine(int globalIndex, String line) {
    synchronized (view.lineWidthCache) {
      Float v = view.lineWidthCache.get(globalIndex);
      if (v != null) return v;
    }
    String safe = (line == null) ? "" : line;
    float w;
    int logicalLen = view.getLogicalLineLength(globalIndex, safe);
    if (logicalLen > view.highlightManager.maxSyntaxLineLength) {
      w = view.highlightManager.getAverageCharWidthForLine(safe, globalIndex) * logicalLen;
    } else {
      w = view.whitespaceGuideManager.measureTextWithVisualSpaces(view, safe, 0, safe.length(), view.paint);
    }
    synchronized (view.lineWidthCache) {
      view.lineWidthCache.put(globalIndex, w);
    }
    return w;
  }

  public String getLineTextForRender(int line) {
    return getLineTextForRenderWithDirect(line, null);
  }

  @Nullable
  public String getLineTextForRenderWithDirect(int line, @Nullable Map<Integer, String> direct) {
    if (line < 0) return null;
    if (direct != null) {
      String cached = direct.get(line);
      if (cached != null) return cached;
    }
    String mod = view.modifiedLines.get(line);
    if (mod != null) return mod;
    if (line >= view.windowStartLine && line < view.windowStartLine + view.linesWindow.size()) {
      String text = getLineFromWindowLocal(line - view.windowStartLine);
      return (text != null) ? text : "";
    }
    if (view.fileManager.getSourceFile() != null && view.fileManager.isIndexReady()) {
      long offset;
      synchronized (view.fileManager.lineOffsetsLock) {
        if (line < 0 || line >= view.fileManager.getLineOffsets().length) return null;
        offset = view.fileManager.getLineOffsets()[line];
      }
      try (java.io.RandomAccessFile raf = new java.io.RandomAccessFile(view.fileManager.getSourceFile(), "r")) {
        return view.fileManager.readLineUtf8AtByte(raf, offset);
      } catch (Exception ignored) {
        return null;
      }
    }
    return null;
  }

  public void populateDirectLinesForRange(int startLine, int endLine, Map<Integer, String> direct) {
    if (direct == null) return;
    int s = Math.max(0, Math.min(startLine, endLine));
    int e = Math.max(startLine, endLine);
    for (int line = s; line <= e; line++) {
      if (direct.containsKey(line)) continue;
      String text = getLineTextForRender(line);
      if (text == null) text = "";
      direct.put(line, text);
    }
  }

  int getGlobalLineForY(float y) {
    int idx = Math.max(0, (int) (y / view.lineHeight));
    if (view.wordWrapManager.isWordWrapEnabled) {
      return view.wordWrapManager.getVisualPositionForIndex(view, idx).line;
    }
    return view.mapVisibleIndexToGlobal(idx);
  }

  int getVisualIndexForLineAndChar(int line, int ch) {
    if (!view.wordWrapManager.isWrapMetricsUsableForLine(view, line)) {
      if (view.foldManager.isCodeFoldingEnabled) return view.getVisibleIndexForGlobalLine(line);
      return Math.max(0, line);
    }
    int totalLines = view.wordWrapManager.wrapLinePrefix.length - 1;
    int safeLine = Math.max(0, Math.min(line, Math.max(0, totalLines - 1)));
    String text = getLineTextForRender(safeLine);
    int[] starts = view.wordWrapManager.getWrapStartsForLine(view, safeLine, text);
    int seg = view.wordWrapManager.getWrapSegmentIndexForChar(starts, Math.max(0, Math.min(ch, text.length())));
    return view.wordWrapManager.wrapLinePrefix[safeLine] + seg;
  }

  public int getVisibleLineCount() {
    int total = getLinesCount();
    if (total <= 0) total = view.windowStartLine + view.linesWindow.size();
    int visible = Math.max(1, total - view.foldManager.getHiddenLineCount(total));
    return visible;
  }

  public int mapVisibleIndexToGlobal(int visibleIndex) {
    int total = getLinesCount();
    if (total <= 0) total = view.windowStartLine + view.linesWindow.size();
    return view.foldManager.mapVisibleIndexToGlobal(visibleIndex, total);
  }

  public int getVisibleIndexForGlobalLine(int globalLine) {
    return view.foldManager.getVisibleIndexForGlobalLine(globalLine);
  }

  public int getLinesCount() {
    if (view.fileManager.isFileCleared()) {
      return Math.max(1, view.windowStartLine + view.linesWindow.size());
    }
    int windowCount = view.windowStartLine + view.linesWindow.size();
    if (view.fileManager.isIndexReady() && view.fileManager.getLineOffsets().length > 0) {
      boolean hasEdits;
      synchronized (view.modifiedLines) {
        hasEdits = !view.modifiedLines.isEmpty();
      }
      if (!hasEdits && view.undoRedo.getLineCountDelta() == 0) {
        return view.fileManager.getLineOffsets().length;
      }
      int count = view.fileManager.getLineOffsets().length + view.undoRedo.getLineCountDelta();
      if (count < 1) count = 1;
      return Math.max(count, windowCount);
    }
    if (view.fileManager.isEof()) return view.windowStartLine + view.linesWindow.size();
    if (!view.linesWindow.isEmpty()) return view.windowStartLine + view.linesWindow.size();
    return -1;
  }

  public void updateLocalLine(int localIdx, String text) {
    if (localIdx >= 0 && localIdx < view.linesWindow.size()) {
      view.linesWindow.set(localIdx, text);
      view.wordWrapManager.onLineContentChanged(view, view.windowStartLine + localIdx, text);
      view.clearStreamedLineInfo(view.windowStartLine + localIdx);
    }
  }

  public String getLineFromWindowLocal(int localIdx) {
    if (localIdx < 0 || localIdx >= view.linesWindow.size()) return null;
    return view.linesWindow.get(localIdx);
  }

  public boolean isWordChar(char c) {
    return Character.isLetterOrDigit(c) || c == '_' || c == '$';
  }

  public int[] computeWordBounds(String line, int pos) {
    pos = Math.max(0, Math.min(pos, line.length()));
    if (line.length() == 0) return new int[] {0, 0};
    if (pos == line.length()) pos = Math.max(0, pos - 1);
    if (Character.isWhitespace(line.charAt(pos))) {
      int i = pos;
      while (i < line.length() && Character.isWhitespace(line.charAt(i))) i++;
      if (i >= line.length()) {
        i = pos - 1;
        while (i >= 0 && Character.isWhitespace(line.charAt(i))) i--;
      }
      if (i < 0) return new int[] {pos, pos};
      pos = i;
    }
    int start = pos;
    int end = pos;
    while (start > 0 && !Character.isWhitespace(line.charAt(start - 1))) start--;
    while (end < line.length() - 1 && !Character.isWhitespace(line.charAt(end + 1))) end++;
    return new int[] {start, end + 1};
  }

  public int[] computeWordBoundsSmart(String line, int pos) {
    if (line == null || line.isEmpty()) return new int[] {0, 0};
    int len = line.length();
    int idx = Math.max(0, Math.min(pos, len - 1));
    if (!isWordChar(line.charAt(idx))) {
      if (idx > 0 && isWordChar(line.charAt(idx - 1))) {
        idx = idx - 1;
      } else if (idx + 1 < len && isWordChar(line.charAt(idx + 1))) {
        idx = idx + 1;
      } else {
        return new int[] {idx, idx};
      }
    }
    int start = idx;
    int end = idx;
    while (start > 0 && isWordChar(line.charAt(start - 1))) start--;
    while (end < len - 1 && isWordChar(line.charAt(end + 1))) end++;
    return new int[] {start, end + 1};
  }

  public boolean applySmartDoubleTapSelection(int line, int charIndex, String lineText) {
    if (lineText == null) return false;
    int[] bounds = computeWordBoundsSmart(lineText, charIndex);
    ArrayList<SodiumEditorView.TextRange> candidates =
        buildDoubleTapCandidates(lineText, charIndex, bounds[0], bounds[1]);
    if (candidates.isEmpty()) return false;

    boolean sameAnchor =
        line == view.lastDoubleTapLine
            && bounds[0] == view.lastDoubleTapWordStart
            && bounds[1] == view.lastDoubleTapWordEnd;
    int currentIdx = findSelectionCandidateIndex(line, candidates);
    int nextIdx;
    if (sameAnchor) {
      if (currentIdx >= 0) {
        nextIdx = Math.min(currentIdx + 1, candidates.size() - 1);
      } else {
        nextIdx = Math.min(view.lastDoubleTapStage + 1, candidates.size() - 1);
      }
    } else {
      nextIdx = 0;
    }

    SodiumEditorView.TextRange pick = candidates.get(nextIdx);
    view.selectionManager.setSelection(line, pick.start, line, pick.end, true);
    view.selectionManager.setSelectAllState(false, false);
    view.cursorManager.setLineAndChar(line, view.selectionManager.selEndChar);
    view.lastDoubleTapLine = line;
    view.lastDoubleTapWordStart = bounds[0];
    view.lastDoubleTapWordEnd = bounds[1];
    view.lastDoubleTapStage = nextIdx;
    return true;
  }

  public ArrayList<SodiumEditorView.TextRange> buildDoubleTapCandidates(String line, int charIndex, int wStart, int wEnd) {
    ArrayList<SodiumEditorView.TextRange> out = new ArrayList<>(6);
    if (line == null) return out;
    int len = line.length();
    addSelectionCandidate(out, wStart, wEnd, len);

    SodiumEditorView.TextRange quote = findEnclosingQuoteRange(line, charIndex);
    if (quote != null) {
      addSelectionCandidate(out, quote.start + 1, quote.end, len);
      addSelectionCandidate(out, quote.start, quote.end + 1, len);
    }

    SodiumEditorView.TextRange bracket = findEnclosingBracketRange(line, charIndex);
    if (bracket != null) {
      addSelectionCandidate(out, bracket.start + 1, bracket.end, len);
      addSelectionCandidate(out, bracket.start, bracket.end + 1, len);
    }
    return out;
  }

  public void addSelectionCandidate(List<SodiumEditorView.TextRange> out, int start, int end, int lineLen) {
    if (out == null) return;
    int s = Math.max(0, Math.min(start, lineLen));
    int e = Math.max(0, Math.min(end, lineLen));
    if (e <= s) return;
    for (SodiumEditorView.TextRange r : out) {
      if (r.start == s && r.end == e) return;
    }
    out.add(new SodiumEditorView.TextRange(s, e));
  }

  public int findSelectionCandidateIndex(int line, List<SodiumEditorView.TextRange> candidates) {
    if (!view.selectionManager.hasSelection() || candidates == null || candidates.isEmpty()) return -1;
    int sL = view.selectionManager.selStartLine;
    int sC = view.selectionManager.selStartChar;
    int eL = view.selectionManager.selEndLine;
    int eC = view.selectionManager.selEndChar;
    if (view.comparePos(sL, sC, eL, eC) > 0) {
      sL = view.selectionManager.selEndLine;
      sC = view.selectionManager.selEndChar;
      eL = view.selectionManager.selStartLine;
      eC = view.selectionManager.selStartChar;
    }
    if (sL != line || eL != line) return -1;
    for (int i = 0; i < candidates.size(); i++) {
      SodiumEditorView.TextRange r = candidates.get(i);
      if (r.start == sC && r.end == eC) return i;
    }
    return -1;
  }

  public boolean isQuoteChar(char c) {
    return c == '"' || c == '\'' || c == '`';
  }

  @Nullable
  public SodiumEditorView.TextRange findEnclosingQuoteRange(String line, int index) {
    if (line == null || line.isEmpty()) return null;
    int len = line.length();
    if (index < 0 || index > len) return null;
    ArrayList<SodiumEditorView.TextRange> ranges = new ArrayList<>();
    char current = 0;
    int start = -1;
    for (int i = 0; i < len; i++) {
      char c = line.charAt(i);
      if (current == 0) {
        if (isQuoteChar(c) && !HighlightManager.isEscaped(line, i)) {
          current = c;
          start = i;
        }
      } else {
        if (c == current && !HighlightManager.isEscaped(line, i)) {
          ranges.add(new SodiumEditorView.TextRange(start, i));
          current = 0;
          start = -1;
        }
      }
    }
    SodiumEditorView.TextRange best = null;
    int bestLen = Integer.MAX_VALUE;
    for (SodiumEditorView.TextRange r : ranges) {
      if (index >= r.start && index <= r.end) {
        int span = r.end - r.start;
        if (span < bestLen) {
          bestLen = span;
          best = r;
        }
      }
    }
    return best;
  }

  @Nullable
  public SodiumEditorView.TextRange findEnclosingBracketRange(String line, int index) {
    if (line == null || line.isEmpty()) return null;
    int len = line.length();
    if (index < 0 || index > len) return null;
    ArrayList<SodiumEditorView.TextRange> ranges = new ArrayList<>();
    int[] stackIdx = new int[Math.max(8, len / 4)];
    char[] stackType = new char[stackIdx.length];
    int sp = 0;
    char currentQuote = 0;
    for (int i = 0; i < len; i++) {
      char c = line.charAt(i);
      if (currentQuote != 0) {
        if (c == currentQuote && !HighlightManager.isEscaped(line, i)) {
          currentQuote = 0;
        }
        continue;
      }
      if (isQuoteChar(c) && !HighlightManager.isEscaped(line, i)) {
        currentQuote = c;
        continue;
      }
      if (c == '(' || c == '[' || c == '{') {
        if (sp >= stackIdx.length) {
          int newSize = stackIdx.length * 2;
          int[] newIdx = new int[newSize];
          char[] newType = new char[newSize];
          System.arraycopy(stackIdx, 0, newIdx, 0, stackIdx.length);
          System.arraycopy(stackType, 0, newType, 0, stackType.length);
          stackIdx = newIdx;
          stackType = newType;
        }
        stackIdx[sp] = i;
        stackType[sp] = c;
        sp++;
        continue;
      }
      if (c == ')' || c == ']' || c == '}') {
        char want = (c == ')') ? '(' : (c == ']') ? '[' : '{';
        if (sp > 0 && stackType[sp - 1] == want) {
          int start = stackIdx[sp - 1];
          sp--;
          ranges.add(new SodiumEditorView.TextRange(start, i));
        }
      }
    }
    SodiumEditorView.TextRange best = null;
    int bestLen = Integer.MAX_VALUE;
    for (SodiumEditorView.TextRange r : ranges) {
      if (index >= r.start && index <= r.end) {
        int span = r.end - r.start;
        if (span < bestLen) {
          bestLen = span;
          best = r;
        }
      }
    }
    return best;
  }

  private static int getFirstNonSpaceIndex(String line) {
    for (int i = 0; i < line.length(); i++) {
      if (!Character.isWhitespace(line.charAt(i))) return i;
    }
    return -1;
  }

  private void drawEditorBackground(Canvas canvas) {
    if (view.hasEditorBackgroundColor) {
      canvas.drawColor(view.editorBackgroundColor);
    }
    if (view.editorBackgroundBitmap != null && !view.editorBackgroundBitmap.isRecycled()) {
      view.editorBackgroundDst.set(0, 0, view.getWidth(), view.getHeight());
      canvas.drawBitmap(view.editorBackgroundBitmap, null, view.editorBackgroundDst, null);
    }
  }

  void drawContent(Canvas canvas) {
    if (view.wordWrapManager.isWordWrapEnabled) {
      drawContentWrapped(canvas);
      return;
    }
    final boolean drawDecorations = view.zoomManager.shouldDrawDecorations();

    int firstVisibleIndex = (int) (view.scrollManager.scrollY / view.lineHeight);
    if (firstVisibleIndex < 0) firstVisibleIndex = 0;
    int lastVisibleIndex = firstVisibleIndex + (int) Math.ceil(view.getHeight() / view.lineHeight) + 5;

    int firstVisibleLine = firstVisibleIndex;
    int lastVisibleLine = lastVisibleIndex;
    if (view.foldManager.isCodeFoldingEnabled) {
      int visibleCount = view.getVisibleLineCount();
      if (visibleCount <= 0) visibleCount = 1;
      firstVisibleIndex = Math.max(0, Math.min(firstVisibleIndex, visibleCount - 1));
      lastVisibleIndex = Math.max(firstVisibleIndex, Math.min(lastVisibleIndex, visibleCount - 1));
      firstVisibleLine = view.mapVisibleIndexToGlobal(firstVisibleIndex);
      lastVisibleLine = view.mapVisibleIndexToGlobal(lastVisibleIndex);
      view.drawBaseLine = firstVisibleIndex;
    } else {
      view.drawBaseLine = firstVisibleIndex;
    }

    float baseY = view.drawBaseLine * view.lineHeight;
    float translateY = -view.scrollManager.scrollY + baseY;
    if (view.isEof) {
      synchronized (view.linesWindow) {
        int lastDocLine = Math.max(0, view.windowStartLine + view.linesWindow.size() - 1);
        lastVisibleLine = Math.min(lastVisibleLine, lastDocLine);
      }
    }
    if (lastVisibleLine < firstVisibleLine) lastVisibleLine = firstVisibleLine;

    view.maybeKickWindowLoad(firstVisibleLine);
    view.maybeUpdateStreamedSlicesForVisibleRange(firstVisibleLine, lastVisibleLine);

    if (view.lineNumberManager.isShowLineNumbers()) {
      canvas.drawRect(
          view.getGutterStartX(),
          0,
          view.lineNumberManager.getGutterRight(view.getGutterStartX()),
          view.getHeight(),
          view.lineNumberManager.getGutterPaint());

      float separatorLeft;
      if (view.isRtl) {
        separatorLeft = view.getGutterStartX();
      } else {
        separatorLeft = view.lineNumberManager.getSeparatorLeft(view.getGutterStartX());
      }
      canvas.drawRect(
          separatorLeft,
          0,
          separatorLeft + view.lineNumberManager.getGutterSeparatorWidth(),
          view.getHeight(),
          view.lineNumberManager.getGutterSeparatorPaint());
    }

    if (view.lineNumberManager.isHighlightCurrentLineInGutter()
        && view.cursorManager.getLine() >= firstVisibleLine
        && view.cursorManager.getLine() <= lastVisibleLine
        && (!view.foldManager.isCodeFoldingEnabled || !view.foldManager.isLineHiddenByFold(view.cursorManager.getLine()))) {
      int drawIndex = view.foldManager.isCodeFoldingEnabled ? view.getVisibleIndexForGlobalLine(view.cursorManager.getLine()) : view.cursorManager.getLine();
      float top = Math.round(drawIndex * view.lineHeight - view.scrollManager.scrollY);
      float bottom = top + view.lineHeight;
      view.lineNumberManager.drawCurrentLineHighlightInGutter(canvas, top, bottom, view.highlightManager.currentLinePaint);
    }

    if (view.lineNumberManager.isShowLineNumbers()) {
      view.lineNumberManager.drawLineNumbersCachedUnwrapped(
          canvas, firstVisibleIndex, lastVisibleIndex, firstVisibleLine, lastVisibleLine);
      if (view.foldManager.isCodeFoldingEnabled && drawDecorations) {
        view.drawFoldMarkersForVisibleLines(canvas, firstVisibleIndex, lastVisibleIndex);
      }
    }

    canvas.save();
    if (view.isRtl) {
      canvas.clipRect(
          view.lineNumberManager.getContentClipLeft(view.isRtl),
          0,
          view.lineNumberManager.getContentClipRight(view.getWidth(), view.isRtl),
          view.getHeight());
    } else {
      canvas.clipRect(
          view.lineNumberManager.getContentClipLeft(false), 0, view.getWidth(), view.getHeight());
    }
    canvas.translate(view.getTextStartX() - view.getEffectiveScrollX(), translateY);
    if (view.zoomManager.isPinchVisualZoomActive()) {
      float pivotX = view.zoomManager.getPinchFocusX() - (view.getTextStartX() - view.getEffectiveScrollX());
      float pivotY = view.zoomManager.getPinchFocusY() - translateY;
      canvas.scale(view.zoomManager.getPinchVisualScale(), view.zoomManager.getPinchVisualScale(), pivotX, pivotY);
    }

    Paint selPaint = null;
    if (view.selectionManager.hasSelection()) {
      selPaint = view.selectionManager.getSelectionPaint();
    }

    HashMap<Integer, String> directLines = null;
    if (view.isIndexReady && view.sourceFile != null && view.sourceFile.exists()) {
      boolean needDirect =
          (firstVisibleLine < view.windowStartLine)
              || (firstVisibleLine >= view.windowStartLine + view.linesWindow.size())
              || (lastVisibleLine >= view.windowStartLine + view.linesWindow.size());

      if (needDirect) {
        view.directLinesTmp.clear();
        directLines = view.directLinesTmp;
        if (firstVisibleLine < view.windowStartLine) {
          populateDirectLinesForRange(
              firstVisibleLine, Math.min(lastVisibleLine, view.windowStartLine - 1), directLines);
        }
        int winEnd = view.windowStartLine + view.linesWindow.size() - 1;
        if (lastVisibleLine > winEnd) {
          populateDirectLinesForRange(
              Math.max(firstVisibleLine, winEnd + 1), lastVisibleLine, directLines);
        }
        if (directLines.isEmpty()
            && (firstVisibleLine < view.windowStartLine
                || firstVisibleLine >= view.windowStartLine + view.linesWindow.size())) {
          populateDirectLinesForRange(firstVisibleLine, lastVisibleLine, directLines);
        }
      }
    }

    BracketMatchManager.BracketMatch bracketMatch =
        view.bracketMatchManager.getMatch(firstVisibleLine, lastVisibleLine, directLines);

    int winEnd;
    synchronized (view.linesWindow) {
      winEnd = view.windowStartLine + view.linesWindow.size() - 1;
    }
    int prefetchForDraw = view.zoomManager.isZoomGestureActive() ? 0 : view.prefetchLines;
    int hlStart = Math.max(view.windowStartLine, Math.max(0, firstVisibleLine - prefetchForDraw));
    int hlEnd = Math.min(winEnd, lastVisibleLine + prefetchForDraw);
    view.maybeEnsureHighlightCacheForRange(hlStart, hlEnd, directLines);

    if (view.bracketGuideManager.isEnabled() && drawDecorations) {
      view.bracketGuideManager.ensureCacheForWindow(directLines);
    }

    if (view.foldManager.isCodeFoldingEnabled) {
      view.indentGuideManager.rebuildIntervalsIfNeeded();
      for (int v = firstVisibleIndex; v <= lastVisibleIndex; v++) {
        int globalLine = view.mapVisibleIndexToGlobal(v);
        String line = view.getLineTextForRenderWithDirect(globalLine, directLines);
        FoldManager.FoldRange foldRange = view.foldManager.getFoldRangeAtStart(globalLine);
        boolean isFoldStart = (foldRange != null);
        float lineBaseX = view.isRtl ? view.getRtlLineBaseX(line, globalLine) : 0f;
        float lineWidth =
            view.isRtl
                ? view.highlightManager.measureHighlightedSegmentWidth(
                    line, globalLine, 0, view.getLogicalLineLength(globalLine, line))
                : 0f;

        if (view.highlightManager.highlightCurrentLine && globalLine == view.cursorManager.getLine() && !view.selectionManager.hasSelection()) {
          float top = Math.round(view.scrollManager.getDrawLineTop(globalLine));
          float bottom = Math.round(view.scrollManager.getDrawLineBottom(globalLine));
          float viewLeft = view.lineNumberManager.getContentViewLeft(view.isRtl);
          float viewRight = view.lineNumberManager.getContentViewRight(view.getWidth(), view.isRtl);
          float left = viewLeft + view.getEffectiveScrollX() - view.getTextStartX();
          float right = viewRight + view.getEffectiveScrollX() - view.getTextStartX();
          canvas.drawRect(left, top, right, bottom, view.highlightManager.currentLinePaint);
        }

        if (view.selectionManager.hasSelection() && selPaint != null) {
          float top = Math.round(view.scrollManager.getDrawLineTop(globalLine));
          float bottom = Math.round(view.scrollManager.getDrawLineBottom(globalLine));
          float fullRight =
              Math.max(view.currentMaxWindowLineWidth, view.scrollManager.scrollX + (view.getWidth() - view.getTextStartX()));
          if (view.isRtl) {
            fullRight = lineBaseX + lineWidth;
          }

          if (view.selectionManager.isSelectAllActive()) {
            boolean lineExists =
                (view.isEof) ? (globalLine <= view.windowStartLine + view.linesWindow.size() - 1) : true;
            if (lineExists) {
              boolean roundTop = globalLine == view.selectionManager.selStartLine;
              boolean roundBottom = globalLine == view.selectionManager.selEndLine;
              float leftSel = view.isRtl ? lineBaseX : 0f;
              float rightSel = view.isRtl ? (lineBaseX + lineWidth) : fullRight;
              view.selectionManager.drawSelectionSegment(
                  canvas,
                  leftSel,
                  top,
                  rightSel,
                  bottom,
                  roundTop,
                  roundTop,
                  roundBottom,
                  roundBottom,
                  view.lineHeight, selPaint);
            }
          } else {
            int startLine, endLine, startChar, endChar;
            if (view.comparePos(view.selectionManager.selStartLine, view.selectionManager.selStartChar, view.selectionManager.selEndLine, view.selectionManager.selEndChar) <= 0) {
              startLine = view.selectionManager.selStartLine;
              startChar = view.selectionManager.selStartChar;
              endLine = view.selectionManager.selEndLine;
              endChar = view.selectionManager.selEndChar;
            } else {
              startLine = view.selectionManager.selEndLine;
              startChar = view.selectionManager.selEndChar;
              endLine = view.selectionManager.selStartLine;
              endChar = view.selectionManager.selStartChar;
            }

            if (globalLine >= startLine && globalLine <= endLine) {
              float left, right;
              if (view.isRtl) {
                float lineLeft = lineBaseX;
                float lineRight = lineBaseX + lineWidth;
                if (startLine == endLine) {
                  float x1 =
                      view.getCaretXForLine(
                          line, globalLine, Math.min(startChar, line.length()));
                  float x2 =
                      view.getCaretXForLine(
                          line, globalLine, Math.min(endChar, line.length()));
                  left = Math.min(x1, x2);
                  right = Math.max(x1, x2);
                } else if (globalLine == startLine) {
                  float x =
                      view.getCaretXForLine(
                          line, globalLine, Math.min(startChar, line.length()));
                  left = lineLeft;
                  right = x;
                } else if (globalLine == endLine) {
                  float x =
                      view.getCaretXForLine(
                          line, globalLine, Math.min(endChar, line.length()));
                  left = x;
                  right = lineRight;
                } else {
                  left = lineLeft;
                  right = lineRight;
                }
              } else {
                if (startLine == endLine) {
                  left = view.highlightManager.measureText(line, Math.min(startChar, line.length()), globalLine);
                  right = view.highlightManager.measureText(line, Math.min(endChar, line.length()), globalLine);
                } else {
                  if (globalLine == startLine) {
                    left = view.highlightManager.measureText(line, Math.min(startChar, line.length()), globalLine);
                    right = fullRight;
                  } else if (globalLine == endLine) {
                    left = 0;
                    right = view.highlightManager.measureText(line, Math.min(endChar, line.length()), globalLine);
                    if (line.length() == 0) right = fullRight;
                  } else {
                    left = 0;
                    right = fullRight;
                  }
                }
              }
              if (right > left) {
                boolean isStart = globalLine == startLine;
                boolean isEnd = globalLine == endLine;
                boolean roundTop = isStart;
                boolean roundBottom = isEnd;
                if (!isStart && !isEnd) {
                  roundTop = false;
                  roundBottom = false;
                } else if (isStart && !isEnd) {
                  roundBottom = false;
                } else if (!isStart && isEnd) {
                  roundTop = false;
                }
                view.selectionManager.drawSelectionSegment(
                    canvas,
                    left,
                    top,
                    right,
                    bottom,
                    roundTop,
                    roundTop,
                    roundBottom,
                    roundBottom,
                    view.lineHeight, selPaint);
              }
            }
          }
        }

        float y = Math.round(view.scrollManager.getDrawLineTop(globalLine) + view.lineHeight - view.paint.descent());
        view.paint.setUnderlineText(false);

        canvas.save();
        if (lineBaseX != 0f) canvas.translate(lineBaseX, 0f);

        float lineTop = Math.round(view.scrollManager.getDrawLineTop(globalLine));
        float lineBottom = Math.round(view.scrollManager.getDrawLineBottom(globalLine));

        view.highlightManager.drawColorCodeBackgrounds(canvas, line, globalLine, lineTop, lineBottom);

        if (isFoldStart) {
          if (view.bracketGuideManager.isEnabled() && drawDecorations) {
            List<BracketGuideManager.BracketGuideToken> guideTokens = view.bracketGuideManager.getTokensForLine(globalLine);
            view.bracketGuideManager.drawGuidesForLine(canvas, line, globalLine, guideTokens);
          }
          if (drawDecorations) {
            view.whitespaceGuideManager.drawWhitespaceGuidesForLine(view, canvas, line, globalLine, y);
            view.indentGuideManager.drawIndentGuidesForLine(canvas, line, globalLine);
          }
          view.drawFoldedLine(canvas, line, globalLine);
          canvas.restore();
          continue;
        }

        view.searchManager.drawSearchHighlightsForLine(canvas, line, globalLine, lineTop, lineBottom);
        view.highlightManager.drawHighlightedLine(canvas, line, globalLine, y);
        if (drawDecorations) {
          view.whitespaceGuideManager.drawWhitespaceGuidesForLine(view, canvas, line, globalLine, y);
          view.indentGuideManager.drawIndentGuidesForLine(canvas, line, globalLine);
        }

        view.autoSuggestionManager.drawAutoSuggestion(canvas, line, globalLine, y);

        if (view.bracketGuideManager.isEnabled() && drawDecorations) {
          List<BracketGuideManager.BracketGuideToken> guideTokens = view.bracketGuideManager.getTokensForLine(globalLine);
          view.bracketGuideManager.drawGuidesForLine(canvas, line, globalLine, guideTokens);
        }

        if (drawDecorations) {
          view.bracketMatchManager.drawMatchForLine(canvas, line, globalLine, bracketMatch);
        }
        canvas.restore();
      }
    } else {
      view.indentGuideManager.rebuildIntervalsIfNeeded();
      for (int globalLine = firstVisibleLine; globalLine <= lastVisibleLine; globalLine++) {
        String line = view.getLineTextForRenderWithDirect(globalLine, directLines);
        if (line == null) line = "";
        float lineBaseX = view.isRtl ? view.getRtlLineBaseX(line, globalLine) : 0f;
        float lineWidth =
            view.isRtl
                ? view.highlightManager.measureHighlightedSegmentWidth(
                    line, globalLine, 0, view.getLogicalLineLength(globalLine, line))
                : 0f;

        if (view.highlightManager.highlightCurrentLine && globalLine == view.cursorManager.getLine() && !view.selectionManager.hasSelection()) {
          float top = Math.round(view.scrollManager.getDrawLineTop(globalLine));
          float bottom = Math.round(view.scrollManager.getDrawLineBottom(globalLine));
          float viewLeft = view.lineNumberManager.getContentViewLeft(view.isRtl);
          float viewRight = view.lineNumberManager.getContentViewRight(view.getWidth(), view.isRtl);
          float left = viewLeft + view.getEffectiveScrollX() - view.getTextStartX();
          float right = viewRight + view.getEffectiveScrollX() - view.getTextStartX();
          canvas.drawRect(left, top, right, bottom, view.highlightManager.currentLinePaint);
        }

        if (view.selectionManager.hasSelection() && selPaint != null) {
          float top = Math.round(view.scrollManager.getDrawLineTop(globalLine));
          float bottom = Math.round(view.scrollManager.getDrawLineBottom(globalLine));
          float fullRight =
              Math.max(view.currentMaxWindowLineWidth, view.scrollManager.scrollX + (view.getWidth() - view.getTextStartX()));
          if (view.isRtl) {
            fullRight = lineBaseX + lineWidth;
          }

          if (view.selectionManager.isSelectAllActive()) {
            boolean lineExists =
                (view.isEof) ? (globalLine <= view.windowStartLine + view.linesWindow.size() - 1) : true;
            if (lineExists) {
              boolean roundTop = globalLine == view.selectionManager.selStartLine;
              boolean roundBottom = globalLine == view.selectionManager.selEndLine;
              float leftSel = view.isRtl ? lineBaseX : 0f;
              float rightSel = view.isRtl ? (lineBaseX + lineWidth) : fullRight;
              view.selectionManager.drawSelectionSegment(
                  canvas,
                  leftSel,
                  top,
                  rightSel,
                  bottom,
                  roundTop,
                  roundTop,
                  roundBottom,
                  roundBottom,
                  view.lineHeight, selPaint);
            }
          } else {
            int startLine, endLine, startChar, endChar;
            if (view.comparePos(view.selectionManager.selStartLine, view.selectionManager.selStartChar, view.selectionManager.selEndLine, view.selectionManager.selEndChar) <= 0) {
              startLine = view.selectionManager.selStartLine;
              startChar = view.selectionManager.selStartChar;
              endLine = view.selectionManager.selEndLine;
              endChar = view.selectionManager.selEndChar;
            } else {
              startLine = view.selectionManager.selEndLine;
              startChar = view.selectionManager.selEndChar;
              endLine = view.selectionManager.selStartLine;
              endChar = view.selectionManager.selStartChar;
            }

            if (globalLine >= startLine && globalLine <= endLine) {
              float left, right;
              if (view.isRtl) {
                float lineLeft = lineBaseX;
                float lineRight = lineBaseX + lineWidth;
                if (startLine == endLine) {
                  float x1 =
                      view.getCaretXForLine(
                          line, globalLine, Math.min(startChar, line.length()));
                  float x2 =
                      view.getCaretXForLine(
                          line, globalLine, Math.min(endChar, line.length()));
                  left = Math.min(x1, x2);
                  right = Math.max(x1, x2);
                } else if (globalLine == startLine) {
                  float x =
                      view.getCaretXForLine(
                          line, globalLine, Math.min(startChar, line.length()));
                  left = lineLeft;
                  right = x;
                } else if (globalLine == endLine) {
                  float x =
                      view.getCaretXForLine(
                          line, globalLine, Math.min(endChar, line.length()));
                  left = x;
                  right = lineRight;
                } else {
                  left = lineLeft;
                  right = lineRight;
                }
              } else {
                if (startLine == endLine) {
                  left = view.highlightManager.measureText(line, Math.min(startChar, line.length()), globalLine);
                  right = view.highlightManager.measureText(line, Math.min(endChar, line.length()), globalLine);
                } else {
                  if (globalLine == startLine) {
                    left = view.highlightManager.measureText(line, Math.min(startChar, line.length()), globalLine);
                    right = fullRight;
                  } else if (globalLine == endLine) {
                    left = 0;
                    right = view.highlightManager.measureText(line, Math.min(endChar, line.length()), globalLine);
                    if (line.length() == 0) right = fullRight;
                  } else {
                    left = 0;
                    right = fullRight;
                  }
                }
              }
              if (right > left) {
                boolean isStart = globalLine == startLine;
                boolean isEnd = globalLine == endLine;
                boolean roundTop = isStart;
                boolean roundBottom = isEnd;
                if (!isStart && !isEnd) {
                  roundTop = false;
                  roundBottom = false;
                } else if (isStart && !isEnd) {
                  roundBottom = false;
                } else if (!isStart && isEnd) {
                  roundTop = false;
                }
                view.selectionManager.drawSelectionSegment(
                    canvas,
                    left,
                    top,
                    right,
                    bottom,
                    roundTop,
                    roundTop,
                    roundBottom,
                    roundBottom,
                    view.lineHeight, selPaint);
              }
            }
          }
        }

        float y = Math.round(view.scrollManager.getDrawLineTop(globalLine) + view.lineHeight - view.paint.descent());
        view.paint.setUnderlineText(false);

        canvas.save();
        if (lineBaseX != 0f) canvas.translate(lineBaseX, 0f);

        float lineTop = Math.round(view.scrollManager.getDrawLineTop(globalLine));
        float lineBottom = Math.round(view.scrollManager.getDrawLineBottom(globalLine));

        view.highlightManager.drawColorCodeBackgrounds(canvas, line, globalLine, lineTop, lineBottom);
        view.searchManager.drawSearchHighlightsForLine(canvas, line, globalLine, lineTop, lineBottom);
        view.highlightManager.drawHighlightedLine(canvas, line, globalLine, y);
        if (drawDecorations) {
          view.whitespaceGuideManager.drawWhitespaceGuidesForLine(view, canvas, line, globalLine, y);
          view.indentGuideManager.drawIndentGuidesForLine(canvas, line, globalLine);
        }

        view.autoSuggestionManager.drawAutoSuggestion(canvas, line, globalLine, y);

        if (view.bracketGuideManager.isEnabled() && drawDecorations) {
          List<BracketGuideManager.BracketGuideToken> guideTokens = view.bracketGuideManager.getTokensForLine(globalLine);
          view.bracketGuideManager.drawGuidesForLine(canvas, line, globalLine, guideTokens);
        }

        if (drawDecorations) {
          view.bracketMatchManager.drawMatchForLine(canvas, line, globalLine, bracketMatch);
        }
        canvas.restore();
      }
    }
    if (view.isFocused()
        && !view.isReadOnly
        && !view.selectionManager.hasSelection()
        && view.cursorManager.getLine() >= firstVisibleLine
        && view.cursorManager.getLine() <= lastVisibleLine
        && (!view.foldManager.isCodeFoldingEnabled || !view.foldManager.isLineHiddenByFold(view.cursorManager.getLine()))) {
      String cursorLineText = view.getLineTextForRender(view.cursorManager.getLine());
      int safeChar = Math.min(view.cursorManager.getChar(), view.getLogicalLineLength(view.cursorManager.getLine(), cursorLineText));
      float cursorX = view.getCaretXForLine(cursorLineText, view.cursorManager.getLine(), safeChar);
      float cursorY = view.scrollManager.getDrawLineTop(view.cursorManager.getLine());
      view.cursorManager.drawCaret(canvas, cursorX, cursorY);
      float drawX = view.cursorAnimationManager.getCursorDrawX();
      float drawY = view.cursorAnimationManager.getCursorDrawY();
      view.handlesManager.drawCursorHandle(canvas, drawX, drawY, view.lineHeight);
    }

    if (view.selectionManager.hasSelection() && !view.isReadOnly) {
      if (view.selectionManager.selStartLine >= firstVisibleLine
          && view.selectionManager.selStartLine <= lastVisibleLine
          && (!view.foldManager.isCodeFoldingEnabled || !view.foldManager.isLineHiddenByFold(view.selectionManager.selStartLine))) {
        String startLineText = view.getLineTextForRender(view.selectionManager.selStartLine);
        float startX =
            view.getCaretXForLine(
                startLineText,
                view.selectionManager.selStartLine,
                Math.min(view.selectionManager.selStartChar, view.getLogicalLineLength(view.selectionManager.selStartLine, startLineText)));
        float startY = view.scrollManager.getDrawLineTop(view.selectionManager.selStartLine) + view.lineHeight;
        view.handlesManager.drawSelectionStartHandle(canvas, startX, startY, view.isRtl);
      } else {
        if (view.isRtl) view.handlesManager.clearRightHandleRect();
        else view.handlesManager.clearLeftHandleRect();
      }
      if (view.selectionManager.selEndLine >= firstVisibleLine
          && view.selectionManager.selEndLine <= lastVisibleLine
          && (!view.foldManager.isCodeFoldingEnabled || !view.foldManager.isLineHiddenByFold(view.selectionManager.selEndLine))) {
        String endLineText = view.getLineTextForRender(view.selectionManager.selEndLine);
        float endX =
            view.getCaretXForLine(
                endLineText,
                view.selectionManager.selEndLine,
                Math.min(view.selectionManager.selEndChar, view.getLogicalLineLength(view.selectionManager.selEndLine, endLineText)));
        float endY = view.scrollManager.getDrawLineTop(view.selectionManager.selEndLine) + view.lineHeight;
        view.handlesManager.drawSelectionEndHandle(canvas, endX, endY, view.isRtl);
      } else {
        if (view.isRtl) view.handlesManager.clearLeftHandleRect();
        else view.handlesManager.clearRightHandleRect();
      }
    }

    canvas.restore();

    if (view.popupMenuManager.isPopupVisible()) view.popupMenuManager.drawPopup(canvas);

    view.loadingCircleManager.draw(canvas);
  }

  void drawContentWrapped(Canvas canvas) {
    int wrapWidthPx = Math.max(1, Math.round(view.wordWrapManager.getWrapWidth(view)));
    final boolean drawDecorations = view.zoomManager.shouldDrawDecorations();
    if (!view.zoomManager.isZoomGestureActive()) {
      view.wordWrapManager.applyPendingWrapPrefixUpdateIfAny(view);
    }
    if (view.wordWrapManager.shouldSuppressWrapMetricsForFastSelectAll(view)) {
      drawContentWrappedFallback(canvas, wrapWidthPx);
      return;
    }
    if (!view.wordWrapManager.isWrapMetricsUsableForWindow(view, wrapWidthPx)) {
      if (!view.wordWrapManager.wrapMetricsReady || view.wordWrapManager.wrapMetricsWidth != wrapWidthPx) {
        view.wordWrapManager.scheduleWrapMetricsSnapshotIfNeeded(view, wrapWidthPx);
      }
      if (view.wordWrapManager.wrapPrefixValidUpToLine < view.getWindowEndLine()) {
        view.wordWrapManager.requestWrapPrefixRebuild(view);
      }
      drawContentWrappedFallback(canvas, wrapWidthPx);
      return;
    }
    int totalLines = view.getLinesCount();
    if (totalLines <= 0) totalLines = view.windowStartLine + view.linesWindow.size();
    if (totalLines <= 0) totalLines = 1;

    int totalVisual = view.wordWrapManager.getTotalVisualLineCount(view);
    int firstVisualIndex = Math.max(0, (int) (view.scrollManager.scrollY / view.lineHeight));
    int lastVisualIndex =
        Math.min(totalVisual - 1, firstVisualIndex + (int) Math.ceil(view.getHeight() / view.lineHeight) + 5);
    if (lastVisualIndex < firstVisualIndex) lastVisualIndex = firstVisualIndex;

    SodiumEditorView.VisualLinePosition firstPos = view.wordWrapManager.getVisualPositionForIndex(view, firstVisualIndex);
    SodiumEditorView.VisualLinePosition lastPos = view.wordWrapManager.getVisualPositionForIndex(view, lastVisualIndex);

    view.maybeKickWindowLoad(firstPos.line);

    HashMap<Integer, String> directLines = null;
    if (view.isIndexReady && view.sourceFile != null && view.sourceFile.exists()) {
      view.directLinesTmp.clear();
      directLines = view.directLinesTmp;
      int rangeStart = Math.max(0, firstPos.line - 1);
      int rangeEnd = Math.min(totalLines - 1, lastPos.line + 1);
      populateDirectLinesForRange(rangeStart, rangeEnd, directLines);
    }

    boolean patched = false;
    if (!view.zoomManager.isZoomGestureActive()) {
      patched =
          view.wordWrapManager.patchWrapMetricsForVisualRange(view,
              firstVisualIndex, lastVisualIndex, directLines, wrapWidthPx);
    }
    if (patched) {
      totalLines = view.getLinesCount();
      if (totalLines <= 0) totalLines = view.windowStartLine + view.linesWindow.size();
      if (totalLines <= 0) totalLines = 1;

      totalVisual = view.wordWrapManager.getTotalVisualLineCount(view);
      firstVisualIndex = Math.max(0, (int) (view.scrollManager.scrollY / view.lineHeight));
      lastVisualIndex =
          Math.min(
              totalVisual - 1, firstVisualIndex + (int) Math.ceil(view.getHeight() / view.lineHeight) + 5);
      if (lastVisualIndex < firstVisualIndex) lastVisualIndex = firstVisualIndex;

      firstPos = view.wordWrapManager.getVisualPositionForIndex(view, firstVisualIndex);
      lastPos = view.wordWrapManager.getVisualPositionForIndex(view, lastVisualIndex);
      view.maybeKickWindowLoad(firstPos.line);

      if (directLines != null) {
        view.directLinesTmp.clear();
        int rangeStart = Math.max(0, firstPos.line - 1);
        int rangeEnd = Math.min(totalLines - 1, lastPos.line + 1);
        populateDirectLinesForRange(rangeStart, rangeEnd, directLines);
      }
    }

    float baseY = firstVisualIndex * view.lineHeight;
    float translateY = -view.scrollManager.scrollY + baseY;

    if (view.lineNumberManager.isShowLineNumbers()) {
      canvas.drawRect(
          view.getGutterStartX(),
          0,
          view.lineNumberManager.getGutterRight(view.getGutterStartX()),
          view.getHeight(),
          view.lineNumberManager.getGutterPaint());

      float separatorLeft;
      if (view.isRtl) {
        separatorLeft = view.getGutterStartX();
      } else {
        separatorLeft = view.lineNumberManager.getSeparatorLeft(view.getGutterStartX());
      }
      canvas.drawRect(
          separatorLeft,
          0,
          separatorLeft + view.lineNumberManager.getGutterSeparatorWidth(),
          view.getHeight(),
          view.lineNumberManager.getGutterSeparatorPaint());
    }

    if (view.lineNumberManager.isHighlightCurrentLineInGutter()
        && (!view.foldManager.isCodeFoldingEnabled || !view.foldManager.isLineHiddenByFold(view.cursorManager.getLine()))) {
      int currentVisualIndex = view.getVisualIndexForLineAndChar(view.cursorManager.getLine(), 0);
      String cursorLineText = view.getLineTextForRender(view.cursorManager.getLine());
      int[] starts = view.wordWrapManager.getWrapStartsForLine(view, view.cursorManager.getLine(), cursorLineText);
      int segCount = Math.max(1, starts.length);
      int lastVisualIndexForLine = currentVisualIndex + segCount - 1;
      int drawFrom = Math.max(firstVisualIndex, currentVisualIndex);
      int drawTo = Math.min(lastVisualIndex, lastVisualIndexForLine);
      for (int v = drawFrom; v <= drawTo; v++) {
        float top = Math.round(v * view.lineHeight - view.scrollManager.scrollY);
        float bottom = top + view.lineHeight;
        view.lineNumberManager.drawCurrentLineHighlightInGutter(canvas, top, bottom, view.highlightManager.currentLinePaint);
      }
    }

    if (view.lineNumberManager.isShowLineNumbers()) {
      view.lineNumberManager.drawLineNumbersCachedWrapped(canvas, firstVisualIndex, lastVisualIndex);
    }

    canvas.save();
    if (view.isRtl) {
      canvas.clipRect(
          view.lineNumberManager.getContentClipLeft(view.isRtl),
          0,
          view.lineNumberManager.getContentClipRight(view.getWidth(), view.isRtl),
          view.getHeight());
    } else {
      canvas.clipRect(
          view.lineNumberManager.getContentClipLeft(false), 0, view.getWidth(), view.getHeight());
    }
    canvas.translate(view.getTextStartX() - view.getEffectiveScrollX(), translateY);
    if (view.zoomManager.isPinchVisualZoomActive()) {
      float pivotX = view.zoomManager.getPinchFocusX() - (view.getTextStartX() - view.getEffectiveScrollX());
      float pivotY = view.zoomManager.getPinchFocusY() - translateY;
      canvas.scale(view.zoomManager.getPinchVisualScale(), view.zoomManager.getPinchVisualScale(), pivotX, pivotY);
    }

    Paint selPaint = null;
    if (view.selectionManager.hasSelection()) {
      selPaint = view.selectionManager.getSelectionPaint();
    }

    int startLine = view.selectionManager.selStartLine;
    int startChar = view.selectionManager.selStartChar;
    int endLine = view.selectionManager.selEndLine;
    int endChar = view.selectionManager.selEndChar;
    if (view.selectionManager.hasSelection() && view.comparePos(view.selectionManager.selStartLine, view.selectionManager.selStartChar, view.selectionManager.selEndLine, view.selectionManager.selEndChar) > 0) {
      startLine = view.selectionManager.selEndLine;
      startChar = view.selectionManager.selEndChar;
      endLine = view.selectionManager.selStartLine;
      endChar = view.selectionManager.selStartChar;
    }

    for (int v = firstVisualIndex; v <= lastVisualIndex; v++) {
      SodiumEditorView.VisualLinePosition pos = view.wordWrapManager.getVisualPositionForIndex(view, v);
      String line = view.getLineTextForRenderWithDirect(pos.line, directLines);
      int[] starts = view.wordWrapManager.getWrapStartsForLine(view, pos.line, line);

      if (pos.segment >= starts.length) continue;

      int segStart = view.wordWrapManager.getWrapSegmentStart(starts, pos.segment);
      int segEnd = view.wordWrapManager.getWrapSegmentEnd(starts, pos.segment, line.length());
      float segBaseX = view.isRtl ? view.getRtlSegmentBaseX(line, pos.line, segStart, segEnd) : 0f;

      float top = Math.round((v - firstVisualIndex) * view.lineHeight);
      float bottom = top + view.lineHeight;
      float y = Math.round(top + view.lineHeight - view.paint.descent());

      if (view.highlightManager.highlightCurrentLine && pos.line == view.cursorManager.getLine() && !view.selectionManager.hasSelection()) {
        canvas.drawRect(
            -view.paddingLeft, top, Math.max(view.wordWrapManager.getWrapWidth(view), view.getWidth()), bottom, view.highlightManager.currentLinePaint);
      }

      if (view.selectionManager.hasSelection() && selPaint != null) {
        if (pos.line >= startLine && pos.line <= endLine) {
          int lineSelStart = (pos.line == startLine) ? startChar : 0;
          int lineSelEnd = (pos.line == endLine) ? endChar : line.length();
          int segSelStart = Math.max(segStart, lineSelStart);
          int segSelEnd = Math.min(segEnd, lineSelEnd);
          if (segSelEnd > segSelStart) {
            float left;
            float right;
            if (view.isRtl) {
              float x1 =
                  view.getCaretXForSegment(
                      line, pos.line, segStart, segEnd, Math.min(segSelStart, line.length()));
              float x2 =
                  view.getCaretXForSegment(
                      line, pos.line, segStart, segEnd, Math.min(segSelEnd, line.length()));
              left = Math.min(x1, x2);
              right = Math.max(x1, x2);
            } else {
              boolean fullSegmentSelected = (segSelStart == segStart && segSelEnd == segEnd);
              float leftRel =
                  fullSegmentSelected
                      ? 0f
                      : view.whitespaceGuideManager.measureTextWithVisualSpaces(view, line, segStart, segSelStart, view.paint);
              float rightRel =
                  fullSegmentSelected
                      ? Math.max(0f, wrapWidthPx)
                      : leftRel + view.whitespaceGuideManager.measureTextWithVisualSpaces(view, line, segSelStart, segSelEnd, view.paint);
              left = leftRel + segBaseX;
              right = rightRel + segBaseX;
            }
            boolean roundTop = (pos.line == startLine && segSelStart == startChar);
            boolean roundBottom = (pos.line == endLine && segSelEnd == endChar);
            view.selectionManager.drawSelectionSegment(
                canvas,
                left,
                top,
                right,
                bottom,
                roundTop,
                roundTop,
                roundBottom,
                roundBottom,
                view.lineHeight, selPaint);
          }
        }
      }

      int segDrawEnd = segEnd;
      if (view.wordWrapManager.isWordWrapIndicatorEnabled && segEnd < line.length()) {
        segDrawEnd = view.wordWrapManager.clampSegmentEndForWrapIndicator(view, line, segStart, segEnd, wrapWidthPx);
      }
      canvas.save();
      if (segBaseX != 0f) canvas.translate(segBaseX, 0f);
      view.searchManager.drawSearchHighlightsForSegment(canvas, line, pos.line, segStart, segDrawEnd, top, bottom);
      view.highlightManager.drawHighlightedLineSegment(canvas, line, pos.line, segStart, segDrawEnd, y, top, bottom);
      view.highlightManager.drawErrorUnderlinesForSegment(canvas, line, pos.line, segStart, segDrawEnd, y, top, bottom);
      view.drawDeleteAnimationForSegment(canvas, line, pos.line, segStart, segDrawEnd, y);
      if (drawDecorations) {
        view.whitespaceGuideManager.drawWhitespaceGuidesForSegment(view, canvas, line, pos.line, segStart, segDrawEnd, y);
      }
      view.autoSuggestionManager.drawAutoSuggestionWrapped(canvas, line, pos.line, segStart, segDrawEnd, v, y);
      if (view.wordWrapManager.isWordWrapIndicatorEnabled && segEnd < line.length()) {
        float indicatorX =
            view.isRtl
                ? view.wordWrapManager.wordWrapIndicatorPadPx
                : Math.max(
                    view.wordWrapManager.wordWrapIndicatorPadPx,
                    wrapWidthPx - view.wordWrapManager.wordWrapIndicatorWidth - view.wordWrapManager.wordWrapIndicatorPadPx);
        canvas.drawText(view.wordWrapManager.WORD_WRAP_INDICATOR_TEXT, indicatorX, y, view.wordWrapManager.wordWrapIndicatorPaint);
      }
      canvas.restore();
    }

    if (view.isFocused() && !view.isReadOnly && !view.selectionManager.hasSelection()) {
      int cursorVisualIndex = view.getVisualIndexForLineAndChar(view.cursorManager.getLine(), view.cursorManager.getChar());
      if (cursorVisualIndex >= firstVisualIndex && cursorVisualIndex <= lastVisualIndex) {
        String cursorLineText = view.getLineTextForRenderWithDirect(view.cursorManager.getLine(), directLines);
        int[] starts = view.wordWrapManager.getWrapStartsForLine(view, view.cursorManager.getLine(), cursorLineText);
        int seg = view.wordWrapManager.getWrapSegmentIndexForChar(starts, view.cursorManager.getChar());
        int segStart = view.wordWrapManager.getWrapSegmentStart(starts, seg);
        int segEnd = view.wordWrapManager.getWrapSegmentEnd(starts, seg, cursorLineText.length());
        int safeChar = Math.min(view.cursorManager.getChar(), cursorLineText.length());
        float cursorX = view.getCaretXForSegment(cursorLineText, view.cursorManager.getLine(), segStart, segEnd, safeChar);
        float cursorY = (cursorVisualIndex - firstVisualIndex) * view.lineHeight;
        view.cursorManager.drawCaret(canvas, cursorX, cursorY);
        float drawX = view.cursorAnimationManager.getCursorDrawX();
        float drawY = view.cursorAnimationManager.getCursorDrawY();
        view.handlesManager.drawCursorHandle(canvas, drawX, drawY, view.lineHeight);
      }
    }

    if (view.selectionManager.hasSelection() && !view.isReadOnly) {
      int startVisualIndex = view.getVisualIndexForLineAndChar(view.selectionManager.selStartLine, view.selectionManager.selStartChar);
      int endVisualIndex = view.getVisualIndexForLineAndChar(view.selectionManager.selEndLine, view.selectionManager.selEndChar);
      if (startVisualIndex >= firstVisualIndex && startVisualIndex <= lastVisualIndex) {
        String startLineText = view.getLineTextForRenderWithDirect(view.selectionManager.selStartLine, directLines);
        int[] starts = view.wordWrapManager.getWrapStartsForLine(view, view.selectionManager.selStartLine, startLineText);
        int seg = view.wordWrapManager.getWrapSegmentIndexForChar(starts, view.selectionManager.selStartChar);
        int segStart = view.wordWrapManager.getWrapSegmentStart(starts, seg);
        int segEnd = view.wordWrapManager.getWrapSegmentEnd(starts, seg, startLineText.length());
        float x =
            view.getCaretXForSegment(
                startLineText,
                view.selectionManager.selStartLine,
                segStart,
                segEnd,
                Math.min(view.selectionManager.selStartChar, startLineText.length()));
        float y = (startVisualIndex - firstVisualIndex) * view.lineHeight + view.lineHeight + translateY;
        view.handlesManager.drawSelectionStartHandle(canvas, x, y, view.isRtl);
      } else {
        view.handlesManager.clearLeftHandleRect();
      }
      if (endVisualIndex >= firstVisualIndex && endVisualIndex <= lastVisualIndex) {
        String endLineText = view.getLineTextForRenderWithDirect(view.selectionManager.selEndLine, directLines);
        int[] starts = view.wordWrapManager.getWrapStartsForLine(view, view.selectionManager.selEndLine, endLineText);
        int seg = view.wordWrapManager.getWrapSegmentIndexForChar(starts, view.selectionManager.selEndChar);
        int segStart = view.wordWrapManager.getWrapSegmentStart(starts, seg);
        int segEnd = view.wordWrapManager.getWrapSegmentEnd(starts, seg, endLineText.length());
        float x =
            view.getCaretXForSegment(
                endLineText,
                view.selectionManager.selEndLine,
                segStart,
                segEnd,
                Math.min(view.selectionManager.selEndChar, endLineText.length()));
        float y = (endVisualIndex - firstVisualIndex) * view.lineHeight + view.lineHeight + translateY;
        view.handlesManager.drawSelectionEndHandle(canvas, x, y, view.isRtl);
      } else {
        view.handlesManager.clearRightHandleRect();
      }
    }

    canvas.restore();

    if (view.popupMenuManager.isPopupVisible()) view.popupMenuManager.drawPopup(canvas);

    view.loadingCircleManager.draw(canvas);
  }

  void drawContentWrappedFallback(Canvas canvas, int wrapWidthPx) {
    final boolean drawDecorations = view.zoomManager.shouldDrawDecorations();

    int firstLine;
    int lastLine;
    if (view.foldManager.isCodeFoldingEnabled) {
      int visibleCount = view.getVisibleLineCount();
      if (visibleCount <= 0) visibleCount = 1;
      int firstVisibleIndex = Math.max(0, (int) (view.scrollManager.scrollY / view.lineHeight));
      int lastVisibleIndex =
          Math.min(visibleCount - 1, firstVisibleIndex + (int) Math.ceil(view.getHeight() / view.lineHeight) + 5);
      firstLine = view.mapVisibleIndexToGlobal(firstVisibleIndex);
      lastLine = view.mapVisibleIndexToGlobal(lastVisibleIndex);
    } else {
      firstLine = Math.max(0, (int) (view.scrollManager.scrollY / view.lineHeight));
      lastLine = firstLine + (int) Math.ceil(view.getHeight() / view.lineHeight) + 5;
    }

    int totalLines = view.getLinesCount();
    if (totalLines <= 0) totalLines = view.windowStartLine + view.linesWindow.size();
    if (totalLines <= 0) totalLines = 1;
    lastLine = Math.min(Math.max(0, totalLines - 1), lastLine);
    if (lastLine < firstLine) lastLine = firstLine;

    int firstIndex = view.foldManager.isCodeFoldingEnabled ? view.getVisibleIndexForGlobalLine(firstLine) : firstLine;

    HashMap<Integer, String> directLines = null;
    if (view.isIndexReady && view.sourceFile != null && view.sourceFile.exists()) {
      view.directLinesTmp.clear();
      directLines = view.directLinesTmp;
      int rangeStart = Math.max(0, firstLine - 1);
      int rangeEnd = Math.min(totalLines - 1, lastLine + 1);
      populateDirectLinesForRange(rangeStart, rangeEnd, directLines);
    }

    float baseY = firstLine * view.lineHeight;
    float translateY = -view.scrollManager.scrollY + baseY;

    boolean useLineNumberCache = false;

    canvas.save();
    canvas.translate(0, translateY);

    float lineNumX = 0f;
    if (view.lineNumberManager.isShowLineNumbers() && !useLineNumberCache) {
      lineNumX =
          view.isRtl
              ? view.getGutterStartX() + view.paddingLeft
              : view.lineNumberManager.getGutterRight(view.getGutterStartX())
                  - view.paddingLeft;
    }

    int saveCount = canvas.save();
    if (view.isRtl) {
      canvas.clipRect(
          view.lineNumberManager.getContentClipLeft(view.isRtl),
          0,
          view.lineNumberManager.getContentClipRight(view.getWidth(), view.isRtl),
          view.getHeight());
    } else {
      canvas.clipRect(
          view.lineNumberManager.getContentClipLeft(false), 0, view.getWidth(), view.getHeight());
    }
    canvas.translate(view.getTextStartX() - view.getEffectiveScrollX(), 0);

    Paint selPaint = null;
    if (view.selectionManager.hasSelection()) {
      selPaint = view.selectionManager.getSelectionPaint();
    }

    int startLine = view.selectionManager.selStartLine;
    int startChar = view.selectionManager.selStartChar;
    int endLine = view.selectionManager.selEndLine;
    int endChar = view.selectionManager.selEndChar;
    if (view.selectionManager.hasSelection() && view.comparePos(view.selectionManager.selStartLine, view.selectionManager.selStartChar, view.selectionManager.selEndLine, view.selectionManager.selEndChar) > 0) {
      startLine = view.selectionManager.selEndLine;
      startChar = view.selectionManager.selEndChar;
      endLine = view.selectionManager.selStartLine;
      endChar = view.selectionManager.selStartChar;
    }

    int visualIndex = firstIndex;
    float yOffset = 0f;
    boolean cursorDrawn = false;
    int startHandleVisual = -1;
    int endHandleVisual = -1;

    for (int line = firstLine; line <= lastLine; line++) {
      if (yOffset > view.getHeight() + view.lineHeight) break;
      String text = view.getLineTextForRenderWithDirect(line, directLines);
      int[] starts = view.wordWrapManager.getWrapStartsForLine(view, line, text);

      for (int seg = 0; seg < starts.length; seg++) {
        int segStart = view.wordWrapManager.getWrapSegmentStart(starts, seg);
        int segEnd = view.wordWrapManager.getWrapSegmentEnd(starts, seg, text.length());
        float segBaseX = view.isRtl ? view.getRtlSegmentBaseX(text, line, segStart, segEnd) : 0f;

        float top = Math.round(yOffset);
        float bottom = top + view.lineHeight;
        float y = Math.round(top + view.lineHeight - view.paint.descent());

        if (view.lineNumberManager.isShowLineNumbers() && seg == 0 && !useLineNumberCache) {
          canvas.restore();
          view.lineNumberManager.drawLineNumber(
              canvas,
              line,
              lineNumX,
              y,
              view.lineNumberManager.getCurrentLineNumberColor(),
              line == view.cursorManager.getLine());
          canvas.save();
          if (view.isRtl) {
            canvas.clipRect(
                view.lineNumberManager.getContentClipLeft(view.isRtl),
                0,
                view.lineNumberManager.getContentClipRight(view.getWidth(), view.isRtl),
                view.getHeight());
          } else {
            canvas.clipRect(
                view.lineNumberManager.getContentClipLeft(false), 0, view.getWidth(), view.getHeight());
          }
          canvas.translate(view.getTextStartX() - view.getEffectiveScrollX(), 0);
        }

        if (view.highlightManager.highlightCurrentLine && line == view.cursorManager.getLine() && !view.selectionManager.hasSelection()) {
          canvas.drawRect(
              -view.paddingLeft, top, Math.max(view.wordWrapManager.getWrapWidth(view), view.getWidth()), bottom, view.highlightManager.currentLinePaint);
        }

        if (view.selectionManager.hasSelection() && selPaint != null) {
          if (line >= startLine && line <= endLine) {
            int lineSelStart = (line == startLine) ? startChar : 0;
            int lineSelEnd = (line == endLine) ? endChar : text.length();
            int segSelStart = Math.max(segStart, lineSelStart);
            int segSelEnd = Math.min(segEnd, lineSelEnd);
            if (segSelEnd > segSelStart) {
              boolean fullSegmentSelected = (segSelStart == segStart && segSelEnd == segEnd);
              float leftRel =
                  fullSegmentSelected
                      ? 0f
                      : view.whitespaceGuideManager.measureTextWithVisualSpaces(view, text, segStart, segSelStart, view.paint);
              float rightRel =
                  fullSegmentSelected
                      ? Math.max(0f, wrapWidthPx)
                      : leftRel + view.whitespaceGuideManager.measureTextWithVisualSpaces(view, text, segSelStart, segSelEnd, view.paint);
              float left = leftRel + segBaseX;
              float right = rightRel + segBaseX;
              boolean roundTop = (line == startLine && segSelStart == startChar);
              boolean roundBottom = (line == endLine && segSelEnd == endChar);
              view.selectionManager.drawSelectionSegment(
                  canvas,
                  left,
                  top,
                  right,
                  bottom,
                  roundTop,
                  roundTop,
                  roundBottom,
                  roundBottom,
                  view.lineHeight, selPaint);
            }
          }
        }

        int segDrawEnd = segEnd;
        if (view.wordWrapManager.isWordWrapIndicatorEnabled && segEnd < text.length()) {
          segDrawEnd = view.wordWrapManager.clampSegmentEndForWrapIndicator(view, text, segStart, segEnd, wrapWidthPx);
        }
        canvas.save();
        if (segBaseX != 0f) canvas.translate(segBaseX, 0f);
        view.searchManager.drawSearchHighlightsForSegment(canvas, text, line, segStart, segDrawEnd, top, bottom);
        view.highlightManager.drawHighlightedLineSegment(canvas, text, line, segStart, segDrawEnd, y, top, bottom);
        view.highlightManager.drawErrorUnderlinesForSegment(canvas, text, line, segStart, segDrawEnd, y, top, bottom);
        view.drawDeleteAnimationForSegment(canvas, text, line, segStart, segDrawEnd, y);
        if (drawDecorations) {
          view.whitespaceGuideManager.drawWhitespaceGuidesForSegment(view, canvas, text, line, segStart, segDrawEnd, y);
        }
        view.autoSuggestionManager.drawAutoSuggestionWrapped(canvas, text, line, segStart, segDrawEnd, visualIndex, y);
        if (view.wordWrapManager.isWordWrapIndicatorEnabled && segEnd < text.length()) {
          float indicatorX =
              view.isRtl
                  ? view.wordWrapManager.wordWrapIndicatorPadPx
                  : Math.max(
                      view.wordWrapManager.wordWrapIndicatorPadPx,
                      wrapWidthPx - view.wordWrapManager.wordWrapIndicatorWidth - view.wordWrapManager.wordWrapIndicatorPadPx);
          canvas.drawText(view.wordWrapManager.WORD_WRAP_INDICATOR_TEXT, indicatorX, y, view.wordWrapManager.wordWrapIndicatorPaint);
        }
        canvas.restore();

        if (!cursorDrawn && view.isFocused() && !view.selectionManager.hasSelection() && line == view.cursorManager.getLine()) {
          int cursorSeg = view.wordWrapManager.getWrapSegmentIndexForChar(starts, view.cursorManager.getChar());
          if (cursorSeg == seg) {
            int safeChar = Math.min(view.cursorManager.getChar(), text.length());
            float cursorX = view.getCaretXForSegment(text, line, segStart, segEnd, safeChar);
            float cursorY = top;
            view.cursorManager.drawCaret(canvas, cursorX, cursorY);
            float drawX = view.cursorAnimationManager.getCursorDrawX();
            float drawY = view.cursorAnimationManager.getCursorDrawY();
            view.handlesManager.drawCursorHandle(canvas, drawX, drawY, view.lineHeight);
            cursorDrawn = true;
          }
        }

        if (view.selectionManager.hasSelection()) {
          if (line == view.selectionManager.selStartLine) {
            int selSeg = view.wordWrapManager.getWrapSegmentIndexForChar(starts, view.selectionManager.selStartChar);
            if (selSeg == seg) startHandleVisual = visualIndex;
          }
          if (line == view.selectionManager.selEndLine) {
            int selSeg = view.wordWrapManager.getWrapSegmentIndexForChar(starts, view.selectionManager.selEndChar);
            if (selSeg == seg) endHandleVisual = visualIndex;
          }
        }

        yOffset += view.lineHeight;
        visualIndex++;
        if (yOffset > view.getHeight() + view.lineHeight) break;
      }
    }

    canvas.restore();
    canvas.restore();

    if (view.selectionManager.hasSelection()) {
      if (startHandleVisual >= firstIndex && startHandleVisual <= visualIndex - 1) {
        String startLineText = view.getLineTextForRenderWithDirect(view.selectionManager.selStartLine, directLines);
        int[] starts = view.wordWrapManager.getWrapStartsForLine(view, view.selectionManager.selStartLine, startLineText);
        int seg = view.wordWrapManager.getWrapSegmentIndexForChar(starts, view.selectionManager.selStartChar);
        int segStart = view.wordWrapManager.getWrapSegmentStart(starts, seg);
        int segEnd = view.wordWrapManager.getWrapSegmentEnd(starts, seg, startLineText.length());
        float x =
            view.getCaretXForSegment(
                startLineText,
                view.selectionManager.selStartLine,
                segStart,
                segEnd,
                Math.min(view.selectionManager.selStartChar, startLineText.length()));
        float y = (startHandleVisual - firstIndex) * view.lineHeight + view.lineHeight + translateY;
        view.handlesManager.drawSelectionStartHandle(canvas, x, y, view.isRtl);
      } else {
        view.handlesManager.clearLeftHandleRect();
      }

      if (endHandleVisual >= firstIndex && endHandleVisual <= visualIndex - 1) {
        String endLineText = view.getLineTextForRenderWithDirect(view.selectionManager.selEndLine, directLines);
        int[] starts = view.wordWrapManager.getWrapStartsForLine(view, view.selectionManager.selEndLine, endLineText);
        int seg = view.wordWrapManager.getWrapSegmentIndexForChar(starts, view.selectionManager.selEndChar);
        int segStart = view.wordWrapManager.getWrapSegmentStart(starts, seg);
        int segEnd = view.wordWrapManager.getWrapSegmentEnd(starts, seg, endLineText.length());
        float x =
            view.getCaretXForSegment(
                endLineText,
                view.selectionManager.selEndLine,
                segStart,
                segEnd,
                Math.min(view.selectionManager.selEndChar, endLineText.length()));
        float y = (endHandleVisual - firstIndex) * view.lineHeight + view.lineHeight + translateY;
        view.handlesManager.drawSelectionEndHandle(canvas, x, y, view.isRtl);
      } else {
        view.handlesManager.clearRightHandleRect();
      }
    }

    if (view.popupMenuManager.isPopupVisible()) view.popupMenuManager.drawPopup(canvas);

    view.loadingCircleManager.draw(canvas);
  }

  public float getRtlLineBaseX(@Nullable String line, int globalLine) {
    if (!view.isRtl || line == null) return 0f;
    int logicalLen = view.getLogicalLineLength(globalLine, line);
    float w = view.highlightManager.measureHighlightedSegmentWidth(line, globalLine, 0, logicalLen);
    float area = view.getTextAreaWidth();
    return area - w;
  }

  public float getRtlSegmentBaseX(@Nullable String line, int globalLine, int segStart, int segEnd) {
    if (!view.isRtl || line == null) return 0f;
    float w = view.highlightManager.measureHighlightedSegmentWidth(line, globalLine, segStart, segEnd);
    float area = view.getTextAreaWidth();
    return area - w;
  }

  public float getCaretXForLine(String line, int globalLine, int charIndex) {
    float x = view.highlightManager.measureText(line, charIndex, globalLine);
    if (!view.isRtl) return x;
    int logicalLen = view.getLogicalLineLength(globalLine, line);
    float w = view.highlightManager.measureHighlightedSegmentWidth(line, globalLine, 0, logicalLen);
    float baseX = getRtlLineBaseX(line, globalLine);
    return baseX + (w - x);
  }

  public float getCaretXForSegment(
      String line, int globalLine, int segStart, int segEnd, int charIndex) {
    float xRel = view.whitespaceGuideManager.measureTextWithVisualSpaces(view, line, segStart, charIndex, view.paint);
    if (!view.isRtl) return xRel;
    float w = view.highlightManager.measureHighlightedSegmentWidth(line, globalLine, segStart, segEnd);
    float baseX = getRtlSegmentBaseX(line, globalLine, segStart, segEnd);
    return baseX + (w - xRel);
  }

  public int getCharIndexForXInRange(String text, int globalLine, int start, int end, float x) {
    if (text == null || text.isEmpty()) return 0;
    start = Math.max(0, Math.min(start, text.length()));
    end = Math.max(start, Math.min(end, text.length()));
    if (view.isRtl) {
      float baseX = getRtlSegmentBaseX(text, globalLine, start, end);
      x -= baseX;
      float w = view.highlightManager.measureHighlightedSegmentWidth(text, globalLine, start, end);
      x = w - x;
    }
    if (x <= 0f) return start;
    int len = end - start;
    if (len <= 0) return start;
    if (view.getVisualSpaceScale() == 1) {
      int count = view.paint.breakText(text, start, end, true, x, null);
      int idx = start + Math.max(0, count);
      return Math.min(idx, end);
    }
    float[] widths = view.whitespaceGuideManager.ensureMeasureWidthBuffer(len);
    view.paint.getTextWidths(text, start, end, widths);
    float current = 0f;
    for (int i = 0; i < len; i++) {
      float adv = view.whitespaceGuideManager.getCharAdvanceWidth(text.charAt(start + i), widths[i], view.paint, WordWrapManager.DEFAULT_TAB_SIZE_SPACES);
      float mid = current + adv * 0.5f;
      if (x < mid) return start + i;
      if (x < current + adv) return start + i + 1;
      current += adv;
    }
    return end;
  }

  public SodiumEditorView.CursorTarget getCursorTargetForPosition(
      float viewX, float viewY, @Nullable Map<Integer, String> directLines) {
    float y = viewY + view.scrollManager.scrollY;
    int visualIndex = Math.max(0, (int) (y / view.lineHeight));
    SodiumEditorView.VisualLinePosition pos =
        view.wordWrapManager.isWordWrapEnabled
            ? view.wordWrapManager.getVisualPositionForIndex(view, visualIndex)
            : new SodiumEditorView.VisualLinePosition(view.mapVisibleIndexToGlobal(visualIndex), 0);
    String line = view.getLineTextForRenderWithDirect(pos.line, directLines);
    if (!view.wordWrapManager.isWordWrapEnabled) {
      float x = view.viewToTextXPublic(viewX);
      int charIndex = view.getCharIndexForXPublic(line, x, pos.line);
      int clamped = Math.max(0, Math.min(charIndex, view.getLogicalLineLength(pos.line, line)));
      return new SodiumEditorView.CursorTarget(pos.line, clamped);
    }
    int[] starts = view.wordWrapManager.getWrapStartsForLine(view, pos.line, line);
    int seg = Math.min(Math.max(0, pos.segment), Math.max(0, starts.length - 1));
    int segStart = view.wordWrapManager.getWrapSegmentStart(starts, seg);
    int segEnd = view.wordWrapManager.getWrapSegmentEnd(starts, seg, line.length());
    float x = view.viewToTextXPublic(viewX);
    int charIndex = getCharIndexForXInRange(line, pos.line, segStart, segEnd, x);
    int clamped = Math.max(0, Math.min(charIndex, line.length()));
    return new SodiumEditorView.CursorTarget(pos.line, clamped);
  }

  public void recalculateMaxLineWidth() {
    final int startLine;
    final ArrayList<String> snapshot;
    synchronized (view.linesWindow) {
      startLine = view.windowStartLine;
      snapshot = new ArrayList<>(view.linesWindow);
    }
    if (snapshot.isEmpty()) return;

    float mx = 0f;
    for (int i = 0; i < snapshot.size(); i++) {
      String line = snapshot.get(i);
      if (line == null) line = "";
      float w = getWidthForLine(startLine + i, line);
      synchronized (view.lineWidthCache) {
        view.lineWidthCache.put(startLine + i, w);
      }
      if (w > mx) mx = w;
    }
    view.currentMaxWindowLineWidth = mx;
    view.globalMaxLineWidth = Math.max(view.globalMaxLineWidth, mx);
    view.scrollManager.clampScrollX();
    view.invalidate();
  }

  public void recalculateMaxLineWidthAsync() {
    final int token = ++view.maxWidthRecalcToken;
    final int startLine;
    final ArrayList<String> snapshot;
    synchronized (view.linesWindow) {
      startLine = view.windowStartLine;
      snapshot = new ArrayList<>(view.linesWindow);
    }
    if (snapshot.isEmpty()) return;

    final int chunkSize = 120;
    view.post(
        new Runnable() {
          int index = 0;
          float mx = 0f;

          @Override
          public void run() {
            if (token != view.maxWidthRecalcToken) return;
            int end = Math.min(snapshot.size(), index + chunkSize);
            for (int i = index; i < end; i++) {
              String line = snapshot.get(i);
              if (line == null) line = "";
              float w = getWidthForLine(startLine + i, line);
              synchronized (view.lineWidthCache) {
                view.lineWidthCache.put(startLine + i, w);
              }
              if (w > mx) mx = w;
            }
            view.currentMaxWindowLineWidth = mx;
            view.globalMaxLineWidth = Math.max(view.globalMaxLineWidth, mx);
            index = end;
            if (index < snapshot.size()) {
              view.post(this);
            } else {
              view.scrollManager.clampScrollX();
              view.invalidate();
            }
          }
        });
  }
}
