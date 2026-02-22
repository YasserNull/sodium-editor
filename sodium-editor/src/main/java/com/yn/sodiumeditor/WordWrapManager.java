package com.yn.sodiumeditor;

import android.graphics.Paint;
import java.util.HashMap;
import java.util.concurrent.atomic.AtomicInteger;
import androidx.annotation.Nullable;

public final class WordWrapManager {
  public static final int DEFAULT_TAB_SIZE_SPACES = 4;
  static final String WORD_WRAP_INDICATOR_TEXT = "\u21A9"; // ↩
  int wrapWidthPx = -1;
  final HashMap<Integer, int[]> wrapCache = new HashMap<>();
  public volatile int[] wrapLineCounts = null;
  public volatile int[] wrapLinePrefix = null;
  volatile int wrapPrefixValidUpToLine = -1;
  volatile int totalWrapVisualLines = 0;
  public volatile boolean wrapMetricsReady = false;
  volatile int wrapMetricsWidth = -1;
  final AtomicInteger wrapMetricsToken = new AtomicInteger(0);
  volatile boolean wrapMetricsBuilding = false;
  final AtomicInteger wrapSnapshotToken = new AtomicInteger(0);
  volatile boolean wrapSnapshotBuilding = false;
  volatile int wrapSnapshotWidth = -1;
  volatile int wrapSnapshotStart = -1;
  volatile int wrapSnapshotSize = -1;
  final AtomicInteger wrapPrefixToken = new AtomicInteger(0);
  public volatile boolean wrapPrefixBuilding = false;
  volatile int wrapPrefixWidth = -1;
  volatile int wrapPrefixTargetLine = -1;
  public boolean wrapPrefixRebuildPending = false;

  public boolean isWordWrapEnabled = false;
  boolean isWordWrapIndicatorEnabled = false;
  final Paint wordWrapIndicatorPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  float wordWrapIndicatorPadPx = 0f;
  float wordWrapIndicatorWidth = 0f;
  float wordWrapIndicatorTextScale = 0.85f;

  @Nullable int[] pendingWrapPrefixCounts = null;
  @Nullable int[] pendingWrapPrefixPrefix = null;
  int pendingWrapPrefixTotalVisualLines = 0;
  int pendingWrapPrefixWidthPx = -1;
  int pendingWrapPrefixValidUpToLine = -1;
  boolean pendingApplyWrapPrefixUpdate = false;

  public float getWrapWidth(SodiumEditorView view) {
    return Math.max(1f, view.getWidth() - view.getTextStartX());
  }

  void setWordWrapEnabled(SodiumEditorView view, boolean enabled) {
    if (isWordWrapEnabled == enabled) return;
    isWordWrapEnabled = enabled;
    invalidateWrapMetrics(view);
    if (enabled) {
      view.scrollManager.scrollX = 0f;
      view.scrollManager.clampScrollX();
      view.clearStreamedLineCaches();
      view.reloadWindowAroundVisible(false);
    }
    view.requestLayout();
    view.invalidate();
  }

  void setWordWrapIndicatorEnabled(SodiumEditorView view, boolean enabled) {
    if (isWordWrapIndicatorEnabled == enabled) return;
    isWordWrapIndicatorEnabled = enabled;
    view.invalidate();
  }

  void setWordWrapIndicatorColor(SodiumEditorView view, int color) {
    setIndicatorColor(color);
    view.invalidate();
  }

  void applyPendingWrapPrefixUpdateIfAny(SodiumEditorView view) {
    if (!pendingApplyWrapPrefixUpdate) return;
    if (!isWordWrapEnabled) {
      pendingApplyWrapPrefixUpdate = false;
      pendingWrapPrefixCounts = null;
      pendingWrapPrefixPrefix = null;
      return;
    }
    if (view.zoomManager.isZoomGestureActive()) return;
    if (pendingWrapPrefixCounts == null || pendingWrapPrefixPrefix == null) {
      pendingApplyWrapPrefixUpdate = false;
      return;
    }
    // Only apply if the wrap width still matches; otherwise a new rebuild will be scheduled.
    int currentWidthPx = Math.max(1, Math.round(getWrapWidth(view)));
    if (pendingWrapPrefixWidthPx != currentWidthPx) {
      pendingApplyWrapPrefixUpdate = false;
      pendingWrapPrefixCounts = null;
      pendingWrapPrefixPrefix = null;
      return;
    }

    // Keep the current top visual line anchored while swapping in the new prefix arrays.
    int anchorFirstVisual = Math.max(0, (int) (getScrollY(view) / view.lineHeight));
    SodiumEditorView.VisualLinePosition anchorPos = getVisualPositionForIndex(view, anchorFirstVisual);
    int anchorLine = anchorPos.line;
    int anchorSeg = anchorPos.segment;

    wrapLineCounts = pendingWrapPrefixCounts;
    wrapLinePrefix = pendingWrapPrefixPrefix;
    totalWrapVisualLines = pendingWrapPrefixTotalVisualLines;
    wrapMetricsWidth = pendingWrapPrefixWidthPx;
    wrapMetricsReady = true;
    wrapPrefixValidUpToLine = Math.max(wrapPrefixValidUpToLine, pendingWrapPrefixValidUpToLine);

    pendingApplyWrapPrefixUpdate = false;
    pendingWrapPrefixCounts = null;
    pendingWrapPrefixPrefix = null;

    if (anchorLine >= 0 && wrapLinePrefix != null && anchorLine < wrapLinePrefix.length) {
      int newAnchorFirstVisual = wrapLinePrefix[anchorLine] + Math.max(0, anchorSeg);
      int dv = newAnchorFirstVisual - anchorFirstVisual;
      if (dv != 0) {
        addScrollY(view, dv * view.lineHeight);
        view.clampScrollY();
      }
    }
  }

