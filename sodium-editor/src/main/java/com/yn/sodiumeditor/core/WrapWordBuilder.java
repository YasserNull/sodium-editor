package com.yn.sodiumeditor.core;

import android.graphics.Paint;
import android.widget.Scroller;
import com.yn.sodiumeditor.SodiumEditorView;
import com.yn.sodiumeditor.io.WrapWordDocument;
import com.yn.sodiumeditor.state.WrapWordMetrics;
import com.yn.sodiumeditor.state.WrapWordState;
import com.yn.sodiumeditor.utils.WrapWordUtils;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Map;

public final class WrapWordBuilder {

  //================================================================================
  // Dependencies
  //================================================================================

  private final WrapWordMetrics metrics;
  private final WrapWordState state;
  private final WrapWordEngine engine;
  private final WrapWordMapper mapper;
  private final WrapWordDocument document;

  public WrapWordBuilder(
      WrapWordMetrics metrics,
      WrapWordState state,
      WrapWordEngine engine,
      WrapWordMapper mapper,
      WrapWordDocument document) {
    this.metrics = metrics;
    this.state = state;
    this.engine = engine;
    this.mapper = mapper;
    this.document = document;
  }

  //================================================================================
  // Public API
  //================================================================================

  public void scheduleBuild(SodiumEditorView view) {
    if (!state.isWordWrapEnabled) return;
    if (shouldSuppressForSelectAll(view)) return;
    if (view.getWidth() <= 0) return;

    File sourceFile = view.sourceFile;
    if (sourceFile == null || !view.isIndexReady) {
      buildInMemory(view);
      return;
    }

    final int token = state.wrapMetricsToken.incrementAndGet();
    final int widthPx = Math.max(1, Math.round(getWrapWidth(view)));
    final Paint wrapPaint = new Paint(view.paint);
    state.wrapMetricsBuilding = true;

    view.ioHandler.post(() -> buildFromFile(view, token, widthPx, wrapPaint));
  }

  public void scheduleWrapMetricsSnapshotIfNeeded(SodiumEditorView view, int widthPx) {
    // For now, delegate to scheduleBuild - can be enhanced later
    scheduleBuild(view);
  }

  public void invalidate(boolean clearExisting, boolean scheduleRebuild) {
    engine.invalidateCache(-1);
    metrics.clearCache();
    state.wrapWidthPx = -1;
    state.wrapMetricsToken.incrementAndGet();
    state.wrapPrefixValidUpToLine = -1;
    metrics.wrapPrefixValidUpToLine = -1;

    if (clearExisting) {
      metrics.wrapLineCounts = null;
      metrics.wrapLinePrefix = null;
    }

    int currentLines = metrics.wrapLineCounts != null ? metrics.wrapLineCounts.length : 0;
    boolean sizeMismatch = (metrics.wrapLineCounts != null && metrics.wrapLineCounts.length != currentLines);
    boolean missing = (metrics.wrapLineCounts == null || metrics.wrapLinePrefix == null);

    if (clearExisting || sizeMismatch || missing) {
      metrics.wrapMetricsReady = false;
      metrics.totalWrapVisualLines = 0;
    } else {
      metrics.wrapMetricsReady = true;
    }

    if (state.isWordWrapEnabled && scheduleRebuild) {
      // Will be scheduled by caller
    }
  }

  public void requestPrefixRebuild(SodiumEditorView view) {
    if (!state.isWordWrapEnabled) return;
    if (view.zoomGestureHandler.isScaling() || view.zoomGestureHandler.isScaleInProgress()) {
      state.wrapPrefixRebuildPending = true;
      return;
    }
    schedulePrefixRebuildUpToWindow(view);
  }

  public void cancelPrefixRebuildForInteraction() {
    if (!state.wrapPrefixBuilding) return;
    state.cancelPrefixBuild();
  }

