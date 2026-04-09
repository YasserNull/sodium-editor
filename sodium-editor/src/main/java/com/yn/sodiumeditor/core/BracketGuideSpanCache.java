package com.yn.sodiumeditor.core;

import android.graphics.Canvas;
import com.yn.sodiumeditor.SodiumEditor;
import com.yn.sodiumeditor.utils.BracketGuideScanner;
import com.yn.sodiumeditor.utils.BracketGuideScanner.SpanCollector;
import com.yn.sodiumeditor.utils.BracketGuideScanner.BracketSpanScanState;

/**
 * Manages span-based bracket guide cache for performance-optimized rendering.
 */
public class BracketGuideSpanCache {
  private final SodiumEditor editor;
  private final BracketGuides bracketGuides;

  public int bracketGuideSpanCacheStartLine = -1;
  public int bracketGuideSpanCacheEndLine = -1;
  public int bracketGuideSpanCacheEditVersion = -1;
  public int bracketGuideSpanCacheConfigHash = 0;
  public int bracketGuideSpanCount = 0;
  private int bracketGuideSpanCapacity = 0;
  private int[] bracketGuideSpanColumns;
  private int[] bracketGuideSpanStartLines;
  private int[] bracketGuideSpanEndLines;
  private char[] bracketGuideSpanBrackets;
  public boolean bracketGuideSpanBuildInProgress = false;
  public int bracketGuideSpanPendingStart = -1;
  public int bracketGuideSpanPendingEnd = -1;
  public int bracketGuideSpanPendingEditVersion = -1;
  public int bracketGuideSpanPendingConfigHash = 0;
  private float[] bracketGuideSpanDrawPts;
  private int bracketGuideSpanDrawPtsCapacity = 0;

  public BracketGuideSpanCache(SodiumEditor editor, BracketGuides bracketGuides) {
    this.editor = editor;
    this.bracketGuides = bracketGuides;
  }

  private void ensureSpanCapacity(int desired) {
    if (desired <= bracketGuideSpanCapacity) return;
    int newCap = Math.max(64, desired * 2);
    bracketGuideSpanCapacity = newCap;
    bracketGuideSpanColumns = (bracketGuideSpanColumns == null) ? new int[newCap] : java.util.Arrays.copyOf(bracketGuideSpanColumns, newCap);
    bracketGuideSpanStartLines = (bracketGuideSpanStartLines == null) ? new int[newCap] : java.util.Arrays.copyOf(bracketGuideSpanStartLines, newCap);
    bracketGuideSpanEndLines = (bracketGuideSpanEndLines == null) ? new int[newCap] : java.util.Arrays.copyOf(bracketGuideSpanEndLines, newCap);
    bracketGuideSpanBrackets = (bracketGuideSpanBrackets == null) ? new char[newCap] : java.util.Arrays.copyOf(bracketGuideSpanBrackets, newCap);
  }

  private void addSpan(int column, int startLine, int endLine, char bracket) {
    if (startLine > endLine) return;
    ensureSpanCapacity(bracketGuideSpanCount + 1);
    int idx = bracketGuideSpanCount++;
    bracketGuideSpanColumns[idx] = column;
    bracketGuideSpanStartLines[idx] = startLine;
    bracketGuideSpanEndLines[idx] = endLine;
    bracketGuideSpanBrackets[idx] = bracket;
  }

  private float getGuideXApproxFromColumn(int column) {
    float spaceWidth = editor.getVisualSpaceWidth(editor.textRender.paint);
    return spaceWidth * Math.max(0, column);
  }

  /**
   * Invalidates the span cache.
   */
  public void invalidate() {
    bracketGuideSpanCacheStartLine = -1;
    bracketGuideSpanCacheEndLine = -1;
    bracketGuideSpanCacheEditVersion = -1;
    bracketGuideSpanCacheConfigHash = 0;
    bracketGuideSpanCount = 0;
    bracketGuideSpanBuildInProgress = false;
    bracketGuideSpanPendingStart = -1;
    bracketGuideSpanPendingEnd = -1;
    bracketGuideSpanPendingEditVersion = -1;
    bracketGuideSpanPendingConfigHash = 0;
  }

  /**
   * Checks if span cache is valid.
   */
  public boolean isCacheValid(int startLine, int endLine, int editVersion, int configHash) {
    return startLine == bracketGuideSpanCacheStartLine
        && endLine == bracketGuideSpanCacheEndLine
        && editVersion == bracketGuideSpanCacheEditVersion
        && configHash == bracketGuideSpanCacheConfigHash;
  }

