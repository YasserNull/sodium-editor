package com.yn.sodiumeditor.view;

import android.graphics.Paint;
import java.util.HashMap;
import java.util.concurrent.atomic.AtomicInteger;
import androidx.annotation.Nullable;

final class WordWrapManager {
  int wrapWidthPx = -1;
  final HashMap<Integer, int[]> wrapCache = new HashMap<>();
  volatile int[] wrapLineCounts = null;
  volatile int[] wrapLinePrefix = null;
  volatile int wrapPrefixValidUpToLine = -1;
  volatile int totalWrapVisualLines = 0;
  volatile boolean wrapMetricsReady = false;
  volatile int wrapMetricsWidth = -1;
  final AtomicInteger wrapMetricsToken = new AtomicInteger(0);
  volatile boolean wrapMetricsBuilding = false;
  final AtomicInteger wrapSnapshotToken = new AtomicInteger(0);
  volatile boolean wrapSnapshotBuilding = false;
  volatile int wrapSnapshotWidth = -1;
  volatile int wrapSnapshotStart = -1;
  volatile int wrapSnapshotSize = -1;
  final AtomicInteger wrapPrefixToken = new AtomicInteger(0);
  volatile boolean wrapPrefixBuilding = false;
  volatile int wrapPrefixWidth = -1;
  volatile int wrapPrefixTargetLine = -1;
  boolean wrapPrefixRebuildPending = false;

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

