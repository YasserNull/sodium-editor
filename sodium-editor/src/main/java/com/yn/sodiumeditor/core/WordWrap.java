package com.yn.sodiumeditor.core; 
import com.yn.sodiumeditor.SodiumEditor;
import android.graphics.Paint;
import android.os.Handler;
import android.os.Looper;
import android.util.SparseIntArray;
import androidx.annotation.Nullable;
import java.io.BufferedReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import com.yn.sodiumeditor.io.EditOperators;
import com.yn.sodiumeditor.core.WordWrapIndicator;
import com.yn.sodiumeditor.renderer.TextRender;
/**
 * Manages word wrap functionality for the SodiumEditor.
 * Handles line wrapping, visual line positioning, and wrap metrics.
 */
public class WordWrap {

  private final SodiumEditor editor;
  private final Handler mainHandler;

  // Word wrap state
  public boolean isWordWrapEnabled = false;
  public int wrapWidthPx = -1;
  public final HashMap<Integer, int[]> wrapCache = new HashMap<>();
  public volatile int[] wrapLineCounts = null;
  public volatile int[] wrapLinePrefix = null;
  public volatile int wrapPrefixValidUpToLine = -1;
  public volatile int totalWrapVisualLines = 0;
  public volatile boolean wrapMetricsReady = false;
  public volatile int wrapMetricsWidth = -1;
  public final AtomicInteger wrapMetricsToken = new AtomicInteger(0);
  public volatile boolean wrapMetricsBuilding = false;
  public final AtomicInteger wrapSnapshotToken = new AtomicInteger(0);
  public volatile boolean wrapSnapshotBuilding = false;
  public volatile int wrapSnapshotWidth = -1;
  public volatile int wrapSnapshotStart = -1;
  public volatile int wrapSnapshotSize = -1;
  public final AtomicInteger wrapPrefixToken = new AtomicInteger(0);
  public volatile boolean wrapPrefixBuilding = false;
  public volatile int wrapPrefixWidth = -1;
  public volatile int wrapPrefixTargetLine = -1;
  public boolean wrapPrefixRebuildPending = false;

  // Word wrap indicator manager
  public final WordWrapIndicator indicator;

  // Visual line position class
  public static class VisualLinePosition {
    public final int line;
    public final int segment;

    public VisualLinePosition(int line, int segment) {
      this.line = line;
      this.segment = segment;
    }
  }

  public WordWrap(SodiumEditor editor) {
    this.editor = editor;
    this.mainHandler = new Handler(Looper.getMainLooper());
    this.indicator = new WordWrapIndicator(editor);
  }

  /**
   * Enables or disables word wrap.
   */
  public void setWordWrapEnabled(boolean enabled) {
    if (this.isWordWrapEnabled == enabled) return;
    this.isWordWrapEnabled = enabled;
    invalidateWrapMetrics();
    if (enabled) {
      editor.scroll.scrollX = 0f;
      editor.scroll.clampScrollX();
      editor.clearStreamedLineCaches();
      editor.textRender.reloadWindowAroundVisible(false);
    }
    editor.requestLayout();
    editor.invalidate();
  }

  /**
   * Enables or disables the word wrap indicator.
   */
  public void setWordWrapIndicatorEnabled(boolean enabled) {
    indicator.setWordWrapIndicatorEnabled(enabled);
  }

  /**
   * Sets the color of the word wrap indicator.
   */
  public void setWordWrapIndicatorColor(int color) {
    indicator.setWordWrapIndicatorColor(color);
  }

  /**
   * Sets the text size of the word wrap indicator.
   */
  public void setWordWrapIndicatorTextSize(float sizeSp) {
    indicator.setWordWrapIndicatorTextSize(sizeSp);
  }

  /**
   * Gets the wrap width in pixels.
   */
  public float getWrapWidth() {
    return Math.max(1f, editor.getWidth() - editor.getTextStartX());
  }

  /**
   * Invalidates wrap metrics and triggers a rebuild.
   */
  public void invalidateWrapMetrics() {
    invalidateWrapMetrics(true, true);
  }

  /**
   * Invalidates wrap metrics with options.
   */
  public void invalidateWrapMetrics(boolean clearExisting) {
    invalidateWrapMetrics(clearExisting, true);
  }