  /**
   * Draws bracket guides for visible range using span cache.
   */
  public void drawBracketGuidesForVisibleRange(Canvas canvas, int visibleStart, int visibleEnd) {
    if (!bracketGuides.isBracketGuidesEnabled || editor.isHeavyDrawSuppressed()) return;
    if (bracketGuideSpanCacheStartLine < 0 || bracketGuideSpanCacheEndLine < bracketGuideSpanCacheStartLine) return;
    if (bracketGuideSpanCount <= 0) return;

    int start = Math.max(visibleStart, bracketGuideSpanCacheStartLine);
    int end = Math.min(visibleEnd, bracketGuideSpanCacheEndLine);
    if (start > end) return;

    int maxPts = bracketGuideSpanCount * 4;
    if (bracketGuideSpanDrawPtsCapacity < maxPts) {
      bracketGuideSpanDrawPtsCapacity = Math.max(64, maxPts * 2);
      bracketGuideSpanDrawPts = new float[bracketGuideSpanDrawPtsCapacity];
    }

    int p = 0;
    for (int i = 0; i < bracketGuideSpanCount; i++) {
      int s = bracketGuideSpanStartLines[i];
      int e = bracketGuideSpanEndLines[i];
      if (e < start || s > end) continue;
      int drawStart = Math.max(start, s);
      int drawEnd = Math.min(end, e);
      if (drawStart > drawEnd) continue;
      float x = getGuideXApproxFromColumn(bracketGuideSpanColumns[i]);
      int segStart = drawStart;
      for (int line = drawStart; line <= drawEnd; line++) {
        String ln = bracketGuides.getLineTextForGuideScan(line, null, null);
        if (ln != null && !editor.isWhitespaceAtX(ln, line, x)) {
          if (segStart <= line - 1) {
            float top = editor.textRender.getDrawLineTop(segStart);
            float bottom = editor.textRender.getDrawLineTop(line - 1) + editor.textRender.lineHeight;
            if (SodiumEditor.DEBUG_RENDER_LOGS) {
              // frameLineCount++;
              // frameTokenCount++;
            }
            bracketGuideSpanDrawPts[p++] = x;
            bracketGuideSpanDrawPts[p++] = top;
            bracketGuideSpanDrawPts[p++] = x;
            bracketGuideSpanDrawPts[p++] = bottom;
          }
          segStart = line + 1;
        }
      }
      if (segStart <= drawEnd) {
        float top = editor.textRender.getDrawLineTop(segStart);
        float bottom = editor.textRender.getDrawLineTop(drawEnd) + editor.textRender.lineHeight;
        if (SodiumEditor.DEBUG_RENDER_LOGS) {
          // frameLineCount++;
          // frameTokenCount++;
        }
        bracketGuideSpanDrawPts[p++] = x;
        bracketGuideSpanDrawPts[p++] = top;
        bracketGuideSpanDrawPts[p++] = x;
        bracketGuideSpanDrawPts[p++] = bottom;
      }
    }

    if (p > 0) {
      canvas.drawLines(bracketGuideSpanDrawPts, 0, p, bracketGuides.bracketGuidePaint);
    }
  }

  /**
   * Builds the span cache asynchronously.
   */
  public void buildSpanCacheAsync(
      int startLine, int endLine, int v, int cfg, long startTime, java.util.Map<Integer, String> directLines) {
    BracketSpanScanState state = new BracketSpanScanState();
    SpanCollector collector = new SpanCollector(256);

    try {
      // Seed state from checkpoint stack
      BracketGuideCheckpoint checkpoint = bracketGuides.checkpoint;
      int checkpointIdx = checkpoint.getCheckpointIndexForLine(startLine);
      if (checkpointIdx >= 0) {
        BracketGuideState cp = checkpoint.getCheckpointState(checkpointIdx);
        state.inBlockComment = cp.inBlockComment;
        state.stringState = cp.stringState;
        for (BracketGuideToken token : cp.stack) {
          state.stack.push(token.column, startLine, startLine, token.bracket);
        }
      }

      for (int line = startLine; line <= endLine; line++) {
        if (editor.editOperators.editVersion.get() != v || bracketGuides.getBracketGuideCacheConfigHash() != cfg) {
          bracketGuideSpanBuildInProgress = false;
          return;
        }
        String text = bracketGuides.getLineTextForGuideScan(line, directLines, null);
        bracketGuides.scanner.scanLineForSpans(text, line, state, collector);
      }

      if (state.pendingParen) {
        int spanStart = state.pendingParenOpenLine + 1;
        int spanEnd = state.pendingParenCloseLine - 1;
        if (spanStart <= spanEnd) {
          collector.add(state.pendingParenColumn, spanStart, spanEnd, '(');
        }
        state.pendingParen = false;
      }

      // Close any remaining spans to endLine
      while (state.stack.size > 0) {
        int column = state.stack.topColumn();
        int guideStart = state.stack.topStartLine();
        char bracket = state.stack.topBracket();
        state.stack.pop();
        int spanStart = guideStart;
        int spanEnd = endLine;
        if (spanStart <= spanEnd) {
          collector.add(column, spanStart, spanEnd, bracket);
        }
      }
    } catch (Exception ignored) {
    }

    final int finalCount = collector.count;
    final int[] finalColumns = java.util.Arrays.copyOf(collector.columns, finalCount);
    final int[] finalStartLines = java.util.Arrays.copyOf(collector.startLines, finalCount);
    final int[] finalEndLines = java.util.Arrays.copyOf(collector.endLines, finalCount);
    final char[] finalBrackets = java.util.Arrays.copyOf(collector.brackets, finalCount);

    editor.post(() -> {
      bracketGuideSpanCount = 0;
      ensureSpanCapacity(finalCount);
      for (int i = 0; i < finalCount; i++) {
        addSpan(finalColumns[i], finalStartLines[i], finalEndLines[i], finalBrackets[i]);
      }
      bracketGuideSpanCacheStartLine = startLine;
      bracketGuideSpanCacheEndLine = endLine;
      bracketGuideSpanCacheEditVersion = v;
      bracketGuideSpanCacheConfigHash = cfg;
      bracketGuideSpanBuildInProgress = false;
      editor.invalidate();
    });
  }

  /**
   * Gets span cache start line.
   */
  public int getStartLine() {
    return bracketGuideSpanCacheStartLine;
  }

  /**
   * Gets span cache end line.
   */
  public int getEndLine() {
    return bracketGuideSpanCacheEndLine;
  }

  /**
   * Checks if span cache can be used for drawing.
   */
  public boolean canDraw() {
    return bracketGuideSpanCacheStartLine >= 0 && bracketGuideSpanCacheEndLine >= bracketGuideSpanCacheStartLine && bracketGuideSpanCount > 0;
  }
}
