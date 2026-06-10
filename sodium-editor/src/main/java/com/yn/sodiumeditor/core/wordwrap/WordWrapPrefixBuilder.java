package com.yn.sodiumeditor.core.wordwrap;

import android.graphics.Paint;
import com.yn.sodiumeditor.SodiumEditor;
import java.io.RandomAccessFile;

/** Handles wrap prefix rebuilds, anchor preservation, and zoom pending updates. */
public class WordWrapPrefixBuilder {
  private final SodiumEditor editor;
  private final WordWrap wordWrap;

  public WordWrapPrefixBuilder(SodiumEditor editor, WordWrap wordWrap) {
    this.editor = editor;
    this.wordWrap = wordWrap;
  }

  public void scheduleWrapPrefixRebuildUpToWindow() {
    if (!wordWrap.isWordWrapEnabled || wordWrap.shouldSuppressWrapMetricsForFastSelectAll()) return;
    int total = editor.view.getLinesCount();
    if (total <= 0) return;
    int target;
    synchronized (editor.windowRender.linesWindow) {
      target =
          Math.min(
              total - 1,
              editor.windowRender.windowStartLine + editor.windowRender.linesWindow.size() - 1);
    }
    if (target < 0) return;
    int widthPx = Math.max(1, Math.round(wordWrap.getWrapWidth()));
    if (wordWrap.wrapPrefixBuilding
        && wordWrap.wrapPrefixWidth == widthPx
        && wordWrap.wrapPrefixTargetLine >= target) return;
    wordWrap.wrapPrefixBuilding = true;
    wordWrap.wrapPrefixWidth = widthPx;
    wordWrap.wrapPrefixTargetLine = target;
    if (!editor.scroll.scroller.isFinished()) editor.scroll.scroller.abortAnimation();
    final int token = wordWrap.wrapPrefixToken.incrementAndGet();
    final int[] base =
        (wordWrap.wrapLineCounts != null && wordWrap.wrapLineCounts.length == total)
            ? wordWrap.wrapLineCounts.clone()
            : null;
    WordWrap.VisualLinePosition anchor =
        wordWrap.getVisualPositionForIndex(
            Math.max(0, (int) (editor.scroll.scrollY / editor.textRender.lineHeight)));
    final int oldPrefix =
        (wordWrap.wrapLinePrefix != null
                && anchor.line >= 0
                && anchor.line < wordWrap.wrapLinePrefix.length)
            ? wordWrap.wrapLinePrefix[anchor.line]
            : anchor.line;
    final Paint paint = new Paint(editor.textRender.paint);
    editor.fileIO.ioHandler.post(
        () -> {
          if (token != wordWrap.wrapPrefixToken.get()) return;
          int[] counts = (base != null) ? base : new int[total];
          if (base == null) {
            for (int i = 0; i < total; i++) {
              counts[i] = wordWrap.engine.getDefaultWrapCountForLine(i);
            }
          }
          if (editor.fileIO.sourceFile == null || !editor.fileIO.sourceFile.exists()) {
            synchronized (editor.windowRender.linesWindow) {
              if (editor.windowRender.windowStartLine == 0) {
                for (int i = 0;
                    i <= Math.min(target, editor.windowRender.linesWindow.size() - 1);
                    i++) {
                  counts[i] =
                      wordWrap.engine.getWrapCountForLine(
                          i, editor.windowRender.linesWindow.get(i), widthPx, paint);
                }
              } else {
                postPrefixStopped(token);
                return;
              }
            }
          } else {
            try (RandomAccessFile raf = new RandomAccessFile(editor.fileIO.sourceFile, "r")) {
              long fileLen = raf.length();
              for (int i = 0; i <= target; i++) {
                if (token != wordWrap.wrapPrefixToken.get()) return;
                counts[i] =
                    wordWrap.fileLineReader.getWrapCountForFileLine(
                        i, raf, fileLen, widthPx, paint);
              }
            } catch (Exception e) {
              postPrefixStopped(token);
              return;
            }
          }
          int[] prefix = buildPrefix(counts);
          final int[] finalCounts = counts;
          final int[] finalPrefix = prefix;
          final int totalVisual = prefix[prefix.length - 1];
          final int newPrefix =
              (anchor.line >= 0 && anchor.line < prefix.length) ? prefix[anchor.line] : oldPrefix;
          wordWrap.mainHandler.post(
              () -> {
                if (token != wordWrap.wrapPrefixToken.get()
                    || Math.max(1, Math.round(wordWrap.getWrapWidth())) != widthPx) {
                  wordWrap.wrapPrefixBuilding = false;
                  return;
                }
                wordWrap.wrapPrefixBuilding = false;
                if (editor.zoom.isZoomGestureActive()) {
                  editor.zoom.pendingWrapPrefixCounts = finalCounts;
                  editor.zoom.pendingWrapPrefixPrefix = finalPrefix;
                  editor.zoom.pendingWrapPrefixTotalVisualLines = totalVisual;
                  editor.zoom.pendingWrapPrefixWidthPx = widthPx;
                  editor.zoom.pendingWrapPrefixValidUpToLine =
                      Math.max(
                          wordWrap.wrapPrefixValidUpToLine, Math.min(target, total - 1));
                  editor.zoom.pendingApplyWrapPrefixUpdate = true;
                  return;
                }
                wordWrap.wrapLineCounts = finalCounts;
                wordWrap.wrapLinePrefix = finalPrefix;
                wordWrap.totalWrapVisualLines = totalVisual;
                wordWrap.wrapMetricsWidth = widthPx;
                wordWrap.wrapMetricsReady = true;
                wordWrap.wrapPrefixValidUpToLine =
                    Math.max(wordWrap.wrapPrefixValidUpToLine, Math.min(target, total - 1));
                if (newPrefix != oldPrefix) {
                  editor.scroll.scrollY += (newPrefix - oldPrefix) * editor.textRender.lineHeight;
                  editor.scroll.clampScrollY();
                }
                editor.postInvalidateOnAnimation();
              });
        });
  }