  /**
   * Invalidates wrap metrics with full control.
   */
  public void invalidateWrapMetrics(boolean clearExisting, boolean scheduleFullRebuild) {
    wrapCache.clear();
    wrapWidthPx = -1;
    wrapMetricsWidth = -1;
    wrapMetricsToken.incrementAndGet();
    wrapPrefixValidUpToLine = -1;

    if (clearExisting) {
      wrapLineCounts = null;
      wrapLinePrefix = null;
    }

    int currentLines = editor.getLinesCount();
    if (currentLines <= 0) currentLines = editor.textRender.windowStartLine + editor.textRender.linesWindow.size();

    boolean sizeMismatch = (wrapLineCounts != null && wrapLineCounts.length != currentLines);
    boolean missing = (wrapLineCounts == null || wrapLinePrefix == null);

    if (clearExisting || sizeMismatch || missing) {
      buildWrapMetricsForWindowSnapshot();
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
        scheduleWrapMetricsBuild();
      } else {
        int widthPx = Math.max(1, Math.round(getWrapWidth()));
        scheduleWrapMetricsSnapshotIfNeeded(widthPx);
        scheduleWrapPrefixRebuildUpToWindow();
      }
    }
  }

  /**
   * Requests a wrap prefix rebuild.
   */
  public void requestWrapPrefixRebuild() {
    if (!isWordWrapEnabled) return;
    if (editor.zoom.isScaling || (editor.scaleGestureDetector != null && editor.scaleGestureDetector.isInProgress())) {
      wrapPrefixRebuildPending = true;
      return;
    }
    scheduleWrapPrefixRebuildUpToWindow();
  }

  /**
   * Cancels wrap prefix rebuild for interaction.
   */
  public void cancelWrapPrefixRebuildForInteraction() {
    if (!wrapPrefixBuilding) return;
    wrapPrefixToken.incrementAndGet();
    wrapPrefixBuilding = false;
    wrapPrefixRebuildPending = true;
  }

  /**
   * Cancels wrap work for priority operations.
   */
  public void cancelWrapWorkForPriority() {
    if (!isWordWrapEnabled) return;
    wrapMetricsToken.incrementAndGet();
    wrapSnapshotToken.incrementAndGet();
    wrapPrefixToken.incrementAndGet();
    wrapMetricsBuilding = false;
    wrapSnapshotBuilding = false;
    wrapPrefixBuilding = false;
  }

  /**
   * Checks if wrap metrics should be suppressed for fast select all.
   */
  public boolean shouldSuppressWrapMetricsForFastSelectAll() {
    if (!isWordWrapEnabled || (!editor.selection.isSelectAllActive && !editor.selection.isEntireFileSelected)) return false;
    int widthPx = Math.max(1, Math.round(getWrapWidth()));
    return !isWrapMetricsUsableForWindow(widthPx);
  }

  /**
   * Schedules a wrap prefix rebuild up to the current window.
   */
  public void scheduleWrapPrefixRebuildUpToWindow() {
    if (!isWordWrapEnabled) return;
    if (shouldSuppressWrapMetricsForFastSelectAll()) return;
    int total = editor.getLinesCount();
    if (total <= 0) return;

    int targetLine;
    synchronized (editor.textRender.linesWindow) {
      targetLine = editor.textRender.windowStartLine + editor.textRender.linesWindow.size() - 1;
    }
    if (targetLine < 0) return;
    targetLine = Math.min(targetLine, total - 1);
    final int targetLineFinal = targetLine;

    int widthPx = Math.max(1, Math.round(getWrapWidth()));
    if (wrapPrefixBuilding && wrapPrefixWidth == widthPx && wrapPrefixTargetLine >= targetLineFinal)
      return;

    wrapPrefixBuilding = true;
    wrapPrefixWidth = widthPx;
    wrapPrefixTargetLine = targetLineFinal;

    if (!editor.scroll.scroller.isFinished()) editor.scroll.scroller.abortAnimation();

    final int token = wrapPrefixToken.incrementAndGet();
    final int[] baseCounts =
        (wrapLineCounts != null && wrapLineCounts.length == total) ? wrapLineCounts.clone() : null;

    int anchorVisualIndex = Math.max(0, (int) (editor.scroll.scrollY / editor.textRender.lineHeight));
    VisualLinePosition anchorPos = getVisualPositionForIndex(anchorVisualIndex);
    final int anchorLine = anchorPos.line;
    final int oldAnchorPrefix =
        (wrapLinePrefix != null && anchorLine >= 0 && anchorLine < wrapLinePrefix.length)
            ? wrapLinePrefix[anchorLine]
            : anchorLine;

    final Paint wrapPaint = new Paint(editor.textRender.paint);

    editor.fileIO.ioHandler.post(
        () -> {
          if (token != wrapPrefixToken.get()) return;

          int[] counts;
          if (baseCounts != null) {
            counts = baseCounts;
          } else {
            counts = new int[total];
            for (int i = 0; i < total; i++) counts[i] = 1;
          }

          if (editor.fileIO.sourceFile == null || !editor.fileIO.sourceFile.exists()) {
            int start;
            ArrayList<String> snapshot = new ArrayList<>();
            synchronized (editor.textRender.linesWindow) {
              start = editor.textRender.windowStartLine;
              snapshot.addAll(editor.textRender.linesWindow);
            }
            if (start == 0) {
              int end = Math.min(targetLineFinal, snapshot.size() - 1);
              for (int i = 0; i <= end; i++) {
                String line = snapshot.get(i);
                counts[i] = computeWrapCountForLine(line, widthPx, wrapPaint, false);
              }
            } else {
              mainHandler.post(
                  () -> {
                    if (token != wrapPrefixToken.get()) return;
                    wrapPrefixBuilding = false;
                  });
              return;
            }
          } else {
            BufferedReader br = null;
            try {
              br = editor.fileIO.reopenReaderAtStart();
              int lineIndex = 0;
              while (lineIndex <= targetLineFinal) {
                if (token != wrapPrefixToken.get()) return;
                String fileLine = (br != null) ? br.readLine() : null;
                String line = fileLine == null ? "" : fileLine;
                String mod;
                synchronized (editor.textRender.modifiedLines) {
                  mod = editor.textRender.modifiedLines.get(lineIndex);
                }
                if (mod != null) line = mod;
                counts[lineIndex] = computeWrapCountForLine(line, widthPx, wrapPaint, false);
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
              mainHandler.post(
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
              (anchorLine >= 0 && anchorLine < prefix.length)
                  ? prefix[anchorLine]
                  : oldAnchorPrefix;
          final int deltaPrefix = newAnchorPrefix - oldAnchorPrefix;

          mainHandler.post(
              () -> {
                if (token != wrapPrefixToken.get()) return;
                if (Math.max(1, Math.round(getWrapWidth())) != widthPx) {
                  wrapPrefixBuilding = false;
                  return;
                }
                wrapPrefixBuilding = false;
                if (editor.zoom.isZoomGestureActive()) {
                  editor.zoom.pendingWrapPrefixCounts = counts;
                  editor.zoom.pendingWrapPrefixPrefix = prefix;
                  editor.zoom.pendingWrapPrefixTotalVisualLines = runningFinal;
                  editor.zoom.pendingWrapPrefixWidthPx = widthPx;
                  editor.zoom.pendingWrapPrefixValidUpToLine =
                      Math.max(wrapPrefixValidUpToLine, Math.min(targetLineFinal, total - 1));
                  editor.zoom.pendingApplyWrapPrefixUpdate = true;
                  return;
                }
                wrapLineCounts = counts;
                wrapLinePrefix = prefix;
                totalWrapVisualLines = runningFinal;
                wrapMetricsWidth = widthPx;
                wrapMetricsReady = true;
                wrapPrefixValidUpToLine =
                    Math.max(wrapPrefixValidUpToLine, Math.min(targetLineFinal, total - 1));
                if (deltaPrefix != 0) {
                  editor.scroll.scrollY += deltaPrefix * editor.textRender.lineHeight;
                  editor.scroll.clampScrollY();
                }
                editor.postInvalidateOnAnimation();
              });
        });
  }

  /**
   * Called when line content changes.
   */
  public void onLineContentChanged(int globalLine, @Nullable String newText) {
    if (!isWordWrapEnabled) return;
    wrapCache.remove(globalLine);

    int widthPx = Math.max(1, Math.round(getWrapWidth()));
    if (!wrapMetricsReady
        || wrapLineCounts == null
        || wrapLinePrefix == null
        || wrapMetricsWidth != widthPx) {
      invalidateWrapMetrics();
      return;
    }
    if (globalLine < 0 || globalLine >= wrapLineCounts.length) {
      invalidateWrapMetrics();
      return;
    }

    int newCount = computeWrapCountForLine(newText, widthPx);
    int oldCount = wrapLineCounts[globalLine];
    if (newCount == oldCount) return;

    int delta = newCount - oldCount;
    wrapLineCounts[globalLine] = newCount;
    for (int i = globalLine + 1; i < wrapLinePrefix.length; i++) {
      wrapLinePrefix[i] += delta;
    }
    totalWrapVisualLines += delta;
  }

  /**
   * Called when line count changes.
   */
  public void onLineCountChanged() {
    if (isWordWrapEnabled) invalidateWrapMetrics();
    editor.lineNumber.invalidateLineNumberCache();
  }

  /**
   * Builds wrap metrics for the current window snapshot.
   */
  public void buildWrapMetricsForWindowSnapshot() {
    int total = editor.getLinesCount();
    if (total <= 0) total = editor.textRender.windowStartLine + editor.textRender.linesWindow.size();
    if (total <= 0) {
      wrapLineCounts = null;
      wrapLinePrefix = null;
      totalWrapVisualLines = 0;
      wrapMetricsReady = true;
      return;
    }

    int widthPx = Math.max(1, Math.round(getWrapWidth()));
    int[] counts;

    if (wrapLineCounts != null && wrapLineCounts.length == total) {
      counts = wrapLineCounts.clone();
    } else {
      counts = new int[total];
      for (int i = 0; i < total; i++) counts[i] = 1;
    }

    int start;
    ArrayList<String> snapshot = new ArrayList<>();
    synchronized (editor.textRender.linesWindow) {
      start = editor.textRender.windowStartLine;
      snapshot.addAll(editor.textRender.linesWindow);
    }

    if (!snapshot.isEmpty()) {
      for (int i = 0; i < snapshot.size(); i++) {
        int global = start + i;
        if (global < 0 || global >= total) continue;
        String line = snapshot.get(i);
        counts[global] = computeWrapCountForLine(line, widthPx);
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

  /**
   * Schedules wrap metrics snapshot if needed.
   */
  public void scheduleWrapMetricsSnapshotIfNeeded(int widthPx) {
    if (shouldSuppressWrapMetricsForFastSelectAll()) return;
    int start;
    int size;
    ArrayList<String> snapshot = new ArrayList<>();
    synchronized (editor.textRender.linesWindow) {
      start = editor.textRender.windowStartLine;
      size = editor.textRender.linesWindow.size();
      if (size > 0) snapshot.addAll(editor.textRender.linesWindow);
    }
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
    final Paint wrapPaint = new Paint(editor.textRender.paint);

    editor.fileIO.ioHandler.post(
        () -> {
          int total = editor.getLinesCount();
          if (total <= 0) total = start + size;
          if (total <= 0) {
            mainHandler.post(
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
            counts[global] = computeWrapCountForLine(line, widthPx, wrapPaint, false);
          }

          int[] prefix = new int[total + 1];
          int running = 0;
          for (int i = 0; i < total; i++) {
            running += counts[i];
            prefix[i + 1] = running;
          }
          final int runningFinal = running;

          mainHandler.post(
              () -> {
                if (token != wrapSnapshotToken.get()) return;
                wrapLineCounts = counts;
                wrapLinePrefix = prefix;
                totalWrapVisualLines = runningFinal;
                wrapMetricsWidth = widthPx;
                wrapMetricsReady = true;
                wrapSnapshotBuilding = false;
                editor.postInvalidateOnAnimation();
              });
        });
  }

  /**
   * Schedules wrap metrics build.
   */
  public void scheduleWrapMetricsBuild() {
    if (!isWordWrapEnabled) return;
    if (shouldSuppressWrapMetricsForFastSelectAll()) return;
    if (editor.getWidth() <= 0) return;
    if (editor.fileIO.sourceFile == null || !editor.fileIO.isIndexReady) {
      buildWrapMetricsInMemory();
      return;
    }
    final int token = wrapMetricsToken.incrementAndGet();
    final int widthPx = Math.max(1, Math.round(getWrapWidth()));
    final Paint wrapPaint = new Paint(editor.textRender.paint);
    wrapMetricsBuilding = true;
    editor.fileIO.ioHandler.post(() -> buildWrapMetricsFromFile(token, widthPx, wrapPaint));
  }

  /**
   * Builds wrap metrics in memory.
   */
  public void buildWrapMetricsInMemory() {
    int total = editor.getLinesCount();
    if (total <= 0) total = editor.textRender.windowStartLine + editor.textRender.linesWindow.size();
    if (total <= 0) {
      wrapLineCounts = null;
      wrapLinePrefix = null;
      totalWrapVisualLines = 0;
      wrapMetricsReady = true;
      return;
    }
    int widthPx = Math.max(1, Math.round(getWrapWidth()));
    int[] counts = new int[total];
    int[] prefix = new int[total + 1];
    int running = 0;
    for (int i = 0; i < total; i++) {
      String line = editor.getLineTextForRender(i);
      int c = computeWrapCountForLine(line, widthPx);
      counts[i] = c;
      running += c;
      prefix[i + 1] = running;
    }
    wrapLineCounts = counts;
    wrapLinePrefix = prefix;
    totalWrapVisualLines = running;
    wrapMetricsWidth = widthPx;
    wrapMetricsReady = true;
    wrapPrefixValidUpToLine = (editor.textRender.windowStartLine == 0) ? (total - 1) : -1;
    mainHandler.post(() -> editor.postInvalidateOnAnimation());
  }

  /**
   * Builds wrap metrics from file.
   */
  public void buildWrapMetricsFromFile(int token, int widthPx, Paint wrapPaint) {
    int total = editor.getLinesCount();
    if (total <= 0) total = editor.textRender.windowStartLine + editor.textRender.linesWindow.size();
    if (total <= 0) {
      wrapLineCounts = null;
      wrapLinePrefix = null;
      totalWrapVisualLines = 0;
      wrapMetricsReady = true;
      wrapMetricsBuilding = false;
      mainHandler.post(() -> editor.postInvalidateOnAnimation());
      return;
    }
    int[] counts = new int[total];
    int[] prefix = new int[total + 1];
    int running = 0;
    BufferedReader br = null;
    try {
      br = editor.fileIO.reopenReaderAtStart();
      int lineIndex = 0;
      while (lineIndex < total) {
        if (token != wrapMetricsToken.get()) {
          wrapMetricsBuilding = false;
          return;
        }
        String fileLine = (br != null) ? br.readLine() : null;
        String line = fileLine == null ? "" : fileLine;
        String mod;
        synchronized (editor.textRender.modifiedLines) {
          mod = editor.textRender.modifiedLines.get(lineIndex);
        }
        if (mod != null) line = mod;
        int c = computeWrapCountForLine(line, widthPx, wrapPaint, false);
        counts[lineIndex] = c;
        running += c;
        prefix[lineIndex + 1] = running;
        lineIndex++;
        if (fileLine == null && mod == null) {
          while (lineIndex < total) {
            counts[lineIndex] = 1;
            running += 1;
            prefix[lineIndex + 1] = running;
            lineIndex++;
          }
          break;
        }
      }
    } catch (Exception ignored) {
      wrapMetricsBuilding = false;
      return;
    } finally {
      try {
        if (br != null) br.close();
      } catch (Exception ignored) {
      }
    }
    if (token != wrapMetricsToken.get()) {
      wrapMetricsBuilding = false;
      return;
    }
    wrapLineCounts = counts;
    wrapLinePrefix = prefix;
    totalWrapVisualLines = running;
    wrapMetricsWidth = widthPx;
    wrapMetricsReady = true;
    wrapPrefixValidUpToLine = total - 1;
    wrapMetricsBuilding = false;
    mainHandler.post(() -> editor.postInvalidateOnAnimation());
  }

  /**
   * Computes wrap count for a line.
   */
  public int computeWrapCountForLine(String line, int widthPx) {
    int[] starts = computeWrapStarts(line, widthPx, editor.textRender.paint, true);
    return Math.max(1, starts.length);
  }

  /**
   * Computes wrap count for a line with custom paint.
   */
  public int computeWrapCountForLine(String line, int widthPx, Paint p, boolean useSharedBuffer) {
    int[] starts = computeWrapStarts(line, widthPx, p, useSharedBuffer);
    return Math.max(1, starts.length);
  }

  /**
   * Gets wrap starts for a line.
   */
  public int[] getWrapStartsForLine(int globalLine, String line) {
    if (!isWordWrapEnabled) return new int[] {0};
    int widthPx = Math.max(1, Math.round(getWrapWidth()));
    if (wrapWidthPx != widthPx) {
      wrapWidthPx = widthPx;
      wrapCache.clear();
    }
    boolean cacheable = isWrapCacheableForLine(globalLine);
    if (!cacheable) {
      wrapCache.remove(globalLine);
      return computeWrapStarts(line, widthPx, editor.textRender.paint, true);
    }
    int[] cached = wrapCache.get(globalLine);
    if (cached != null) return cached;
    int[] starts = computeWrapStarts(line, widthPx, editor.textRender.paint, true);
    wrapCache.put(globalLine, starts);
    return starts;
  }

  /**
   * Checks if a line is cacheable for wrap.
   */
  public boolean isWrapCacheableForLine(int globalLine) {
    if (globalLine >= editor.textRender.windowStartLine && globalLine < editor.textRender.windowStartLine + editor.textRender.linesWindow.size()) {
      return true;
    }
    synchronized (editor.textRender.modifiedLines) {
      return editor.textRender.modifiedLines.containsKey(globalLine);
    }
  }

  /**
   * Computes wrap starts for a line.
   */
  public int[] computeWrapStarts(String line, int widthPx, Paint p, boolean useSharedBuffer) {
    if (line == null) return new int[] {0};
    int len = line.length();
    if (len == 0) return new int[] {0};
    if (shouldUseBreakTextWrap(line)) {
      return computeWrapStartsWithBreakText(line, widthPx, p);
    }
    float[] widths;
    if (useSharedBuffer) {
      if (editor.textRender.measureWidthBuffer == null || editor.textRender.measureWidthBuffer.length < len) {
        editor.textRender.measureWidthBuffer = new float[len];
      }
      widths = editor.textRender.measureWidthBuffer;
    } else {
      widths = new float[len];
    }
    p.getTextWidths(line, 0, len, widths);
    float[] adv = new float[len];
    for (int i = 0; i < len; i++) {
      adv[i] = getCharAdvanceWidth(line.charAt(i), widths[i], p);
    }
    ArrayList<Integer> starts = new ArrayList<>();
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

  /**
   * Checks if breakText wrap should be used.
   */
  public boolean shouldUseBreakTextWrap(String line) {
    if (editor.getVisualSpaceScale() != 1) return false;
    return line.indexOf('\t') < 0;
  }

  /**
   * Computes wrap starts using breakText.
   */
  public int[] computeWrapStartsWithBreakText(String line, int widthPx, Paint p) {
    int len = line.length();
    ArrayList<Integer> starts = new ArrayList<>();
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

  /**
   * Gets the character advance width.
   */
  private float getCharAdvanceWidth(char c, float measuredWidth, Paint p) {
    if (c == ' ') {
      return editor.getVisualSpaceScale() != 1 ? measuredWidth * editor.getVisualSpaceScale() : measuredWidth;
    }
    if (c == '\t') {
      int tabSize = TextRender.DEFAULT_TAB_SIZE_SPACES;
      float tabWidth = measuredWidth * tabSize;
      return editor.getVisualSpaceScale() != 1 ? tabWidth * editor.getVisualSpaceScale() : tabWidth;
    }
    return measuredWidth;
  }

  /**
   * Gets wrap segment index for a character.
   */
  public int getWrapSegmentIndexForChar(int[] starts, int charIndex) {
    if (starts == null || starts.length == 0) return 0;
    int idx = 0;
    for (int i = 0; i < starts.length; i++) {
      if (starts[i] <= charIndex) idx = i;
      else break;
    }
    return idx;
  }

  /**
   * Gets wrap segment start.
   */
  public int getWrapSegmentStart(int[] starts, int segIndex) {
    if (starts == null || starts.length == 0) return 0;
    if (segIndex <= 0) return starts[0];
    return starts[Math.min(segIndex, starts.length - 1)];
  }

  /**
   * Gets wrap segment end.
   */
  public int getWrapSegmentEnd(int[] starts, int segIndex, int lineLength) {
    if (starts == null || starts.length == 0) return lineLength;
    int next = segIndex + 1;
    if (next >= 0 && next < starts.length) return starts[next];
    return lineLength;
  }

  /**
   * Gets total visual line count.
   */
  public int getTotalVisualLineCount() {
    if (!isWordWrapEnabled) return editor.codeFold.getVisibleLineCount();
    int widthPx = Math.max(1, Math.round(getWrapWidth()));
    if (!isWrapMetricsUsableForWindow(widthPx)) {
      int total = editor.getLinesCount();
      if (total <= 0) total = editor.textRender.windowStartLine + editor.textRender.linesWindow.size();
      return Math.max(1, total);
    }
    return Math.max(1, totalWrapVisualLines);
  }

  /**
   * Gets wrap range count.
   */
  public int getWrapRangeCount(int startLine, int endLine) {
    if (wrapLinePrefix == null) return 0;
    int total = wrapLinePrefix.length - 1;
    int s = Math.max(0, Math.min(startLine, total - 1));
    int e = Math.max(s, Math.min(endLine, total - 1));
    return wrapLinePrefix[e + 1] - wrapLinePrefix[s];
  }

  /**
   * Gets visual position for index.
   */
  public VisualLinePosition getVisualPositionForIndex(int visualIndex) {
    int widthPx = Math.max(1, Math.round(getWrapWidth()));
    if (!isWrapMetricsUsableForWindow(widthPx)) {
      if (isWordWrapEnabled) {
        return getVisualPositionForIndexFallback(visualIndex, widthPx);
      }
      int line = editor.codeFold.mapVisibleIndexToGlobal(visualIndex);
      return new VisualLinePosition(line, 0);
    }
    int maxVisual = Math.max(0, totalWrapVisualLines - 1);
    int v = Math.max(0, Math.min(visualIndex, maxVisual));
    int line = findLineForVisualIndex(v);
    int seg = v - wrapLinePrefix[line];
    return new VisualLinePosition(line, seg);
  }

  /**
   * Gets visual position for index using fallback method.
   */
  public VisualLinePosition getVisualPositionForIndexFallback(int visualIndex, int widthPx) {
    int idx = Math.max(0, visualIndex);
    int baseLine = Math.max(0, editor.textRender.windowStartLine);
    int baseVisual = baseLine;
    if (wrapLinePrefix != null
        && wrapPrefixValidUpToLine >= baseLine
        && baseLine < wrapLinePrefix.length) {
      baseVisual = wrapLinePrefix[baseLine];
    }
    int remaining = idx - baseVisual;
    if (remaining <= 0) {
      return new VisualLinePosition(baseLine, 0);
    }

    int line = baseLine;
    int windowEnd;
    synchronized (editor.textRender.linesWindow) {
      windowEnd = editor.textRender.windowStartLine + editor.textRender.linesWindow.size() - 1;
    }
    if (windowEnd < baseLine) windowEnd = baseLine;

    while (line <= windowEnd) {
      String text = editor.getLineTextForRender(line);
      int[] starts = getWrapStartsForLine(line, text);
      int segCount = Math.max(1, starts.length);
      if (remaining < segCount) {
        return new VisualLinePosition(line, Math.max(0, Math.min(remaining, segCount - 1)));
      }
      remaining -= segCount;
      line++;
    }

    return new VisualLinePosition(windowEnd, 0);
  }

  /**
   * Finds line for visual index.
   */
  public int findLineForVisualIndex(int visualIndex) {
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

  /**
   * Checks if wrap metrics are usable for window.
   */
  public boolean isWrapMetricsUsableForWindow(int widthPx) {
    if (!isWordWrapEnabled) return false;
    if (!wrapMetricsReady || wrapLinePrefix == null || wrapLineCounts == null) return false;
    if (wrapMetricsWidth != widthPx) return false;
    int total = editor.getLinesCount();
    if (total <= 0) total = editor.textRender.windowStartLine + editor.textRender.linesWindow.size();
    if (total <= 0) return false;
    if (wrapLineCounts.length != total || wrapLinePrefix.length != total + 1) return false;
    int windowEnd = editor.getWindowEndLine();
    return wrapPrefixValidUpToLine >= windowEnd;
  }

  /**
   * Checks if wrap metrics are usable for a line.
   */
  public boolean isWrapMetricsUsableForLine(int line) {
    int widthPx = Math.max(1, Math.round(getWrapWidth()));
    if (!isWrapMetricsUsableForWindow(widthPx)) return false;
    return wrapPrefixValidUpToLine >= line;
  }

  /**
   * Patches wrap metrics for visual range.
   */
  public boolean patchWrapMetricsForVisualRange(
      int firstVisualIndex,
      int lastVisualIndex,
      @Nullable Map<Integer, String> directLines,
      int widthPx) {
    if (!isWordWrapEnabled) return false;
    if (!wrapMetricsReady || wrapLineCounts == null || wrapLinePrefix == null) return false;
    if (wrapMetricsWidth != widthPx) return false;
    if (wrapLineCounts.length + 1 != wrapLinePrefix.length) return false;

    final int anchorFirstVisual = firstVisualIndex;
    final VisualLinePosition anchorPos = getVisualPositionForIndex(anchorFirstVisual);
    final int anchorLine = anchorPos.line;
    final int anchorSeg = anchorPos.segment;

    boolean changed = false;

    int v = Math.max(0, firstVisualIndex);
    int vEnd = Math.max(v, lastVisualIndex);
    for (; v <= vEnd; v++) {
      VisualLinePosition pos = getVisualPositionForIndex(v);
      int line = pos.line;
      if (line < 0 || line >= wrapLineCounts.length) break;
      String text = editor.getLineTextForRenderWithDirect(line, directLines);
      int[] starts = getWrapStartsForLine(line, text);
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
        editor.scroll.scrollY += dv * editor.textRender.lineHeight;
        editor.scroll.clampScrollY();
      }
    }
    return true;
  }

  /**
   * Clamps segment end for wrap indicator.
   */
  public int clampSegmentEndForWrapIndicator(String line, int segStart, int segEnd) {
    if (!isWordWrapEnabled || line == null) return segEnd;
    int len = line.length();
    if (segEnd > len) segEnd = len;
    return Math.max(segStart, segEnd);
  }

  /**
   * Gets character index for X position in a range.
   */
  public int getCharIndexForXInRange(String text, int globalLine, int start, int end, float x) {
    if (text == null || text.isEmpty()) return 0;
    start = Math.max(0, Math.min(start, text.length()));
    end = Math.max(start, Math.min(end, text.length()));
    if (editor.textRender.isRtl) {
      float baseX = editor.getRtlSegmentBaseX(text, globalLine, start, end);
      x -= baseX;
      float w = editor.measureHighlightedSegmentWidth(text, globalLine, start, end);
      x = w - x;
    }
    if (x <= 0f) return start;
    if (editor.binaryRender.isBinarySafeRenderingEnabled()) {
      int[] spans = editor.binaryRender.getBinaryTokenSpans(globalLine);
      if (spans != null && spans.length > 0) {
        float padX = editor.binaryRender.binaryCaretNotationEnabled ? 0f : editor.binaryRender.binaryTokenPaddingX;
        return editor.binaryRender.getCharIndexForXBinary(
            text, start, end, x, editor.textRender.paint, spans, padX);
      }
    }
    int len = end - start;
    if (len <= 0) return start;
    if (editor.getVisualSpaceScale() == 1) {
      int count = editor.textRender.paint.breakText(text, start, end, true, x, null);
      int idx = start + Math.max(0, count);
      return Math.min(idx, end);
    }
    if (editor.textRender.measureWidthBuffer == null || editor.textRender.measureWidthBuffer.length < len) {
      editor.textRender.measureWidthBuffer = new float[len];
    }
    editor.textRender.paint.getTextWidths(text, start, end, editor.textRender.measureWidthBuffer);
    float current = 0f;
    for (int i = 0; i < len; i++) {
      float adv = getCharAdvanceWidth(text.charAt(start + i), editor.textRender.measureWidthBuffer[i], editor.textRender.paint);
      float mid = current + adv * 0.5f;
      if (x < mid) return start + i;
      if (x < current + adv) return start + i + 1;
      current += adv;
    }
    return end;
  }

  /**
   * Gets cursor target for position.
   */
  public EditOperators.CursorTarget getCursorTargetForPosition(
      float viewX, float viewY, @Nullable java.util.Map<Integer, String> directLines) {
    float y = viewY + editor.scroll.scrollY;
    int visualIndex = Math.max(0, (int) (y / editor.textRender.lineHeight));
    VisualLinePosition pos =
        isWordWrapEnabled
            ? getVisualPositionForIndex(visualIndex)
            : new VisualLinePosition(editor.codeFold.mapVisibleIndexToGlobal(visualIndex), 0);
    String line = editor.getLineTextForRenderWithDirect(pos.line, directLines);
    if (!isWordWrapEnabled) {
      float x = editor.viewToTextX(viewX);
      int charIndex = editor.getCharIndexForX(line, x, pos.line);
      int clamped = Math.max(0, Math.min(charIndex, editor.getLogicalLineLength(pos.line, line)));
      if (editor.binaryRender.isBinarySafeRenderingEnabled()) {
        clamped = editor.binaryRender.snapBinaryCursor(line, clamped, pos.line);
      }
      return new EditOperators.CursorTarget(pos.line, clamped);
    }
    int[] starts = getWrapStartsForLine(pos.line, line);
    int seg = Math.min(Math.max(0, pos.segment), Math.max(0, starts.length - 1));
    int segStart = getWrapSegmentStart(starts, seg);
    int segEnd = getWrapSegmentEnd(starts, seg, line.length());
    float x = editor.viewToTextX(viewX);
    int charIndex = getCharIndexForXInRange(line, pos.line, segStart, segEnd, x);
    int clamped = Math.max(0, Math.min(charIndex, line.length()));
    if (editor.binaryRender.isBinarySafeRenderingEnabled()) {
      clamped = editor.binaryRender.snapBinaryCursor(line, clamped, pos.line);
    }
    return new EditOperators.CursorTarget(pos.line, clamped);
  }

  // ========================================================================
  // Word Wrap Helper Methods
  // ========================================================================

  /**
   * Clamps segment end for wrap indicator with wrap width.
   */
  public int clampSegmentEndForWrapIndicator(String line, int segStart, int segEnd, int wrapWidthPx) {
    if (segEnd <= segStart) return segEnd;
    float reserved = editor.wordWrap.indicator.wordWrapIndicatorWidth + (editor.wordWrap.indicator.wordWrapIndicatorPadPx * 2f);
    float available = wrapWidthPx - reserved;
    if (available <= 0f) return segStart;
    float width = editor.measureTextWithVisualSpaces(line, segStart, segEnd, editor.textRender.paint);
    if (width <= available) return segEnd;
    int end = segEnd;
    while (end > segStart) {
      end--;
      float w = editor.measureTextWithVisualSpaces(line, segStart, end, editor.textRender.paint);
      if (w <= available) break;
    }
    return end;
  }

  // ========================================================================
  // Wrap Prefix Update Methods
  // ========================================================================

  /**
   * Apply pending wrap prefix update if any
   */
  public void applyPendingWrapPrefixUpdateIfAny() {
    if (!editor.zoom.pendingApplyWrapPrefixUpdate) return;
    if (!isWordWrapEnabled) {
      editor.zoom.pendingApplyWrapPrefixUpdate = false;
      editor.zoom.pendingWrapPrefixCounts = null;
      editor.zoom.pendingWrapPrefixPrefix = null;
      return;
    }
    if (editor.zoom.isZoomGestureActive()) return;
    if (editor.zoom.pendingWrapPrefixCounts == null || editor.zoom.pendingWrapPrefixPrefix == null) {
      editor.zoom.pendingApplyWrapPrefixUpdate = false;
      return;
    }
    // Only apply if the wrap width still matches; otherwise a new rebuild will be scheduled.
    int currentWidthPx = Math.max(1, Math.round(getWrapWidth()));
    if (editor.zoom.pendingWrapPrefixWidthPx != currentWidthPx) {
      editor.zoom.pendingApplyWrapPrefixUpdate = false;
      editor.zoom.pendingWrapPrefixCounts = null;
      editor.zoom.pendingWrapPrefixPrefix = null;
      return;
    }

    // Keep the current top visual line anchored while swapping in the new prefix arrays.
    int anchorFirstVisual = Math.max(0, (int) (editor.scroll.scrollY / editor.textRender.lineHeight));
    WordWrap.VisualLinePosition anchorPos = getVisualPositionForIndex(anchorFirstVisual);
    int anchorLine = anchorPos.line;
    int anchorSeg = anchorPos.segment;

    wrapLineCounts = editor.zoom.pendingWrapPrefixCounts;
    wrapLinePrefix = editor.zoom.pendingWrapPrefixPrefix;
    totalWrapVisualLines = editor.zoom.pendingWrapPrefixTotalVisualLines;
    wrapMetricsWidth = editor.zoom.pendingWrapPrefixWidthPx;
    wrapMetricsReady = true;
    wrapPrefixValidUpToLine = Math.max(wrapPrefixValidUpToLine, editor.zoom.pendingWrapPrefixValidUpToLine);

    editor.zoom.pendingApplyWrapPrefixUpdate = false;
    editor.zoom.pendingWrapPrefixCounts = null;
    editor.zoom.pendingWrapPrefixPrefix = null;

    if (anchorLine >= 0 && wrapLinePrefix != null && anchorLine < wrapLinePrefix.length) {
      int newAnchorFirstVisual = wrapLinePrefix[anchorLine] + Math.max(0, anchorSeg);
      int dv = newAnchorFirstVisual - anchorFirstVisual;
      if (dv != 0) {
        editor.scroll.scrollY += dv * editor.textRender.lineHeight;
        editor.scroll.clampScrollY();
      }
    }
  }

  // ========================================================================
  // Visual Line Position Methods
  // ========================================================================

  /**
   * Get visual index for line and character
   */
  public int getVisualIndexForLineAndChar(int line, int ch) {
    if (!isWrapMetricsUsableForLine(line)) {
      if (editor.codeFold.isCodeFoldingEnabled) {
        return editor.codeFold.getVisibleIndexForGlobalLine(line);
      }
      return Math.max(0, line);
    }
    int totalLines = wrapLinePrefix.length - 1;
    int safeLine = Math.max(0, Math.min(line, Math.max(0, totalLines - 1)));
    String text = editor.getLineTextForRender(safeLine);
    int[] starts = getWrapStartsForLine(safeLine, text);
    int seg = getWrapSegmentIndexForChar(starts, Math.max(0, Math.min(ch, text.length())));
    int visualIndex = wrapLinePrefix[safeLine] + seg;
    
    // When code folding is enabled, we need to adjust the visual index to account
    // for folded lines that come before this line
    if (editor.codeFold.isCodeFoldingEnabled) {
      // Get the base visible index for this global line
      int baseVisibleIndex = editor.codeFold.getVisibleIndexForGlobalLine(line);
      // Calculate the offset within the line (in terms of wrap segments)
      int wrapOffset = visualIndex - wrapLinePrefix[safeLine];
      // The final visual index is the base visible index plus the wrap offset
      visualIndex = baseVisibleIndex + wrapOffset;
    }
    
    return visualIndex;
  }

  /**
   * Get global line for Y position
   */
  public int getGlobalLineForY(float y) {
    int idx = Math.max(0, (int) (y / editor.textRender.lineHeight));
    if (isWordWrapEnabled) {
      return getVisualPositionForIndex(idx).line;
    }
    return editor.codeFold.mapVisibleIndexToGlobal(idx);
  }

}
