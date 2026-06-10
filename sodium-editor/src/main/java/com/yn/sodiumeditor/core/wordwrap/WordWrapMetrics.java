package com.yn.sodiumeditor.core.wordwrap;

import android.graphics.Paint;
import com.yn.sodiumeditor.SodiumEditor;
import java.io.RandomAccessFile;

/** Manages word wrap metrics, including line counts and prefix sums. */
public class WordWrapMetrics {
  private final SodiumEditor editor;
  private final WordWrap wordWrap;

  public WordWrapMetrics(SodiumEditor editor, WordWrap wordWrap) {
    this.editor = editor;
    this.wordWrap = wordWrap;
  }

  public void scheduleWrapMetricsBuild() {
    if (!wordWrap.isWordWrapEnabled
        || wordWrap.shouldSuppressWrapMetricsForFastSelectAll()
        || editor.getWidth() <= 0) return;
    if (editor.fileIO.sourceFile == null || !editor.fileIO.isIndexReady) {
      buildWrapMetricsInMemory();
      return;
    }
    final int token = wordWrap.wrapMetricsToken.incrementAndGet();
    final int widthPx = Math.max(1, Math.round(wordWrap.getWrapWidth()));
    final Paint wrapPaint = new Paint(editor.textRender.paint);
    wordWrap.wrapMetricsBuilding = true;
    editor.fileIO.ioHandler.post(() -> buildWrapMetricsFromFile(token, widthPx, wrapPaint));
  }

  public void buildWrapMetricsInMemory() {
    int total = getEffectiveLinesCount();
    if (total <= 0) {
      resetMetrics(true);
      return;
    }
    int widthPx = Math.max(1, Math.round(wordWrap.getWrapWidth()));
    int[] counts = new int[total];
    int[] prefix = new int[total + 1];
    int running = 0;
    for (int i = 0; i < total; i++) {
      int c =
          wordWrap.calculator.computeWrapCountForLine(
              editor.windowRender.getLineTextForRender(i), widthPx, editor.textRender.paint, true);
      counts[i] = c;
      running += c;
      prefix[i + 1] = running;
    }
    applyMetrics(
        counts,
        prefix,
        running,
        widthPx,
        (editor.windowRender.windowStartLine == 0 ? total - 1 : -1));
  }

  private void buildWrapMetricsFromFile(int token, int widthPx, Paint wrapPaint) {
    int total = getEffectiveLinesCount();
    if (total <= 0) {
      resetMetrics(false);
      return;
    }
    int[] counts = new int[total];
    int[] prefix = new int[total + 1];
    int running = 0;
    try (RandomAccessFile raf = new RandomAccessFile(editor.fileIO.sourceFile, "r")) {
      long fileLen = raf.length();
      int lineIdx = 0;
      while (lineIdx < total) {
        if (token != wordWrap.wrapMetricsToken.get()) {
          wordWrap.wrapMetricsBuilding = false;
          return;
        }
        int c = wordWrap.getWrapCountForFileLine(lineIdx, raf, fileLen, widthPx, wrapPaint);
        counts[lineIdx] = c;
        running += c;
        prefix[lineIdx + 1] = running;
        lineIdx++;
      }
    } catch (Exception e) {
      wordWrap.wrapMetricsBuilding = false;
      return;
    }
    if (token == wordWrap.wrapMetricsToken.get()) {
      applyMetrics(counts, prefix, running, widthPx, total - 1);
      wordWrap.wrapMetricsBuilding = false;
    }
  }

  private void applyMetrics(int[] counts, int[] prefix, int totalVisual, int width, int validUpTo) {
    wordWrap.wrapLineCounts = counts;
    wordWrap.wrapLinePrefix = prefix;
    wordWrap.totalWrapVisualLines = totalVisual;
    wordWrap.wrapMetricsWidth = width;
    wordWrap.wrapMetricsReady = true;
    wordWrap.wrapPrefixValidUpToLine = validUpTo;
    wordWrap.mainHandler.post(() -> editor.postInvalidateOnAnimation());
  }

  private void resetMetrics(boolean markReady) {
    wordWrap.wrapLineCounts = null;
    wordWrap.wrapLinePrefix = null;
    wordWrap.totalWrapVisualLines = 0;
    wordWrap.wrapMetricsReady = markReady;
    wordWrap.wrapMetricsBuilding = false;
    wordWrap.mainHandler.post(() -> editor.postInvalidateOnAnimation());
  }

  private int getEffectiveLinesCount() {
    int total = editor.view.getLinesCount();
    if (total <= 0)
      total = editor.windowRender.windowStartLine + editor.windowRender.linesWindow.size();
    return total;
  }
}