  public void cancelWorkForPriority() {
    if (!state.isWordWrapEnabled) return;
    state.resetTokens();
    state.cancelAllBuilds();
  }

  //================================================================================
  // Build Strategies
  //================================================================================

  private void buildInMemory(SodiumEditorView view) {
    int total = view.getLinesCount();
    if (total <= 0) total = WrapWordMapper.getWindowLineCount(view);
    if (total <= 0) {
      metrics.wrapLineCounts = null;
      metrics.wrapLinePrefix = null;
      metrics.totalWrapVisualLines = 0;
      metrics.wrapMetricsReady = true;
      return;
    }

    int widthPx = Math.max(1, Math.round(getWrapWidth(view)));
    int[] counts = new int[total];
    for (int i = 0; i < total; i++) counts[i] = 1;

    int start;
    ArrayList<String> snapshot = new ArrayList<>();
    start = WrapWordMapper.getWindowStartLineWithSnapshot(view, snapshot);

    if (!snapshot.isEmpty()) {
      for (int i = 0; i < snapshot.size(); i++) {
        int global = start + i;
        if (global < 0 || global >= total) continue;
        String line = snapshot.get(i);
        counts[global] = engine.computeWrapCount(line, widthPx, view.paint);
      }
    }

    int[] prefix = new int[total + 1];
    int running = 0;
    for (int i = 0; i < total; i++) {
      running += counts[i];
      prefix[i + 1] = running;
    }

    metrics.wrapLineCounts = counts;
    metrics.wrapLinePrefix = prefix;
    metrics.totalWrapVisualLines = running;
    metrics.wrapMetricsWidth = widthPx;
    metrics.wrapPrefixValidUpToLine = (view.windowStartLine == 0) ? (total - 1) : -1;
    metrics.wrapMetricsReady = true;
  }

  private void buildFromFile(SodiumEditorView view, int token, int widthPx, Paint wrapPaint) {
    final int total = view.getLinesCount();
    if (total <= 0) {
      view.post(() -> {
        if (token != state.wrapMetricsToken.get()) return;
        metrics.wrapLineCounts = null;
        metrics.wrapLinePrefix = null;
        metrics.totalWrapVisualLines = 0;
        metrics.wrapMetricsReady = true;
        state.wrapMetricsBuilding = false;
      });
      return;
    }

    final int[] counts = new int[total];
    for (int i = 0; i < total; i++) counts[i] = 1;

    try {
      document.processFileLines(view.sourceFile, total, new WrapWordDocument.LineCallback() {
        int lineIndex = 0;

        @Override
        public void onLine(int lineIndex, String line, boolean isModified) {
          if (token != state.wrapMetricsToken.get()) return;
          counts[lineIndex] = engine.computeWrapCount(line, widthPx, wrapPaint);
          this.lineIndex++;
        }

        @Override
        public void onComplete() {
          int[] prefix = new int[total + 1];
          int running = 0;
          for (int i = 0; i < total; i++) {
            running += counts[i];
            prefix[i + 1] = running;
          }
          final int runningFinal = running;
          final int totalFinal = total;

          view.post(() -> {
            if (token != state.wrapMetricsToken.get()) return;
            metrics.wrapLineCounts = counts;
            metrics.wrapLinePrefix = prefix;
            metrics.totalWrapVisualLines = runningFinal;
            metrics.wrapMetricsWidth = widthPx;
            metrics.wrapMetricsReady = true;
            metrics.wrapPrefixValidUpToLine = totalFinal - 1;
            state.wrapMetricsBuilding = false;
            view.postInvalidateOnAnimation();
          });
        }

        @Override
        public void onError(Exception e) {
          view.post(() -> {
            if (token != state.wrapMetricsToken.get()) return;
            metrics.wrapLineCounts = null;
            metrics.wrapLinePrefix = null;
            metrics.totalWrapVisualLines = 0;
            metrics.wrapMetricsReady = true;
            state.wrapMetricsBuilding = false;
          });
        }
      });
    } catch (IOException e) {
      view.post(() -> {
        if (token != state.wrapMetricsToken.get()) return;
        metrics.wrapLineCounts = null;
        metrics.wrapLinePrefix = null;
        metrics.totalWrapVisualLines = 0;
        metrics.wrapMetricsReady = true;
        state.wrapMetricsBuilding = false;
      });
    }
  }

