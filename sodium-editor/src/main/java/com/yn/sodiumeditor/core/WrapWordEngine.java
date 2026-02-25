package com.yn.sodiumeditor.core;

import android.graphics.Paint;
import com.yn.sodiumeditor.SodiumEditorView;
import com.yn.sodiumeditor.WhitespaceGuideManager;
import com.yn.sodiumeditor.state.WrapWordMetrics;
import com.yn.sodiumeditor.utils.WrapWordUtils;
import java.util.ArrayList;

public final class WrapWordEngine {

  //================================================================================
  // Constants
  //================================================================================

  public static final int DEFAULT_TAB_SIZE_SPACES = 4;
  public static final String INDICATOR_TEXT = "\u21A9"; // ↩

  //================================================================================
  // Dependencies
  //================================================================================

  private final WrapWordMetrics metrics;
  private WhitespaceGuideManager whitespaceGuideManager;

  public WrapWordEngine(WrapWordMetrics metrics, WhitespaceGuideManager whitespaceGuideManager) {
    this.metrics = metrics;
    this.whitespaceGuideManager = whitespaceGuideManager;
  }

  public void setWhitespaceGuideManager(WhitespaceGuideManager whitespaceGuideManager) {
    this.whitespaceGuideManager = whitespaceGuideManager;
  }

  //================================================================================
  // Core Wrapping - الخط الأساسي
  //================================================================================

  public int[] computeWrapStarts(String line, int widthPx, Paint paint) {
    if (line == null) return new int[] {0};
    int len = line.length();
    if (len == 0) return new int[] {0};

    if (shouldUseBreakTextWrap(line)) {
      return computeWrapStartsWithBreakText(line, widthPx, paint);
    }

    float[] widths = new float[len];
    paint.getTextWidths(line, 0, len, widths);
    float[] adv = new float[len];
    for (int i = 0; i < len; i++) {
      adv[i] = whitespaceGuideManager.getCharAdvanceWidth(
          line.charAt(i), widths[i], paint, DEFAULT_TAB_SIZE_SPACES);
    }

    ArrayList<Integer> starts = new ArrayList<>();
    starts.add(0);
    int i = 0;

    while (i < len) {
      float w = 0f;
      int lastBreak = -1;
      int j = i;

      for (; j < len; j++) {
        float a = adv[j];
        if (w + a > widthPx && j > i) break;
        w += a;
        if (Character.isWhitespace(line.charAt(j))) {
          lastBreak = j;
        }
      }

      if (j >= len) break;

      int next;
      if (lastBreak >= i) {
        next = lastBreak + 1;
      } else {
        next = Math.max(i + 1, j);
      }
      if (next <= i) next = i + 1;

      starts.add(next);
      i = next;
    }

    int[] out = new int[starts.size()];
    for (int k = 0; k < starts.size(); k++) out[k] = starts.get(k);
    return out;
  }

  public int computeWrapCount(String line, int widthPx, Paint paint) {
    int[] starts = computeWrapStarts(line, widthPx, paint);
    return Math.max(1, starts.length);
  }

  //================================================================================
  // BreakText Fallback
  //================================================================================

  private int[] computeWrapStartsWithBreakText(String line, int widthPx, Paint paint) {
    int len = line.length();
    ArrayList<Integer> starts = new ArrayList<>();
    starts.add(0);
    int i = 0;

    while (i < len) {
      int count = paint.breakText(line, i, len, true, widthPx, null);
      if (count <= 0) count = 1;
      int end = i + count;

      if (end >= len) break;

      int lastBreak = -1;
      for (int j = end - 1; j >= i; j--) {
        if (Character.isWhitespace(line.charAt(j))) {
          lastBreak = j;
          break;
        }
      }

      int next;
      if (lastBreak >= i) {
        next = lastBreak + 1;
      } else {
        next = end;
      }
      if (next <= i) next = i + 1;

      starts.add(next);
      i = next;
    }

    int[] out = new int[starts.size()];
    for (int k = 0; k < starts.size(); k++) out[k] = starts.get(k);
    return out;
  }

  private boolean shouldUseBreakTextWrap(String line) {
    return line.indexOf('\t') < 0;
  }

  //================================================================================
  // Segment Helpers
  //================================================================================

  public int getWrapSegmentIndexForChar(int[] starts, int charIndex) {
    if (starts == null || starts.length == 0) return 0;
    int idx = 0;
    for (int i = 0; i < starts.length; i++) {
      if (starts[i] <= charIndex) idx = i;
      else break;
    }
    return idx;
  }

  public int getWrapSegmentStart(int[] starts, int segIndex) {
    if (starts == null || starts.length == 0) return 0;
    if (segIndex <= 0) return starts[0];
    return starts[Math.min(segIndex, starts.length - 1)];
  }

  public int getWrapSegmentEnd(int[] starts, int segIndex, int lineLength) {
    if (starts == null || starts.length == 0) return lineLength;
    int next = segIndex + 1;
    if (next >= 0 && next < starts.length) return starts[next];
    return lineLength;
  }

  //================================================================================
  // Wrap Cache Management
  //================================================================================

  public int[] getWrapStartsForLine(
      SodiumEditorView view,
      int globalLine,
      String line,
      int widthPx,
      Paint paint) {
    if (metrics.wrapWidthPx != widthPx) {
      metrics.wrapWidthPx = widthPx;
      metrics.clearCache();
    }

    boolean cacheable = isWrapCacheableForLine(view, globalLine);
    if (!cacheable) {
      metrics.wrapCache.remove(globalLine);
      return computeWrapStarts(line, widthPx, paint);
    }

    int[] cached = metrics.wrapCache.get(globalLine);
    if (cached != null) return cached;

    int[] starts = computeWrapStarts(line, widthPx, paint);
    metrics.wrapCache.put(globalLine, starts);
    return starts;
  }

  public boolean isWrapCacheableForLine(SodiumEditorView view, int globalLine) {
    int windowStart = view.windowStartLine;
    int windowSize = getWindowSize(view);
    if (globalLine >= windowStart && globalLine < windowStart + windowSize) {
      return true;
    }
    return WrapWordUtils.isModifiedLine(view.modifiedLines, globalLine);
  }

  public void invalidateCache(int globalLine) {
    metrics.wrapCache.remove(globalLine);
  }

  //================================================================================
  // Visual Position Calculation
  //================================================================================

  public int findLineForVisualIndex(int visualIndex) {
    if (metrics.wrapLinePrefix == null || metrics.wrapLinePrefix.length == 0) return 0;

    int lo = 0;
    int hi = metrics.wrapLinePrefix.length - 1;

    while (lo < hi) {
      int mid = (lo + hi) >>> 1;
      if (metrics.wrapLinePrefix[mid] <= visualIndex) {
        lo = mid + 1;
      } else {
        hi = mid;
      }
    }

    int line = Math.max(0, lo - 1);
    return Math.min(line, metrics.wrapLinePrefix.length - 2);
  }

  public WrapWordUtils.VisualLinePosition getVisualPositionForIndex(int visualIndex) {
    int line = findLineForVisualIndex(visualIndex);
    int seg = visualIndex - metrics.wrapLinePrefix[line];
    return new WrapWordUtils.VisualLinePosition(line, seg);
  }

  //================================================================================
  // Window Helpers
  //================================================================================

  private static int getWindowSize(SodiumEditorView view) {
    synchronized (view.linesWindow) {
      return view.linesWindow.size();
    }
  }
}