  public void requestWrapPrefixRebuild() {
    if (!wordWrap.isWordWrapEnabled) return;
    if (editor.zoom.isScaling
        || (editor.scaleGestureDetector != null && editor.scaleGestureDetector.isInProgress())) {
      wordWrap.wrapPrefixRebuildPending = true;
      return;
    }
    scheduleWrapPrefixRebuildUpToWindow();
  }

  public void cancelWrapPrefixRebuildForInteraction() {
    if (!wordWrap.wrapPrefixBuilding) return;
    wordWrap.wrapPrefixToken.incrementAndGet();
    wordWrap.wrapPrefixBuilding = false;
    wordWrap.wrapPrefixRebuildPending = true;
  }

  public void cancelWrapWorkForPriority() {
    if (!wordWrap.isWordWrapEnabled) return;
    wordWrap.wrapMetricsToken.incrementAndGet();
    wordWrap.wrapSnapshotToken.incrementAndGet();
    wordWrap.wrapPrefixToken.incrementAndGet();
    wordWrap.wrapMetricsBuilding = false;
    wordWrap.wrapSnapshotBuilding = false;
    wordWrap.wrapPrefixBuilding = false;
  }

  public void applyPendingWrapPrefixUpdateIfAny() {
    if (!editor.zoom.pendingApplyWrapPrefixUpdate
        || !wordWrap.isWordWrapEnabled
        || editor.zoom.isZoomGestureActive()
        || editor.zoom.pendingWrapPrefixCounts == null
        || editor.zoom.pendingWrapPrefixPrefix == null) return;
    int widthPx = Math.max(1, Math.round(wordWrap.getWrapWidth()));
    if (editor.zoom.pendingWrapPrefixWidthPx != widthPx) {
      editor.zoom.pendingApplyWrapPrefixUpdate = false;
      editor.zoom.pendingWrapPrefixCounts = null;
      editor.zoom.pendingWrapPrefixPrefix = null;
      return;
    }
    int anchorVisual = Math.max(0, (int) (editor.scroll.scrollY / editor.textRender.lineHeight));
    WordWrap.VisualLinePosition anchor = wordWrap.getVisualPositionForIndex(anchorVisual);
    wordWrap.wrapLineCounts = editor.zoom.pendingWrapPrefixCounts;
    wordWrap.wrapLinePrefix = editor.zoom.pendingWrapPrefixPrefix;
    wordWrap.totalWrapVisualLines = editor.zoom.pendingWrapPrefixTotalVisualLines;
    wordWrap.wrapMetricsWidth = editor.zoom.pendingWrapPrefixWidthPx;
    wordWrap.wrapMetricsReady = true;
    wordWrap.wrapPrefixValidUpToLine =
        Math.max(wordWrap.wrapPrefixValidUpToLine, editor.zoom.pendingWrapPrefixValidUpToLine);
    editor.zoom.pendingApplyWrapPrefixUpdate = false;
    editor.zoom.pendingWrapPrefixCounts = null;
    editor.zoom.pendingWrapPrefixPrefix = null;
    if (anchor.line >= 0
        && wordWrap.wrapLinePrefix != null
        && anchor.line < wordWrap.wrapLinePrefix.length) {
      int deltaVisual =
          (wordWrap.wrapLinePrefix[anchor.line] + Math.max(0, anchor.segment)) - anchorVisual;
      if (deltaVisual != 0) {
        editor.scroll.scrollY += deltaVisual * editor.textRender.lineHeight;
        editor.scroll.clampScrollY();
      }
    }
  }

  private void postPrefixStopped(int token) {
    wordWrap.mainHandler.post(
        () -> {
          if (token == wordWrap.wrapPrefixToken.get()) wordWrap.wrapPrefixBuilding = false;
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