  void scheduleWrapPrefixRebuildUpToWindow(SodiumEditorView view) {
    if (!view.isWordWrapEnabled) return;
    if (shouldSuppressWrapMetricsForFastSelectAll(view)) return;
    int total = view.getLinesCount();
    if (total <= 0) return;

    int targetLine = getWindowTargetLine(view);
    if (targetLine < 0) return;
    targetLine = Math.min(targetLine, total - 1);
    final int targetLineFinal = targetLine;

    int widthPx = Math.max(1, Math.round(view.getWrapWidth()));
    if (wrapPrefixBuilding && wrapPrefixWidth == widthPx && wrapPrefixTargetLine >= targetLineFinal) return;

    wrapPrefixBuilding = true;
    wrapPrefixWidth = widthPx;
    wrapPrefixTargetLine = targetLineFinal;

    abortScrollAnimation(view);

    final int token = wrapPrefixToken.incrementAndGet();
    final int[] baseCounts =
        (wrapLineCounts != null && wrapLineCounts.length == total) ? wrapLineCounts.clone() : null;

    int anchorVisualIndex = Math.max(0, (int) (getScrollY(view) / view.lineHeight));
    SodiumEditorView.VisualLinePosition anchorPos = view.getVisualPositionForIndex(anchorVisualIndex);
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
                counts[i] = view.computeWrapCountForLine(line, widthPx, wrapPaint, false);
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
                counts[lineIndex] = view.computeWrapCountForLine(line, widthPx, wrapPaint, false);
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
                if (Math.max(1, Math.round(view.getWrapWidth())) != widthPx) {
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

  void initIndicatorPaint(Paint basePaint, float density, String indicatorText) {
    wordWrapIndicatorPadPx = 4f * density;
    wordWrapIndicatorPaint.setColor(0xFF9E9E9E);
    wordWrapIndicatorPaint.setAlpha(180);
    wordWrapIndicatorPaint.setTextAlign(Paint.Align.LEFT);
    wordWrapIndicatorPaint.setTextSize(basePaint.getTextSize() * wordWrapIndicatorTextScale);
    wordWrapIndicatorPaint.setTypeface(basePaint.getTypeface());
    wordWrapIndicatorWidth = wordWrapIndicatorPaint.measureText(indicatorText);
  }

  void setIndicatorColor(int color) {
    wordWrapIndicatorPaint.setColor(color);
  }

  void updateIndicatorTextSize(float baseTextSizePx, float targetPx, String indicatorText) {
    if (baseTextSizePx > 0f && targetPx > 0f) {
      wordWrapIndicatorTextScale = targetPx / baseTextSizePx;
    } else {
      wordWrapIndicatorTextScale = 0.85f;
    }
    wordWrapIndicatorPaint.setTextSize(baseTextSizePx * wordWrapIndicatorTextScale);
    wordWrapIndicatorWidth = wordWrapIndicatorPaint.measureText(indicatorText);
  }

  void updateIndicatorPaintForTextSize(float textSizePx, Paint basePaint, String indicatorText) {
    wordWrapIndicatorPaint.setTextSize(textSizePx * wordWrapIndicatorTextScale);
    wordWrapIndicatorPaint.setTypeface(basePaint.getTypeface());
    wordWrapIndicatorWidth = wordWrapIndicatorPaint.measureText(indicatorText);
  }

  void updateIndicatorTypeface(Paint basePaint, String indicatorText) {
    wordWrapIndicatorPaint.setTypeface(basePaint.getTypeface());
    wordWrapIndicatorWidth = wordWrapIndicatorPaint.measureText(indicatorText);
  }

  void invalidateWrapMetrics(SodiumEditorView view) {
    invalidateWrapMetrics(view, true, true);
  }

  void invalidateWrapMetrics(SodiumEditorView view, boolean clearExisting) {
    invalidateWrapMetrics(view, clearExisting, true);
  }

  void invalidateWrapMetrics(SodiumEditorView view, boolean clearExisting, boolean scheduleFullRebuild) {
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

    if (view.isWordWrapEnabled) {
      if (scheduleFullRebuild) {
        scheduleWrapMetricsBuild(view);
      } else {
        int widthPx = Math.max(1, Math.round(view.getWrapWidth()));
        view.scheduleWrapMetricsSnapshotIfNeeded(widthPx);
        view.scheduleWrapPrefixRebuildUpToWindow();
      }
    }
  }

  void requestWrapPrefixRebuild(SodiumEditorView view) {
    if (!view.isWordWrapEnabled) return;
    if (view.zoomManager.isScaling() || view.zoomManager.isScaleInProgress()) {
      wrapPrefixRebuildPending = true;
      return;
    }
    view.scheduleWrapPrefixRebuildUpToWindow();
  }

  void cancelWrapPrefixRebuildForInteraction() {
    if (!wrapPrefixBuilding) return;
    wrapPrefixToken.incrementAndGet();
    wrapPrefixBuilding = false;
    wrapPrefixRebuildPending = true;
  }

  void cancelWrapWorkForPriority(SodiumEditorView view) {
    if (!view.isWordWrapEnabled) return;
    wrapMetricsToken.incrementAndGet();
    wrapSnapshotToken.incrementAndGet();
    wrapPrefixToken.incrementAndGet();
    wrapMetricsBuilding = false;
    wrapSnapshotBuilding = false;
    wrapPrefixBuilding = false;
  }

  boolean shouldSuppressWrapMetricsForFastSelectAll(SodiumEditorView view) {
    if (!view.isWordWrapEnabled
        || (!view.selectionManager.isSelectAllActive() && !view.selectionManager.isEntireFileSelected())) {
      return false;
    }
    int widthPx = Math.max(1, Math.round(view.getWrapWidth()));
    return !view.isWrapMetricsUsableForWindow(widthPx);
  }

  void scheduleWrapMetricsSnapshotIfNeeded(SodiumEditorView view, int widthPx) {
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

          for (int i = 0; i < snapshot.size(); i++) {
            int global = start + i;
            if (global < 0 || global >= total) continue;
            String line = snapshot.get(i);
            counts[global] = view.computeWrapCountForLine(line, widthPx, wrapPaint, false);
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

  void buildWrapMetricsForWindowSnapshot(SodiumEditorView view) {
    int total = view.getLinesCount();
    if (total <= 0) total = getWindowLineCount(view);
    if (total <= 0) {
      wrapLineCounts = null;
      wrapLinePrefix = null;
      totalWrapVisualLines = 0;
      wrapMetricsReady = true;
      return;
    }

    int widthPx = Math.max(1, Math.round(view.getWrapWidth()));
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
        counts[global] = view.computeWrapCountForLine(line, widthPx, view.paint, true);
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
    if (!view.isWordWrapEnabled) return;
    if (shouldSuppressWrapMetricsForFastSelectAll(view)) return;
    if (view.getWidth() <= 0) return;
    java.io.File sourceFile = view.sourceFile;
    if (sourceFile == null || !view.isIndexReady) {
      view.buildWrapMetricsInMemory();
      return;
    }
    final int token = wrapMetricsToken.incrementAndGet();
    final int widthPx = Math.max(1, Math.round(view.getWrapWidth()));
    final Paint wrapPaint = new Paint(view.paint);
    wrapMetricsBuilding = true;
    view.ioHandler.post(() -> view.buildWrapMetricsFromFile(token, widthPx, wrapPaint));
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
    int widthPx = Math.max(1, Math.round(view.getWrapWidth()));
    int[] counts = new int[total];
    for (int i = 0; i < total; i++) counts[i] = 1;

    int start;
    java.util.ArrayList<String> snapshot = new java.util.ArrayList<>();
    start = getWindowStartLineWithSnapshot(view, snapshot);
    for (int i = 0; i < snapshot.size(); i++) {
      int global = start + i;
      if (global < 0 || global >= total) continue;
      String line = snapshot.get(i);
      counts[global] = view.computeWrapCountForLine(line, widthPx, view.paint, true);
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
          counts[lineIndex] = view.computeWrapCountForLine(line, widthPx, wrapPaint, false);
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
    if (!view.isWordWrapEnabled) return;
    wrapCache.remove(globalLine);

    int widthPx = Math.max(1, Math.round(view.getWrapWidth()));
    if (!wrapMetricsReady || wrapLineCounts == null || wrapLinePrefix == null || wrapMetricsWidth != widthPx) {
      invalidateWrapMetrics(view);
      return;
    }
    if (globalLine < 0 || globalLine >= wrapLineCounts.length) {
      invalidateWrapMetrics(view);
      return;
    }

    int newCount = view.computeWrapCountForLine(newText, widthPx, view.paint, true);
    int oldCount = wrapLineCounts[globalLine];
    if (newCount == oldCount) return;

    int delta = newCount - oldCount;
    wrapLineCounts[globalLine] = newCount;
    for (int i = globalLine + 1; i < wrapLinePrefix.length; i++) {
      wrapLinePrefix[i] += delta;
    }
    totalWrapVisualLines += delta;
  }

  void onLineCountChanged(SodiumEditorView view) {
    if (view.isWordWrapEnabled) invalidateWrapMetrics(view);
    view.lineNumberManager.invalidateCache();
  }

  boolean isWrapMetricsUsableForWindow(SodiumEditorView view, int widthPx) {
    if (!view.isWordWrapEnabled) return false;
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
    int widthPx = Math.max(1, Math.round(view.getWrapWidth()));
    if (!isWrapMetricsUsableForWindow(view, widthPx)) return false;
    return wrapPrefixValidUpToLine >= line;
  }

  int getTotalVisualLineCount(SodiumEditorView view) {
    if (!view.isWordWrapEnabled) return view.getVisibleLineCount();
    int widthPx = Math.max(1, Math.round(view.getWrapWidth()));
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

  SodiumEditorView.VisualLinePosition getVisualPositionForIndex(
      SodiumEditorView view, int visualIndex) {
    int widthPx = Math.max(1, Math.round(view.getWrapWidth()));
    if (!isWrapMetricsUsableForWindow(view, widthPx)) {
      if (view.isWordWrapEnabled) {
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
      int[] starts = view.getWrapStartsForLine(line, text);
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

  int[] getWrapStartsForLine(SodiumEditorView view, int globalLine, String line) {
    if (!view.isWordWrapEnabled) return new int[] {0};
    int widthPx = Math.max(1, Math.round(view.getWrapWidth()));
    if (wrapWidthPx != widthPx) {
      wrapWidthPx = widthPx;
      wrapCache.clear();
    }
    boolean cacheable = isWrapCacheableForLine(view, globalLine);
    if (!cacheable) {
      wrapCache.remove(globalLine);
      return view.computeWrapStarts(line, widthPx, view.paint, true);
    }
    int[] cached = wrapCache.get(globalLine);
    if (cached != null) return cached;
    int[] starts = view.computeWrapStarts(line, widthPx, view.paint, true);
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
}