  public void applyPendingWrapPrefixUpdateForZoom(SodiumEditorView view) {
    applyPendingWrapPrefixUpdateIfAny(view);
  }

  public void scheduleWrapPrefixRebuildUpToWindow(SodiumEditorView view) {
    if (!isWordWrapEnabled) return;
    if (shouldSuppressWrapMetricsForFastSelectAll(view)) return;
    int total = view.getLinesCount();
    if (total <= 0) return;

    int targetLine = getWindowTargetLine(view);
    if (targetLine < 0) return;
    targetLine = Math.min(targetLine, total - 1);
    final int targetLineFinal = targetLine;

    int widthPx = Math.max(1, Math.round(getWrapWidth(view)));
    if (wrapPrefixBuilding && wrapPrefixWidth == widthPx && wrapPrefixTargetLine >= targetLineFinal) return;

    wrapPrefixBuilding = true;
    wrapPrefixWidth = widthPx;
    wrapPrefixTargetLine = targetLineFinal;

    abortScrollAnimation(view);

    final int token = wrapPrefixToken.incrementAndGet();
    final int[] baseCounts =
        (wrapLineCounts != null && wrapLineCounts.length == total) ? wrapLineCounts.clone() : null;

    int anchorVisualIndex = Math.max(0, (int) (getScrollY(view) / view.lineHeight));
    SodiumEditorView.VisualLinePosition anchorPos = getVisualPositionForIndex(view, anchorVisualIndex);
    final int anchorLine = anchorPos.line;
    final int oldAnchorPrefix =
        (wrapLinePrefix != null && anchorLine >= 0 && anchorLine < wrapLinePrefix.length)
            ? wrapLinePrefix[anchorLine]
            : anchorLine;

    final Paint wrapPaint = new Paint(view.paint);

    view.ioHandler.post(
        () -> {
          if (token != wrapPrefixToken.get()) return;

          int[] counts;
          if (baseCounts != null) {
            counts = baseCounts;
          } else {
            counts = new int[total];
            for (int i = 0; i < total; i++) counts[i] = 1;
          }

          java.io.File sourceFile = view.sourceFile;
          if (sourceFile == null || !sourceFile.exists()) {
            int start;
            java.util.ArrayList<String> snapshot = new java.util.ArrayList<>();
            start = getWindowStartLineWithSnapshot(view, snapshot);
            if (start == 0) {
              int end = Math.min(targetLineFinal, snapshot.size() - 1);
              for (int i = 0; i <= end; i++) {
                String line = snapshot.get(i);
                counts[i] = this.computeWrapCountForLine(view, line, widthPx, wrapPaint, false);
              }
            } else {
              view.post(
                  () -> {
                    if (token != wrapPrefixToken.get()) return;
                    wrapPrefixBuilding = false;
                  });
              return;
            }
          } else {
            java.io.BufferedReader br = null;
            try {
              br = view.reopenReaderAtStart();
              int lineIndex = 0;
              while (lineIndex <= targetLineFinal) {
                if (token != wrapPrefixToken.get()) return;
                String fileLine = (br != null) ? br.readLine() : null;
                String line = fileLine == null ? "" : fileLine;
                String mod = getModifiedLine(view, lineIndex);
                if (mod != null) line = mod;
                counts[lineIndex] = this.computeWrapCountForLine(view, line, widthPx, wrapPaint, false);
                lineIndex++;
                if (fileLine == null && mod == null) {
                  while (lineIndex <= targetLineFinal) {
                    counts[lineIndex] = 1;
                    lineIndex++;
                  }
                  break;
                }
              }
            } catch (Exception ignored) {
              view.post(
                  () -> {
                    if (token != wrapPrefixToken.get()) return;
                    wrapPrefixBuilding = false;
                  });
              return;
            } finally {
              try {
                if (br != null) br.close();
              } catch (Exception ignored) {
              }
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

          view.post(
              () -> {
                if (token != wrapPrefixToken.get()) return;
                if (Math.max(1, Math.round(getWrapWidth(view))) != widthPx) {
                  wrapPrefixBuilding = false;
                  return;
                }
                wrapPrefixBuilding = false;
                if (view.zoomManager.isZoomGestureActive()) {
                  pendingWrapPrefixCounts = counts;
                  pendingWrapPrefixPrefix = prefix;
                  pendingWrapPrefixTotalVisualLines = runningFinal;
                  pendingWrapPrefixWidthPx = widthPx;
                  pendingWrapPrefixValidUpToLine =
                      Math.max(wrapPrefixValidUpToLine, Math.min(targetLineFinal, total - 1));
                  pendingApplyWrapPrefixUpdate = true;
                  return;
                }
                wrapLineCounts = counts;
                wrapLinePrefix = prefix;
                totalWrapVisualLines = runningFinal;
                wrapMetricsWidth = widthPx;
                wrapMetricsReady = true;
                wrapPrefixValidUpToLine = Math.max(wrapPrefixValidUpToLine, Math.min(targetLineFinal, total - 1));
                if (deltaPrefix != 0) {
                  addScrollY(view, deltaPrefix * view.lineHeight);
                  view.clampScrollY();
                }
                view.postInvalidateOnAnimation();
              });
        });
  }

  void initIndicatorPaint(Paint basePaint, float density) {
    wordWrapIndicatorPadPx = 4f * density;
    wordWrapIndicatorPaint.setColor(0xFF9E9E9E);
    wordWrapIndicatorPaint.setAlpha(180);
    wordWrapIndicatorPaint.setTextAlign(Paint.Align.LEFT);
    wordWrapIndicatorPaint.setTextSize(basePaint.getTextSize() * wordWrapIndicatorTextScale);
    wordWrapIndicatorPaint.setTypeface(basePaint.getTypeface());
    wordWrapIndicatorWidth = wordWrapIndicatorPaint.measureText(WORD_WRAP_INDICATOR_TEXT);
  }
  int clampSegmentEndForWrapIndicator(SodiumEditorView view, String line, int segStart, int segEnd, int wrapWidthPx) {
    if (line == null || wrapWidthPx <= 0) return segEnd;
    float available =
        wrapWidthPx - this.wordWrapIndicatorWidth - this.wordWrapIndicatorPadPx;
    if (available <= 0f) return segStart;
    int end = Math.max(segStart, Math.min(segEnd, line.length()));
    while (end > segStart) {
      float width =
          view.whitespaceGuideManager.measureTextWithVisualSpaces(view, line, segStart, end, view.paint);
      if (width <= available) break;
      end--;
    }
    return end;
  }
  
  void setIndicatorColor(int color) {
    wordWrapIndicatorPaint.setColor(color);
  }

  void updateIndicatorTextSize(float baseTextSizePx, float targetPx) {
    if (baseTextSizePx > 0f && targetPx > 0f) {
      wordWrapIndicatorTextScale = targetPx / baseTextSizePx;
    } else {
      wordWrapIndicatorTextScale = 0.85f;
    }
    wordWrapIndicatorPaint.setTextSize(baseTextSizePx * wordWrapIndicatorTextScale);
    wordWrapIndicatorWidth = wordWrapIndicatorPaint.measureText(WORD_WRAP_INDICATOR_TEXT);
  }

  void updateIndicatorPaintForTextSize(float textSizePx, Paint basePaint) {
    wordWrapIndicatorPaint.setTextSize(textSizePx * wordWrapIndicatorTextScale);
    wordWrapIndicatorPaint.setTypeface(basePaint.getTypeface());
    wordWrapIndicatorWidth = wordWrapIndicatorPaint.measureText(WORD_WRAP_INDICATOR_TEXT);
  }

  void updateIndicatorTypeface(Paint basePaint) {
    wordWrapIndicatorPaint.setTypeface(basePaint.getTypeface());
    wordWrapIndicatorWidth = wordWrapIndicatorPaint.measureText(WORD_WRAP_INDICATOR_TEXT);
  }

  public void invalidateWrapMetrics(SodiumEditorView view) {
    invalidateWrapMetrics(view, true, true);
  }

  public void invalidateWrapMetrics(SodiumEditorView view, boolean clearExisting) {
    invalidateWrapMetrics(view, clearExisting, true);
  }

  public void invalidateWrapMetrics(SodiumEditorView view, boolean clearExisting, boolean scheduleFullRebuild) {
    wrapCache.clear();
    wrapWidthPx = -1;
    wrapMetricsWidth = -1;
    wrapMetricsToken.incrementAndGet();
    wrapPrefixValidUpToLine = -1;

    if (clearExisting) {
      wrapLineCounts = null;
      wrapLinePrefix = null;
    }

    int currentLines = view.getLinesCount();
    if (currentLines <= 0) currentLines = getWindowLineCount(view);

    boolean sizeMismatch = (wrapLineCounts != null && wrapLineCounts.length != currentLines);
    boolean missing = (wrapLineCounts == null || wrapLinePrefix == null);

    if (clearExisting || sizeMismatch || missing) {
      buildWrapMetricsForWindowSnapshot(view);

      if (wrapLineCounts == null) {
        wrapMetricsReady = false;
        totalWrapVisualLines = 0;
      } else {
        wrapMetricsReady = true;
      }
    } else {
      wrapMetricsReady = true;
    }

    if (isWordWrapEnabled) {
      if (scheduleFullRebuild) {
        scheduleWrapMetricsBuild(view);
      } else {
        int widthPx = Math.max(1, Math.round(getWrapWidth(view)));
        scheduleWrapMetricsSnapshotIfNeeded(view, widthPx);
        scheduleWrapPrefixRebuildUpToWindow(view);
      }
    }
  }

  public void requestWrapPrefixRebuild(SodiumEditorView view) {
    if (!isWordWrapEnabled) return;
    if (view.zoomManager.isScaling() || view.zoomManager.isScaleInProgress()) {
      wrapPrefixRebuildPending = true;
      return;
    }
    scheduleWrapPrefixRebuildUpToWindow(view);
  }

  void cancelWrapPrefixRebuildForInteraction() {
    if (!wrapPrefixBuilding) return;
    wrapPrefixToken.incrementAndGet();
    wrapPrefixBuilding = false;
    wrapPrefixRebuildPending = true;
  }

  void cancelWrapWorkForPriority(SodiumEditorView view) {
    if (!isWordWrapEnabled) return;
    wrapMetricsToken.incrementAndGet();
    wrapSnapshotToken.incrementAndGet();
    wrapPrefixToken.incrementAndGet();
    wrapMetricsBuilding = false;
    wrapSnapshotBuilding = false;
    wrapPrefixBuilding = false;
  }

  public boolean shouldSuppressWrapMetricsForFastSelectAll(SodiumEditorView view) {
    if (!isWordWrapEnabled
        || (!view.selectionManager.isSelectAllActive() && !view.selectionManager.isEntireFileSelected())) {
      return false;
    }
    int widthPx = Math.max(1, Math.round(getWrapWidth(view)));
    return !isWrapMetricsUsableForWindow(view, widthPx);
  }

  public void scheduleWrapMetricsSnapshotIfNeeded(SodiumEditorView view, int widthPx) {
    if (shouldSuppressWrapMetricsForFastSelectAll(view)) return;
    int start;
    int size;
    java.util.ArrayList<String> snapshot = new java.util.ArrayList<>();
    start = getWindowStartLineWithSnapshot(view, snapshot);
    size = snapshot.size();
    if (size <= 0) return;

    if (wrapSnapshotBuilding
        && wrapSnapshotWidth == widthPx
        && wrapSnapshotStart == start
        && wrapSnapshotSize == size) {
      return;
    }

    wrapSnapshotWidth = widthPx;
    wrapSnapshotStart = start;
    wrapSnapshotSize = size;
    wrapSnapshotBuilding = true;
    final int token = wrapSnapshotToken.incrementAndGet();
    final Paint wrapPaint = new Paint(view.paint);

    view.ioHandler.post(
        () -> {
          int total = view.getLinesCount();
          if (total <= 0) total = start + size;
          if (total <= 0) {
            view.post(
                () -> {
                  if (token == wrapSnapshotToken.get()) {
                    wrapMetricsReady = true;
                    wrapMetricsBuilding = false;
                    wrapSnapshotBuilding = false;
                  }
                });
            return;
          }

          int[] counts;
          boolean widthChanged = (wrapMetricsWidth != widthPx);
          if (wrapLineCounts == null || wrapLineCounts.length != total || widthChanged) {
            counts = new int[total];
            for (int i = 0; i < total; i++) counts[i] = 1;
          } else {
            counts = wrapLineCounts.clone();
          }

                      int current_i = 0;
                      for (String line : snapshot) {
                        int global = start + current_i;
                        if (global < 0 || global >= total) continue;
                        counts[global] = this.computeWrapCountForLine(view, line, widthPx, wrapPaint, false);
                        current_i++;
                      }
          int[] prefix = new int[total + 1];
          int running = 0;
          for (int i = 0; i < total; i++) {
            running += counts[i];
            prefix[i + 1] = running;
          }
          final int runningFinal = running;

          view.post(
              () -> {
                if (token != wrapSnapshotToken.get()) return;
                wrapLineCounts = counts;
                wrapLinePrefix = prefix;
                totalWrapVisualLines = runningFinal;
                wrapMetricsWidth = widthPx;
                wrapMetricsReady = true;
                wrapSnapshotBuilding = false;
                view.postInvalidateOnAnimation();
              });
        });
  }

  public void buildWrapMetricsForWindowSnapshot(SodiumEditorView view) {
    int total = view.getLinesCount();
    if (total <= 0) total = getWindowLineCount(view);
    if (total <= 0) {
      wrapLineCounts = null;
      wrapLinePrefix = null;
      totalWrapVisualLines = 0;
      wrapMetricsReady = true;
      return;
    }

    int widthPx = Math.max(1, Math.round(getWrapWidth(view)));
    int[] counts;

    if (wrapLineCounts != null && wrapLineCounts.length == total) {
      counts = wrapLineCounts.clone();
    } else {
      counts = new int[total];
      for (int i = 0; i < total; i++) counts[i] = 1;
    }

    int start;
    java.util.ArrayList<String> snapshot = new java.util.ArrayList<>();
    start = getWindowStartLineWithSnapshot(view, snapshot);

          if (!snapshot.isEmpty()) {
            for (int i = 0; i < snapshot.size(); i++) {
              int global = start + i;
              if (global < 0 || global >= total) continue;
              String line = snapshot.get(i);
              counts[global] = this.computeWrapCountForLine(view, line, widthPx, view.paint, true);
            }
          }
    int[] prefix = new int[total + 1];
    int running = 0;
    for (int i = 0; i < total; i++) {
      running += counts[i];
      prefix[i + 1] = running;
    }

    wrapLineCounts = counts;
    wrapLinePrefix = prefix;
    totalWrapVisualLines = running;
    wrapMetricsWidth = widthPx;
    wrapPrefixValidUpToLine = total - 1;
    wrapMetricsReady = true;
  }

  void scheduleWrapMetricsBuild(SodiumEditorView view) {
    if (!isWordWrapEnabled) return;
    if (shouldSuppressWrapMetricsForFastSelectAll(view)) return;
    if (view.getWidth() <= 0) return;
    java.io.File sourceFile = view.sourceFile;
    if (sourceFile == null || !view.isIndexReady) {
      buildWrapMetricsInMemory(view);
      return;
    }
    final int token = wrapMetricsToken.incrementAndGet();
    final int widthPx = Math.max(1, Math.round(getWrapWidth(view)));
    final Paint wrapPaint = new Paint(view.paint);
    wrapMetricsBuilding = true;
    view.ioHandler.post(() -> buildWrapMetricsFromFile(view, token, widthPx, wrapPaint));
  }

  void buildWrapMetricsInMemory(SodiumEditorView view) {
    int total = view.getLinesCount();
    if (total <= 0) total = getWindowLineCount(view);
    if (total <= 0) {
      wrapLineCounts = null;
      wrapLinePrefix = null;
      totalWrapVisualLines = 0;
      wrapMetricsReady = true;
      return;
    }
    int widthPx = Math.max(1, Math.round(getWrapWidth(view)));
    int[] counts = new int[total];
    for (int i = 0; i < total; i++) counts[i] = 1;

    int start;
    java.util.ArrayList<String> snapshot = new java.util.ArrayList<>();
    start = getWindowStartLineWithSnapshot(view, snapshot);
    for (int i = 0; i < snapshot.size(); i++) {
      int global = start + i;
      if (global < 0 || global >= total) continue;
      String line = snapshot.get(i);
      counts[global] = this.computeWrapCountForLine(view, line, widthPx, view.paint, true);
    }

    int[] prefix = new int[total + 1];
    int running = 0;
    for (int i = 0; i < total; i++) {
      running += counts[i];
      prefix[i + 1] = running;
    }

    wrapLineCounts = counts;
    wrapLinePrefix = prefix;
    totalWrapVisualLines = running;
    wrapMetricsWidth = widthPx;
    wrapPrefixValidUpToLine = (view.windowStartLine == 0) ? (total - 1) : -1;
    wrapMetricsReady = true;
  }

  void buildWrapMetricsFromFile(SodiumEditorView view, int token, int widthPx, Paint wrapPaint) {
    java.io.BufferedReader br = null;
    try {
      int total = view.getLinesCount();
      if (total <= 0) total = getWindowLineCount(view);
      if (total <= 0) {
        view.post(
            () -> {
              if (token != wrapMetricsToken.get()) return;
              wrapLineCounts = null;
              wrapLinePrefix = null;
              totalWrapVisualLines = 0;
              wrapMetricsReady = true;
              wrapMetricsBuilding = false;
            });
        return;
      }

      int[] counts = new int[total];
      for (int i = 0; i < total; i++) counts[i] = 1;

      br = view.reopenReaderAtStart();
      int lineIndex = 0;
      while (true) {
        if (token != wrapMetricsToken.get()) return;
        String fileLine = (br != null) ? br.readLine() : null;
        String line = fileLine == null ? "" : fileLine;
        String mod = getModifiedLine(view, lineIndex);
        if (mod != null) line = mod;
        if (lineIndex < total) {
          counts[lineIndex] = this.computeWrapCountForLine(view, line, widthPx, wrapPaint, false);
        }
        lineIndex++;
        if (fileLine == null && mod == null) {
          break;
        }
      }

      int[] prefix = new int[total + 1];
      int running = 0;
      for (int i = 0; i < total; i++) {
        running += counts[i];
        prefix[i + 1] = running;
      }
      final int runningFinal = running;

      final int totalFinal = total;
      view.post(
          () -> {
            if (token != wrapMetricsToken.get()) return;
            wrapLineCounts = counts;
            wrapLinePrefix = prefix;
            totalWrapVisualLines = runningFinal;
            wrapMetricsWidth = widthPx;
            wrapMetricsReady = true;
            wrapPrefixValidUpToLine = totalFinal - 1;
            wrapMetricsBuilding = false;
            view.postInvalidateOnAnimation();
          });
    } catch (Exception ignored) {
      view.post(
          () -> {
            if (token != wrapMetricsToken.get()) return;
            wrapLineCounts = null;
            wrapLinePrefix = null;
            totalWrapVisualLines = 0;
            wrapMetricsReady = true;
            wrapMetricsBuilding = false;
          });
    } finally {
      try {
        if (br != null) br.close();
      } catch (Exception ignored) {
      }
    }
  }

  void onLineContentChanged(SodiumEditorView view, int globalLine, @Nullable String newText) {
    if (!isWordWrapEnabled) return;
    wrapCache.remove(globalLine);

    int widthPx = Math.max(1, Math.round(getWrapWidth(view)));
    if (!wrapMetricsReady || wrapLineCounts == null || wrapLinePrefix == null || wrapMetricsWidth != widthPx) {
      invalidateWrapMetrics(view);
      return;
    }
    if (globalLine < 0 || globalLine >= wrapLineCounts.length) {
      invalidateWrapMetrics(view);
      return;
    }

    int newCount = this.computeWrapCountForLine(view, newText, widthPx, view.paint, true);
    int oldCount = wrapLineCounts[globalLine];
    if (newCount == oldCount) return;

    int delta = newCount - oldCount;
    wrapLineCounts[globalLine] = newCount;
    for (int i = globalLine + 1; i < wrapLinePrefix.length; i++) {
      wrapLinePrefix[i] += delta;
    }
    totalWrapVisualLines += delta;
  }

  public void onLineCountChanged(SodiumEditorView view) {
    if (isWordWrapEnabled) invalidateWrapMetrics(view);
    view.lineNumberManager.invalidateCache();
  }

  boolean isWrapMetricsUsableForWindow(SodiumEditorView view, int widthPx) {
    if (!isWordWrapEnabled) return false;
    if (!wrapMetricsReady || wrapLinePrefix == null || wrapLineCounts == null) return false;
    if (wrapMetricsWidth != widthPx) return false;
    int total = view.getLinesCount();
    if (total <= 0) total = getWindowLineCount(view);
    if (total <= 0) return false;
    if (wrapLineCounts.length != total || wrapLinePrefix.length != total + 1) return false;
    int windowEnd = view.getWindowEndLine();
    return wrapPrefixValidUpToLine >= windowEnd;
  }

  boolean isWrapMetricsUsableForLine(SodiumEditorView view, int line) {
    int widthPx = Math.max(1, Math.round(getWrapWidth(view)));
    if (!isWrapMetricsUsableForWindow(view, widthPx)) return false;
    return wrapPrefixValidUpToLine >= line;
  }

  public int getTotalVisualLineCount(SodiumEditorView view) {
    if (!isWordWrapEnabled) return view.getVisibleLineCount();
    int widthPx = Math.max(1, Math.round(getWrapWidth(view)));
    if (!isWrapMetricsUsableForWindow(view, widthPx)) {
      int total = view.getLinesCount();
      if (total <= 0) total = getWindowLineCount(view);
      return Math.max(1, total);
    }
    return Math.max(1, totalWrapVisualLines);
  }

  int getWrapRangeCount(SodiumEditorView view, int startLine, int endLine) {
    if (wrapLinePrefix == null) return 0;
    int total = wrapLinePrefix.length - 1;
    int s = Math.max(0, Math.min(startLine, total - 1));
    int e = Math.max(s, Math.min(endLine, total - 1));
    return wrapLinePrefix[e + 1] - wrapLinePrefix[s];
  }

  private static int getWindowTargetLine(SodiumEditorView view) {
    synchronized (view.linesWindow) {
      return view.windowStartLine + view.linesWindow.size() - 1;
    }
  }

  private static int getWindowStartLineWithSnapshot(
      SodiumEditorView view, java.util.ArrayList<String> out) {
    synchronized (view.linesWindow) {
      out.clear();
      out.addAll(view.linesWindow);
      return view.windowStartLine;
    }
  }

  private static int getWindowLineCount(SodiumEditorView view) {
    synchronized (view.linesWindow) {
      return view.windowStartLine + view.linesWindow.size();
    }
  }

  private static int getWindowSize(SodiumEditorView view) {
    synchronized (view.linesWindow) {
      return view.linesWindow.size();
    }
  }

  private static float getScrollY(SodiumEditorView view) {
    return view.scrollManager.scrollY;
  }

  private static void abortScrollAnimation(SodiumEditorView view) {
    if (!view.scrollManager.scroller.isFinished()) view.scrollManager.scroller.abortAnimation();
  }

  private static void addScrollY(SodiumEditorView view, float delta) {
    view.scrollManager.scrollY += delta;
  }

  private static String getModifiedLine(SodiumEditorView view, int lineIndex) {
    synchronized (view.modifiedLines) {
      return view.modifiedLines.get(lineIndex);
    }
  }

  private static boolean isModifiedLine(SodiumEditorView view, int globalLine) {
    synchronized (view.modifiedLines) {
      return view.modifiedLines.containsKey(globalLine);
    }
  }

  int findLineForVisualIndex(SodiumEditorView view, int visualIndex) {
    if (wrapLinePrefix == null || wrapLinePrefix.length == 0) return 0;
    int lo = 0;
    int hi = wrapLinePrefix.length - 1;
    while (lo < hi) {
      int mid = (lo + hi) >>> 1;
      if (wrapLinePrefix[mid] <= visualIndex) {
        lo = mid + 1;
      } else {
        hi = mid;
      }
    }
    int line = Math.max(0, lo - 1);
    return Math.min(line, wrapLinePrefix.length - 2);
  }

  public SodiumEditorView.VisualLinePosition getVisualPositionForIndex(
      SodiumEditorView view, int visualIndex) {
    int widthPx = Math.max(1, Math.round(getWrapWidth(view)));
    if (!isWrapMetricsUsableForWindow(view, widthPx)) {
      if (isWordWrapEnabled) {
        return getVisualPositionForIndexFallback(view, visualIndex, widthPx);
      }
      int line = view.mapVisibleIndexToGlobal(visualIndex);
      return new SodiumEditorView.VisualLinePosition(line, 0);
    }
    int maxVisual = Math.max(0, totalWrapVisualLines - 1);
    int v = Math.max(0, Math.min(visualIndex, maxVisual));
    int line = findLineForVisualIndex(view, v);
    int seg = v - wrapLinePrefix[line];
    return new SodiumEditorView.VisualLinePosition(line, seg);
  }

  SodiumEditorView.VisualLinePosition getVisualPositionForIndexFallback(
      SodiumEditorView view, int visualIndex, int widthPx) {
    int idx = Math.max(0, visualIndex);
    int baseLine = Math.max(0, view.windowStartLine);
    int baseVisual = baseLine;
    if (wrapLinePrefix != null && wrapPrefixValidUpToLine >= baseLine && baseLine < wrapLinePrefix.length) {
      baseVisual = wrapLinePrefix[baseLine];
    }
    int remaining = idx - baseVisual;
    if (remaining <= 0) {
      return new SodiumEditorView.VisualLinePosition(baseLine, 0);
    }

    int line = baseLine;
    int windowEnd = view.getWindowEndLine();
    if (windowEnd < baseLine) windowEnd = baseLine;

    while (line <= windowEnd) {
      String text = view.getLineTextForRender(line);
      int[] starts = getWrapStartsForLine(view, line, text);
      int segCount = Math.max(1, starts.length);
      if (remaining < segCount) {
        return new SodiumEditorView.VisualLinePosition(
            line, Math.max(0, Math.min(remaining, segCount - 1)));
      }
      remaining -= segCount;
      line++;
    }

    return new SodiumEditorView.VisualLinePosition(windowEnd, 0);
  }

  public int[] getWrapStartsForLine(SodiumEditorView view, int globalLine, String line) {
    if (!isWordWrapEnabled) return new int[] {0};
    int widthPx = Math.max(1, Math.round(getWrapWidth(view)));
    if (wrapWidthPx != widthPx) {
      wrapWidthPx = widthPx;
      wrapCache.clear();
    }
    boolean cacheable = isWrapCacheableForLine(view, globalLine);
    if (!cacheable) {
      wrapCache.remove(globalLine);
      return this.computeWrapStarts(view, line, widthPx, view.paint, true);
    }
    int[] cached = wrapCache.get(globalLine);
    if (cached != null) return cached;
    int[] starts = this.computeWrapStarts(view, line, widthPx, view.paint, true);
    wrapCache.put(globalLine, starts);
    return starts;
  }

  private boolean isWrapCacheableForLine(SodiumEditorView view, int globalLine) {
    int windowStart = view.windowStartLine;
    int windowSize = getWindowSize(view);
    if (globalLine >= windowStart && globalLine < windowStart + windowSize) {
      return true;
    }
    return isModifiedLine(view, globalLine);
  }

  int computeWrapCountForLine(SodiumEditorView view, String line, int widthPx) {
    int[] starts = computeWrapStarts(view, line, widthPx, view.paint, true);
    return Math.max(1, starts.length);
  }

  int computeWrapCountForLine(SodiumEditorView view, String line, int widthPx, Paint p, boolean useSharedBuffer) {
    int[] starts = computeWrapStarts(view, line, widthPx, p, useSharedBuffer);
    return Math.max(1, starts.length);
  }

  int[] computeWrapStarts(SodiumEditorView view, String line, int widthPx, Paint p, boolean useSharedBuffer) {
    if (line == null) return new int[] {0};
    int len = line.length();
    if (len == 0) return new int[] {0};
    if (this.shouldUseBreakTextWrap(view, line)) {
      return this.computeWrapStartsWithBreakText(line, widthPx, p);
    }
    float[] widths;
    if (useSharedBuffer) {
      widths = view.whitespaceGuideManager.ensureMeasureWidthBuffer(len);
    } else {
      widths = new float[len];
    }
    p.getTextWidths(line, 0, len, widths);
    float[] adv = new float[len];
    for (int i = 0; i < len; i++) {
      adv[i] = view.whitespaceGuideManager.getCharAdvanceWidth(line.charAt(i), widths[i], p, DEFAULT_TAB_SIZE_SPACES);
    }
    java.util.ArrayList<Integer> starts = new java.util.ArrayList<>();
    int i = 0;
    starts.add(0);
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

  private boolean shouldUseBreakTextWrap(SodiumEditorView view, String line) {
    if (view.getVisualSpaceScale() != 1) return false;
    return line.indexOf('\t') < 0;
  }

  private int[] computeWrapStartsWithBreakText(String line, int widthPx, Paint p) {
    int len = line.length();
    java.util.ArrayList<Integer> starts = new java.util.ArrayList<>();
    int i = 0;
    starts.add(0);
    while (i < len) {
      int count = p.breakText(line, i, len, true, widthPx, null);
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

  int getWrapSegmentEnd(int[] starts, int segIndex, int lineLength) {
    if (starts == null || starts.length == 0) return lineLength;
    int next = segIndex + 1;
    if (next >= 0 && next < starts.length) return starts[next];
    return lineLength;
  }

  boolean patchWrapMetricsForVisualRange(
      SodiumEditorView view,
      int firstVisualIndex,
      int lastVisualIndex,
      @Nullable java.util.Map<Integer, String> directLines,
      int widthPx) {
    if (!isWordWrapEnabled) return false;
    if (!wrapMetricsReady || wrapLineCounts == null || wrapLinePrefix == null) return false;
    if (wrapMetricsWidth != widthPx) return false;
    if (wrapLineCounts.length + 1 != wrapLinePrefix.length) return false;

    final int anchorFirstVisual = firstVisualIndex;
    final SodiumEditorView.VisualLinePosition anchorPos = getVisualPositionForIndex(view, anchorFirstVisual);
    final int anchorLine = anchorPos.line;
    final int anchorSeg = anchorPos.segment;

    boolean changed = false;

    int v = Math.max(0, firstVisualIndex);
    int vEnd = Math.max(v, lastVisualIndex);
    for (; v <= vEnd; v++) {
      SodiumEditorView.VisualLinePosition pos = getVisualPositionForIndex(view, v);
      int line = pos.line;
      if (line < 0 || line >= wrapLineCounts.length) break;
      String text = view.getLineTextForRenderWithDirect(line, directLines);
      int[] starts = getWrapStartsForLine(view, line, text);
      int newCount = Math.max(1, starts.length);
      int oldCount = wrapLineCounts[line];
      if (newCount == oldCount) continue;

      int delta = newCount - oldCount;
      wrapLineCounts[line] = newCount;
      for (int i = line + 1; i < wrapLinePrefix.length; i++) {
        wrapLinePrefix[i] += delta;
      }
      totalWrapVisualLines += delta;
      changed = true;
    }

    if (!changed) return false;

    if (anchorLine >= 0 && anchorLine < wrapLinePrefix.length) {
      int newAnchorFirstVisual = wrapLinePrefix[anchorLine] + Math.max(0, anchorSeg);
      int dv = newAnchorFirstVisual - anchorFirstVisual;
      if (dv != 0) {
        view.scrollManager.scrollY += dv * view.lineHeight;
        view.clampScrollY();
      }
    }

    return true;
  }

  public void setWordWrapIndicatorTextSize(SodiumEditorView view, float sizeSp) {
    if (sizeSp <= 0f) return;
    float px = view.spToPx(sizeSp);
    float base = view.paint.getTextSize();
    updateIndicatorTextSize(base, px);
    view.invalidate();
  }
}
