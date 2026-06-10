package com.yn.sodiumeditor.core.wordwrap;

import android.graphics.Paint;
import androidx.annotation.Nullable;
import com.yn.sodiumeditor.SodiumEditor;
import java.io.RandomAccessFile;
import java.util.Map;

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
          wordWrap.engine.getWrapCountForLine(
              i, editor.windowRender.getLineTextForRender(i), widthPx);
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

  public void onLineContentChanged(int globalLine, @Nullable String text) {
    if (!wordWrap.isWordWrapEnabled) return;
    wordWrap.cache.removeLine(globalLine);
    int widthPx = Math.max(1, Math.round(wordWrap.getWrapWidth()));
    if (!wordWrap.wrapMetricsReady
        || wordWrap.wrapLineCounts == null
        || wordWrap.wrapLinePrefix == null
        || wordWrap.wrapMetricsWidth != widthPx
        || globalLine < 0
        || globalLine >= wordWrap.wrapLineCounts.length) {
      wordWrap.invalidateWrapMetrics();
      return;
    }
    int newCount = wordWrap.engine.getWrapCountForLine(globalLine, text, widthPx);
    int oldCount = wordWrap.wrapLineCounts[globalLine];
    if (newCount == oldCount) return;
    int delta = newCount - oldCount;
    wordWrap.wrapLineCounts[globalLine] = newCount;
    for (int i = globalLine + 1; i < wordWrap.wrapLinePrefix.length; i++) {
      wordWrap.wrapLinePrefix[i] += delta;
    }
    wordWrap.totalWrapVisualLines += delta;
  }

  public void onLineCountChanged() {
    if (wordWrap.isWordWrapEnabled) wordWrap.invalidateWrapMetrics();
    editor.lineNumber.invalidateLineNumberCache();
  }

  public int getTotalVisualLineCount() {
    if (!wordWrap.isWordWrapEnabled) return Math.max(1, editor.view.getLinesCount());
    if (!isWrapMetricsUsableForWindow(Math.max(1, Math.round(wordWrap.getWrapWidth())))) {
      int total = editor.view.getLinesCount();
      if (total <= 0)
        total = editor.windowRender.windowStartLine + editor.windowRender.linesWindow.size();
      return Math.max(1, total);
    }
    return Math.max(1, wordWrap.totalWrapVisualLines);
  }

  public int getWrapRangeCount(int start, int end) {
    if (wordWrap.wrapLinePrefix == null) return 0;
    int total = wordWrap.wrapLinePrefix.length - 1;
    return wordWrap.wrapLinePrefix[Math.max(0, Math.min(end, total - 1)) + 1]
        - wordWrap.wrapLinePrefix[Math.max(0, Math.min(start, total - 1))];
  }

  public boolean isWrapMetricsUsableForWindow(int widthPx) {
    if (!wordWrap.isWordWrapEnabled
        || !wordWrap.wrapMetricsReady
        || wordWrap.wrapLinePrefix == null
        || wordWrap.wrapLineCounts == null
        || wordWrap.wrapMetricsWidth != widthPx) return false;
    int total = editor.view.getLinesCount();
    if (total <= 0) {
      total = editor.windowRender.windowStartLine + editor.windowRender.linesWindow.size();
    }
    return total > 0
        && wordWrap.wrapLineCounts.length == total
        && wordWrap.wrapLinePrefix.length == total + 1
        && wordWrap.wrapPrefixValidUpToLine >= editor.windowRender.getWindowEndLine();
  }

  public boolean isWrapMetricsUsableForLine(int line) {
    return isWrapMetricsUsableForWindow(Math.max(1, Math.round(wordWrap.getWrapWidth())))
        && wordWrap.wrapPrefixValidUpToLine >= line;
  }

  public boolean patchWrapMetricsForVisualRange(
      int firstVisual, int lastVisual, @Nullable Map<Integer, String> directLines, int widthPx) {
    if (!wordWrap.isWordWrapEnabled
        || !wordWrap.wrapMetricsReady
        || wordWrap.wrapLineCounts == null
        || wordWrap.wrapLinePrefix == null
        || wordWrap.wrapMetricsWidth != widthPx
        || wordWrap.wrapLineCounts.length + 1 != wordWrap.wrapLinePrefix.length) return false;
    final WordWrap.VisualLinePosition anchor = wordWrap.getVisualPositionForIndex(firstVisual);
    boolean changed = false;
    for (int visual = Math.max(0, firstVisual);
        visual <= Math.max(firstVisual, lastVisual);
        visual++) {
      WordWrap.VisualLinePosition position = wordWrap.getVisualPositionForIndex(visual);
      if (position.line < 0 || position.line >= wordWrap.wrapLineCounts.length) break;
      int newCount =
          wordWrap.engine.getWrapCountForLine(
              position.line,
              editor.windowRender.getLineTextForRenderWithDirect(position.line, directLines),
              widthPx);
      if (newCount == wordWrap.wrapLineCounts[position.line]) continue;
      int delta = newCount - wordWrap.wrapLineCounts[position.line];
      wordWrap.wrapLineCounts[position.line] = newCount;
      for (int i = position.line + 1; i < wordWrap.wrapLinePrefix.length; i++) {
        wordWrap.wrapLinePrefix[i] += delta;
      }
      wordWrap.totalWrapVisualLines += delta;
      changed = true;
    }
    if (changed && anchor.line >= 0 && anchor.line < wordWrap.wrapLinePrefix.length) {
      int deltaVisual =
          (wordWrap.wrapLinePrefix[anchor.line] + Math.max(0, anchor.segment)) - firstVisual;
      if (deltaVisual != 0) {
        editor.scroll.scrollY += deltaVisual * editor.textRender.lineHeight;
        editor.scroll.clampScrollY();
      }
    }
    return changed;
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
        int c =
            wordWrap.fileLineReader.getWrapCountForFileLine(
                lineIdx, raf, fileLen, widthPx, wrapPaint);
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
