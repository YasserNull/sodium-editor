package com.yn.sodiumeditor.core;

import android.graphics.Paint;
import com.yn.sodiumeditor.SodiumEditor;
import com.yn.sodiumeditor.state.WrapWordMetrics;
import com.yn.sodiumeditor.utils.WrapWordUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class WrapWordMapper {

  //================================================================================
  // Dependencies
  //================================================================================

  private final WrapWordMetrics metrics;
  private final WrapWordEngine engine;

  public WrapWordMapper(WrapWordMetrics metrics, WrapWordEngine engine) {
    this.metrics = metrics;
    this.engine = engine;
  }

  //================================================================================
  // Mapping
  //================================================================================

  public SodiumEditor.VisualLinePosition getVisualPositionForIndex(
      SodiumEditor view,
      int visualIndex,
      int widthPx) {
    if (!isWrapMetricsUsableForWindow(view, widthPx)) {
      if (view.wrapWordState.isWordWrapEnabled) {
        return getVisualPositionForIndexFallback(view, visualIndex, widthPx);
      }
      int line = view.mapVisibleIndexToGlobal(visualIndex);
      return new SodiumEditor.VisualLinePosition(line, 0);
    }

    int maxVisual = Math.max(0, metrics.totalWrapVisualLines - 1);
    int v = Math.max(0, Math.min(visualIndex, maxVisual));
    int line = engine.findLineForVisualIndex(v);
    int seg = v - metrics.wrapLinePrefix[line];
    return new SodiumEditor.VisualLinePosition(line, seg);
  }

  public SodiumEditor.VisualLinePosition getVisualPositionForIndexFallback(
      SodiumEditor view,
      int visualIndex,
      int widthPx) {
    int idx = Math.max(0, visualIndex);
    int baseLine = Math.max(0, view.windowStartLine);
    int baseVisual = baseLine;

    if (metrics.wrapLinePrefix != null &&
        metrics.wrapPrefixValidUpToLine >= baseLine &&
        baseLine < metrics.wrapLinePrefix.length) {
      baseVisual = metrics.wrapLinePrefix[baseLine];
    }

    int remaining = idx - baseVisual;
    if (remaining <= 0) {
      return new SodiumEditor.VisualLinePosition(baseLine, 0);
    }

    int line = baseLine;
    int windowEnd = view.getWindowEndLine();
    if (windowEnd < baseLine) windowEnd = baseLine;

    while (line <= windowEnd) {
      String text = view.getLineTextForRender(line);
      int[] starts = engine.getWrapStartsForLine(view, line, text, widthPx, view.editorConfig.paint);
      int segCount = Math.max(1, starts.length);

      if (remaining < segCount) {
        return new SodiumEditor.VisualLinePosition(
            line, Math.max(0, Math.min(remaining, segCount - 1)));
      }
      remaining -= segCount;
      line++;
    }

    return new SodiumEditor.VisualLinePosition(windowEnd, 0);
  }

  //================================================================================
  // Helpers
  //================================================================================

  public int getTotalVisualLineCount(SodiumEditor view, int visibleLineCount) {
    if (!view.wrapWordState.isWordWrapEnabled) {
      return view.getVisibleLineCount();
    }

    int widthPx = Math.max(1, Math.round(getWrapWidth(view)));
    if (!isWrapMetricsUsableForWindow(view, widthPx)) {
      int total = view.getLinesCount();
      if (total <= 0) total = getWindowLineCount(view);
      return Math.max(1, total);
    }

    return Math.max(1, metrics.totalWrapVisualLines);
  }

  public int getWrapRangeCount(int startLine, int endLine) {
    if (metrics.wrapLinePrefix == null) return 0;
    int total = metrics.wrapLinePrefix.length - 1;
    int s = Math.max(0, Math.min(startLine, total - 1));
    int e = Math.max(s, Math.min(endLine, total - 1));
    return metrics.wrapLinePrefix[e + 1] - metrics.wrapLinePrefix[s];
  }

  public int findLineForVisualIndex(int visualIndex) {
    return engine.findLineForVisualIndex(visualIndex);
  }

  //================================================================================
  // Window Helpers (Static)
  //================================================================================

  public static int getWindowTargetLine(SodiumEditor view) {
    synchronized (view.linesWindow) {
      return view.windowStartLine + view.linesWindow.size() - 1;
    }
  }

  public static int getWindowStartLineWithSnapshot(
      SodiumEditor view,
      ArrayList<String> out) {
    synchronized (view.linesWindow) {
      out.clear();
      out.addAll(view.linesWindow);
      return view.windowStartLine;
    }
  }

  public static int getWindowLineCount(SodiumEditor view) {
    synchronized (view.linesWindow) {
      return view.windowStartLine + view.linesWindow.size();
    }
  }

  public static int getWindowSize(SodiumEditor view) {
    synchronized (view.linesWindow) {
      return view.linesWindow.size();
    }
  }

  //================================================================================
  // Validation
  //================================================================================

  public boolean isWrapMetricsUsableForWindow(SodiumEditor view, int widthPx) {
    if (!view.wrapWordState.isWordWrapEnabled) return false;
    if (!metrics.wrapMetricsReady || metrics.wrapLinePrefix == null || metrics.wrapLineCounts == null) return false;
    if (metrics.wrapMetricsWidth != widthPx) return false;
    int total = view.getLinesCount();
    if (total <= 0) total = getWindowLineCount(view);
    if (total <= 0) return false;
    if (metrics.wrapLineCounts.length != total || metrics.wrapLinePrefix.length != total + 1) return false;
    int windowEnd = view.getWindowEndLine();
    return metrics.wrapPrefixValidUpToLine >= windowEnd;
  }

  public boolean isWrapMetricsUsableForLine(SodiumEditor view, int line, int widthPx) {
    if (!isWrapMetricsUsableForWindow(view, widthPx)) return false;
    return metrics.wrapPrefixValidUpToLine >= line;
  }

  //================================================================================
  // Wrap Width
  //================================================================================

  private float getWrapWidth(SodiumEditor view) {
    return Math.max(1f, view.getWidth() - view.getTextStartX());
  }
}