  public void buildWrapMetricsForWindowSnapshot(SodiumEditorView view) {
    buildWindowSnapshot(view);
  }

  private void buildWindowSnapshot(SodiumEditorView view) {
    int total = view.getLinesCount();
    if (total <= 0) total = WrapWordMapper.getWindowLineCount(view);
    if (total <= 0) {
      metrics.wrapLineCounts = null;
      metrics.wrapLinePrefix = null;
      metrics.totalWrapVisualLines = 0;
      metrics.wrapMetricsReady = true;
      return;
    }

    int widthPx = Math.max(1, Math.round(getWrapWidth(view)));
    int[] counts;

    if (metrics.wrapLineCounts != null && metrics.wrapLineCounts.length == total) {
      counts = metrics.wrapLineCounts.clone();
    } else {
      counts = new int[total];
      for (int i = 0; i < total; i++) counts[i] = 1;
    }

    int start;
    ArrayList<String> snapshot = new ArrayList<>();
    start = WrapWordMapper.getWindowStartLineWithSnapshot(view, snapshot);

    if (!snapshot.isEmpty()) {
      for (int i = 0; i < snapshot.size(); i++) {
        int global = start + i;
        if (global < 0 || global >= total) continue;
        String line = snapshot.get(i);
        counts[global] = engine.computeWrapCount(line, widthPx, view.paint);
      }
    }

    int[] prefix = new int[total + 1];
    int running = 0;
    for (int i = 0; i < total; i++) {
      running += counts[i];
      prefix[i + 1] = running;
    }

    metrics.wrapLineCounts = counts;
    metrics.wrapLinePrefix = prefix;
    metrics.totalWrapVisualLines = running;
    metrics.wrapMetricsWidth = widthPx;
    metrics.wrapPrefixValidUpToLine = total - 1;
    metrics.wrapMetricsReady = true;
  }

  //================================================================================
  // Prefix Building
  //================================================================================

