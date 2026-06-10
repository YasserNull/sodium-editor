package com.yn.sodiumeditor.core.wordwrap;

import android.graphics.Paint;
import com.yn.sodiumeditor.SodiumEditor;
import java.util.ArrayList;

/** Handles window snapshot-based wrap metrics patching. */
public class WordWrapSnapshotMetrics {
  private final SodiumEditor editor;
  private final WordWrap wordWrap;

  public WordWrapSnapshotMetrics(SodiumEditor editor, WordWrap wordWrap) {
    this.editor = editor;
    this.wordWrap = wordWrap;
  }

  public void buildWrapMetricsForWindowSnapshot() {
    int total = editor.view.getLinesCount();
    if (total <= 0) {
      total = editor.windowRender.windowStartLine + editor.windowRender.linesWindow.size();
    }
    if (total <= 0) {
      wordWrap.wrapLineCounts = null;
      wordWrap.wrapLinePrefix = null;
      wordWrap.totalWrapVisualLines = 0;
      wordWrap.wrapMetricsReady = true;
      return;
    }
    int widthPx = Math.max(1, Math.round(wordWrap.getWrapWidth()));
    int[] counts =
        (wordWrap.wrapLineCounts != null && wordWrap.wrapLineCounts.length == total)
            ? wordWrap.wrapLineCounts.clone()
            : null;
    if (counts == null) {
      counts = new int[total];
      for (int i = 0; i < total; i++) counts[i] = wordWrap.engine.getDefaultWrapCountForLine(i);
    }
    synchronized (editor.windowRender.linesWindow) {
      int start = editor.windowRender.windowStartLine;
      for (int i = 0; i < editor.windowRender.linesWindow.size(); i++) {
        int globalLine = start + i;
        if (globalLine >= 0 && globalLine < total) {
          counts[globalLine] =
              wordWrap.engine.getWrapCountForLine(
                  globalLine, editor.windowRender.linesWindow.get(i), widthPx);
        }
      }
    }
    int[] prefix = buildPrefix(counts);
    wordWrap.wrapLineCounts = counts;
    wordWrap.wrapLinePrefix = prefix;
    wordWrap.totalWrapVisualLines = prefix[prefix.length - 1];
    wordWrap.wrapMetricsWidth = widthPx;
    wordWrap.wrapPrefixValidUpToLine = total - 1;
    wordWrap.wrapMetricsReady = true;
  }

  public void scheduleWrapMetricsSnapshotIfNeeded(int widthPx) {
    if (wordWrap.shouldSuppressWrapMetricsForFastSelectAll()) return;
    int start;
    ArrayList<String> snapshot = new ArrayList<>();
    synchronized (editor.windowRender.linesWindow) {
      start = editor.windowRender.windowStartLine;
      if (!editor.windowRender.linesWindow.isEmpty()) {
        snapshot.addAll(editor.windowRender.linesWindow);
      }
    }
    if (snapshot.isEmpty()) return;
    if (wordWrap.wrapSnapshotBuilding
        && wordWrap.wrapSnapshotWidth == widthPx
        && wordWrap.wrapSnapshotStart == start
        && wordWrap.wrapSnapshotSize == snapshot.size()) return;
    wordWrap.wrapSnapshotWidth = widthPx;
    wordWrap.wrapSnapshotStart = start;
    wordWrap.wrapSnapshotSize = snapshot.size();
    wordWrap.wrapSnapshotBuilding = true;
    final int token = wordWrap.wrapSnapshotToken.incrementAndGet();
    final Paint paint = new Paint(editor.textRender.paint);
    editor.fileIO.ioHandler.post(
        () -> {
          int total = editor.view.getLinesCount();
          if (total <= 0) total = start + snapshot.size();
          if (total <= 0) {
            wordWrap.mainHandler.post(
                () -> {
                  if (token == wordWrap.wrapSnapshotToken.get()) {
                    wordWrap.wrapMetricsReady = true;
                    wordWrap.wrapSnapshotBuilding = false;
                  }
                });
            return;
          }
          int[] counts =
              (wordWrap.wrapLineCounts == null
                      || wordWrap.wrapLineCounts.length != total
                      || wordWrap.wrapMetricsWidth != widthPx)
                  ? new int[total]
                  : wordWrap.wrapLineCounts.clone();
          if (counts.length != total) {
            counts = new int[total];
            for (int i = 0; i < total; i++) {
              counts[i] = wordWrap.engine.getDefaultWrapCountForLine(i);
            }
          }
          for (int i = 0; i < snapshot.size(); i++) {
            int globalLine = start + i;
            if (globalLine >= 0 && globalLine < total) {
              counts[globalLine] =
                  wordWrap.engine.getWrapCountForLine(globalLine, snapshot.get(i), widthPx, paint);
            }
          }
          int[] prefix = buildPrefix(counts);
          final int[] finalCounts = counts;
          final int[] finalPrefix = prefix;
          final int totalVisual = prefix[prefix.length - 1];
          wordWrap.mainHandler.post(
              () -> {
                if (token == wordWrap.wrapSnapshotToken.get()) {
                  wordWrap.wrapLineCounts = finalCounts;
                  wordWrap.wrapLinePrefix = finalPrefix;
                  wordWrap.totalWrapVisualLines = totalVisual;
                  wordWrap.wrapMetricsWidth = widthPx;
                  wordWrap.wrapMetricsReady = true;
                  wordWrap.wrapSnapshotBuilding = false;
                  editor.postInvalidateOnAnimation();
                }
              });
        });
  }

  private static int[] buildPrefix(int[] counts) {
    int[] prefix = new int[counts.length + 1];
    int running = 0;
    for (int i = 0; i < counts.length; i++) {
      running += counts[i];
      prefix[i + 1] = running;
    }
    return prefix;
  }
}