  public void schedulePrefixRebuildUpToWindow(SodiumEditorView view) {
    if (!state.isWordWrapEnabled) return;
    if (shouldSuppressForSelectAll(view)) return;

    int total = view.getLinesCount();
    if (total <= 0) return;

    int targetLine = WrapWordMapper.getWindowTargetLine(view);
    if (targetLine < 0) return;
    targetLine = Math.min(targetLine, total - 1);
    final int targetLineFinal = targetLine;

    int widthPx = Math.max(1, Math.round(getWrapWidth(view)));
    if (state.wrapPrefixBuilding && state.wrapPrefixWidth == widthPx && state.wrapPrefixTargetLine >= targetLineFinal) {
      return;
    }

    state.wrapPrefixBuilding = true;
    state.wrapPrefixWidth = widthPx;
    state.wrapPrefixTargetLine = targetLineFinal;

    WrapWordUtils.abortScrollAnimation(view.scrollManager.scroller);

    final int token = state.wrapPrefixToken.incrementAndGet();
    final int[] baseCounts =
        (metrics.wrapLineCounts != null && metrics.wrapLineCounts.length == total)
            ? metrics.wrapLineCounts.clone() : null;

    int anchorVisualIndex = Math.max(0, (int) (WrapWordUtils.getScrollY(view) / view.lineHeight));
    SodiumEditorView.VisualLinePosition anchorPos = mapper.getVisualPositionForIndex(view, anchorVisualIndex, widthPx);
    final int anchorLine = anchorPos.line;
    final int oldAnchorPrefix =
        (metrics.wrapLinePrefix != null && anchorLine >= 0 && anchorLine < metrics.wrapLinePrefix.length)
            ? metrics.wrapLinePrefix[anchorLine]
            : anchorLine;

    final Paint wrapPaint = new Paint(view.paint);

    view.ioHandler.post(() -> {
      if (token != state.wrapPrefixToken.get()) return;

      int[] counts;
      if (baseCounts != null) {
        counts = baseCounts;
      } else {
        counts = new int[total];
        for (int i = 0; i < total; i++) counts[i] = 1;
      }

      File sourceFile = view.sourceFile;
      if (sourceFile == null || !sourceFile.exists()) {
        int start;
        ArrayList<String> snapshot = new ArrayList<>();
        start = WrapWordMapper.getWindowStartLineWithSnapshot(view, snapshot);
        if (start == 0) {
          int end = Math.min(targetLineFinal, snapshot.size() - 1);
          for (int i = 0; i <= end; i++) {
            String line = snapshot.get(i);
            counts[i] = engine.computeWrapCount(line, widthPx, wrapPaint);
          }
        } else {
          view.post(() -> {
            if (token != state.wrapPrefixToken.get()) return;
            state.wrapPrefixBuilding = false;
          });
          return;
        }
      } else {
        try {
          document.processFileLines(sourceFile, total, new WrapWordDocument.LineCallback() {
            int lineIndex = 0;

            @Override
            public void onLine(int lineIndex, String line, boolean isModified) {
              if (token != state.wrapPrefixToken.get()) return;
              if (lineIndex <= targetLineFinal) {
                counts[lineIndex] = engine.computeWrapCount(line, widthPx, wrapPaint);
              }
              lineIndex++;
            }

            @Override
            public void onComplete() {}

            @Override
            public void onError(Exception e) {
              view.post(() -> {
                if (token != state.wrapPrefixToken.get()) return;
                state.wrapPrefixBuilding = false;
              });
            }
          });
        } catch (IOException e) {
          view.post(() -> {
            if (token != state.wrapPrefixToken.get()) return;
            state.wrapPrefixBuilding = false;
          });
        }
      }

      int[] prefix = new int[total + 1];
      int running = 0;
      for (int i = 0; i < total; i++) {
        running += counts[i];
        prefix[i + 1] = running;
      }
      final int runningFinal = running;
      final int newAnchorPrefix =
          (anchorLine >= 0 && anchorLine < prefix.length) ? prefix[anchorLine] : oldAnchorPrefix;
      final int deltaPrefix = newAnchorPrefix - oldAnchorPrefix;

      view.post(() -> {
        if (token != state.wrapPrefixToken.get()) return;
        if (Math.max(1, Math.round(getWrapWidth(view))) != widthPx) {
          state.wrapPrefixBuilding = false;
          return;
        }
        state.wrapPrefixBuilding = false;

        if (view.zoomGestureHandler.isZoomGestureActive()) {
          metrics.pendingWrapPrefixCounts = counts;
          metrics.pendingWrapPrefixPrefix = prefix;
          metrics.pendingWrapPrefixTotalVisualLines = runningFinal;
          metrics.pendingWrapPrefixWidthPx = widthPx;
          metrics.pendingWrapPrefixValidUpToLine =
              Math.max(metrics.wrapPrefixValidUpToLine, Math.min(targetLineFinal, total - 1));
          metrics.pendingApplyWrapPrefixUpdate = true;
          return;
        }

        metrics.wrapLineCounts = counts;
        metrics.wrapLinePrefix = prefix;
        metrics.totalWrapVisualLines = runningFinal;
        metrics.wrapMetricsWidth = widthPx;
        metrics.wrapMetricsReady = true;
        metrics.wrapPrefixValidUpToLine = Math.max(metrics.wrapPrefixValidUpToLine, Math.min(targetLineFinal, total - 1));

        if (deltaPrefix != 0) {
          WrapWordUtils.addScrollY(view, deltaPrefix * view.lineHeight);
          view.scrollManager.clampScrollY();
        }
        view.postInvalidateOnAnimation();
      });
    });
  }

  public void applyPendingPrefixUpdate(SodiumEditorView view) {
    if (!metrics.pendingApplyWrapPrefixUpdate) return;
    if (!state.isWordWrapEnabled) {
      metrics.pendingApplyWrapPrefixUpdate = false;
      metrics.pendingWrapPrefixCounts = null;
      metrics.pendingWrapPrefixPrefix = null;
      return;
    }
    if (view.zoomGestureHandler.isZoomGestureActive()) return;
    if (metrics.pendingWrapPrefixCounts == null || metrics.pendingWrapPrefixPrefix == null) {
      metrics.pendingApplyWrapPrefixUpdate = false;
      return;
    }

    int currentWidthPx = Math.max(1, Math.round(getWrapWidth(view)));
    if (metrics.pendingWrapPrefixWidthPx != currentWidthPx) {
      metrics.pendingApplyWrapPrefixUpdate = false;
      metrics.pendingWrapPrefixCounts = null;
      metrics.pendingWrapPrefixPrefix = null;
      return;
    }

    int anchorFirstVisual = Math.max(0, (int) (WrapWordUtils.getScrollY(view) / view.lineHeight));
    int widthPx = Math.max(1, Math.round(getWrapWidth(view)));
    SodiumEditorView.VisualLinePosition anchorPos = mapper.getVisualPositionForIndex(view, anchorFirstVisual, widthPx);
    int anchorLine = anchorPos.line;
    int anchorSeg = anchorPos.segment;

    metrics.applyPendingUpdate();

    if (anchorLine >= 0 && metrics.wrapLinePrefix != null && anchorLine < metrics.wrapLinePrefix.length) {
      int newAnchorFirstVisual = metrics.wrapLinePrefix[anchorLine] + Math.max(0, anchorSeg);
      int dv = newAnchorFirstVisual - anchorFirstVisual;
      if (dv != 0) {
        WrapWordUtils.addScrollY(view, dv * view.lineHeight);
        view.scrollManager.clampScrollY();
      }
    }
  }

  public void applyPendingPrefixUpdateForZoom(SodiumEditorView view) {
    applyPendingPrefixUpdate(view);
  }

  //================================================================================
  // Line Changes
  //================================================================================

  public void onLineContentChanged(SodiumEditorView view, int globalLine, String newText) {
    if (!state.isWordWrapEnabled) return;
    engine.invalidateCache(globalLine);

    int widthPx = Math.max(1, Math.round(getWrapWidth(view)));
    if (!metrics.wrapMetricsReady || metrics.wrapLineCounts == null || metrics.wrapLinePrefix == null || metrics.wrapMetricsWidth != widthPx) {
      invalidate(true, true);
      return;
    }
    if (globalLine < 0 || globalLine >= metrics.wrapLineCounts.length) {
      invalidate(true, true);
      return;
    }

    int newCount = engine.computeWrapCount(newText, widthPx, view.paint);
    int oldCount = metrics.wrapLineCounts[globalLine];
    if (newCount == oldCount) return;

    int delta = newCount - oldCount;
    metrics.wrapLineCounts[globalLine] = newCount;
    metrics.adjustPrefixFromLine(globalLine, delta);
    metrics.totalWrapVisualLines += delta;

    view.lineNumberRenderer.invalidateCache();
    view.invalidate();
  }

  public void onLineCountChanged(SodiumEditorView view) {
    if (state.isWordWrapEnabled) {
      invalidate(true, true);
    }
    view.lineNumberRenderer.invalidateCache();
  }

  //================================================================================
  // Validation
  //================================================================================

  public boolean isMetricsUsableForWindow(SodiumEditorView view, int widthPx) {
    if (!state.isWordWrapEnabled) return false;
    if (!metrics.wrapMetricsReady || metrics.wrapLinePrefix == null || metrics.wrapLineCounts == null) return false;
    if (metrics.wrapMetricsWidth != widthPx) return false;
    int total = view.getLinesCount();
    if (total <= 0) total = WrapWordMapper.getWindowLineCount(view);
    if (total <= 0) return false;
    if (metrics.wrapLineCounts.length != total || metrics.wrapLinePrefix.length != total + 1) return false;
    int windowEnd = view.getWindowEndLine();
    return metrics.wrapPrefixValidUpToLine >= windowEnd;
  }

  public boolean isMetricsUsableForLine(SodiumEditorView view, int line, int widthPx) {
    if (!isMetricsUsableForWindow(view, widthPx)) return false;
    return metrics.wrapPrefixValidUpToLine >= line;
  }

  public boolean shouldSuppressForSelectAll(SodiumEditorView view) {
    if (!state.isWordWrapEnabled ||
        (!view.selectionState.isSelectAllActive() && !view.selectionState.isEntireFileSelected())) {
      return false;
    }
    int widthPx = Math.max(1, Math.round(getWrapWidth(view)));
    return !isMetricsUsableForWindow(view, widthPx);
  }

  //================================================================================
  // Patch Visual Range
  //================================================================================

  public boolean patchWrapMetricsForVisualRange(
      SodiumEditorView view,
      int firstVisualIndex,
      int lastVisualIndex,
      Map<Integer, String> directLines,
      int widthPx) {
    if (!state.isWordWrapEnabled) return false;
    if (!metrics.wrapMetricsReady || metrics.wrapLineCounts == null || metrics.wrapLinePrefix == null) return false;
    if (metrics.wrapMetricsWidth != widthPx) return false;
    if (metrics.wrapLineCounts.length + 1 != metrics.wrapLinePrefix.length) return false;

    final int anchorFirstVisual = firstVisualIndex;
    final SodiumEditorView.VisualLinePosition anchorPos = mapper.getVisualPositionForIndex(view, anchorFirstVisual, widthPx);
    final int anchorLine = anchorPos.line;
    final int anchorSeg = anchorPos.segment;

    boolean changed = false;
    int v = Math.max(0, firstVisualIndex);
    int vEnd = Math.max(v, lastVisualIndex);

    for (; v <= vEnd; v++) {
      SodiumEditorView.VisualLinePosition pos = mapper.getVisualPositionForIndex(view, v, widthPx);
      int line = pos.line;
      if (line < 0 || line >= metrics.wrapLineCounts.length) break;

      String text = view.getLineTextForRenderWithDirect(line, directLines);
      int[] starts = engine.getWrapStartsForLine(view, line, text, widthPx, view.paint);
      int newCount = Math.max(1, starts.length);
      int oldCount = metrics.wrapLineCounts[line];

      if (newCount == oldCount) continue;

      int delta = newCount - oldCount;
      metrics.wrapLineCounts[line] = newCount;
      metrics.adjustPrefixFromLine(line, delta);
      metrics.totalWrapVisualLines += delta;
      changed = true;
    }

    if (!changed) return false;

    if (anchorLine >= 0 && anchorLine < metrics.wrapLinePrefix.length) {
      int newAnchorFirstVisual = metrics.wrapLinePrefix[anchorLine] + Math.max(0, anchorSeg);
      int dv = newAnchorFirstVisual - anchorFirstVisual;
      if (dv != 0) {
        view.scrollManager.scrollY += dv * view.lineHeight;
        view.scrollManager.clampScrollY();
      }
    }

    return true;
  }

  //================================================================================
  // Helpers
  //================================================================================

  private float getWrapWidth(SodiumEditorView view) {
    return Math.max(1f, view.getWidth() - view.getTextStartX());
  }

  private static void abortScrollAnimation(Scroller scroller) {
    if (!scroller.isFinished()) scroller.abortAnimation();
  }
}
