package com.yn.sodiumeditor.renderer;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.util.SparseIntArray;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.yn.sodiumeditor.SodiumEditorView;
import com.yn.sodiumeditor.renderer.TextRender;

public final class ViewRender {
  private final SodiumEditorView view;
  public final TextRender textRender;

  static final class StreamedSliceRequest {
    final int line;
    final int start;
    final int end;

    StreamedSliceRequest(int line, int start, int end) {
      this.line = line;
      this.start = start;
      this.end = end;
    }
  }

  public ViewRender(SodiumEditorView view) {
    this.view = view;
    this.textRender = new TextRender(view);
  }

  private void computeStreamedSliceBounds(
      @Nullable String lineText, int globalLine, int lineLength, int[] out) {
    if (out == null || out.length < 2) return;
    int len = Math.max(0, lineLength);
    if (len <= 0) {
      out[0] = 0;
      out[1] = 0;
      return;
    }
    float avg = view.highlightManager.getAverageCharWidthForLine((lineText == null) ? "" : lineText, globalLine);
    if (avg <= 0f) avg = view.paint.measureText(" ");
    float viewLeft = view.lineNumberManager.getContentViewLeft(view.isRtl);
    float viewRight = view.lineNumberManager.getContentViewRight(view.getWidth(), view.isRtl);
    float leftX = viewLeft + view.getEffectiveScrollX() - view.getTextStartX();
    float rightX = viewRight + view.getEffectiveScrollX() - view.getTextStartX();
    if (view.isRtl) {
      float w = avg * len;
      float baseX = view.getTextAreaWidth();
      leftX = w - (leftX - baseX);
      rightX = w - (rightX - baseX);
    }
    int start = (int) Math.floor(leftX / avg);
    int end = (int) Math.ceil(rightX / avg);
    if (end < start) {
      int t = start;
      start = end;
      end = t;
    }
    int pad = Math.max(0, view.visibleCharPadding);
    start = Math.max(0, start - pad);
    end = Math.min(len, end + pad);
    out[0] = start;
    out[1] = end;
  }

  private int getInitialStreamedSliceSize() {
    int base = Math.max(128, view.colsWidthCacheSize);
    int pad = Math.max(0, view.visibleCharPadding) * 2;
    return Math.max(base, pad);
  }

  public void setWindowSize(int size) {
    int safe = Math.max(10, size);
    int minWindow = view.computeMinWindowSize();
    if (safe < minWindow) safe = minWindow;
    if (view.windowSize == safe) return;
    view.windowSize = safe;
    view.invalidateHighlightEnsureRange();
    view.bracketGuideManager.invalidateCache();
    if (view.wordWrapManager.isWordWrapEnabled) view.wordWrapManager.invalidateWrapMetrics(view, true);
    view.wordWrapManager.requestWrapPrefixRebuild(view);
    view.reloadWindowAroundVisible(false);
  }

  public void setRenderWindow(int windowSize, int prefetchLines) {
    int safeWindow = Math.max(10, windowSize);
    int safePrefetch = Math.max(0, prefetchLines);
    int minWindow = view.computeMinWindowSizeForPrefetch(safePrefetch);
    if (safeWindow < minWindow) safeWindow = minWindow;
    if (view.windowSize == safeWindow && view.prefetchLines == safePrefetch) return;
    view.windowSize = safeWindow;
    view.prefetchLines = safePrefetch;
    view.invalidateHighlightEnsureRange();
    view.bracketGuideManager.invalidateCache();
    if (view.wordWrapManager.isWordWrapEnabled) view.wordWrapManager.invalidateWrapMetrics(view, true);
    view.wordWrapManager.requestWrapPrefixRebuild(view);
    view.reloadWindowAroundVisible(false);
  }

  public void setPrefetchLines(int lines) {
    int safe = Math.max(0, lines);
    if (view.prefetchLines == safe) return;
    view.prefetchLines = safe;
    int minWindow = view.computeMinWindowSize();
    if (view.windowSize < minWindow) view.windowSize = minWindow;
    view.invalidateHighlightEnsureRange();
    view.bracketGuideManager.invalidateCache();
    if (view.wordWrapManager.isWordWrapEnabled) view.wordWrapManager.invalidateWrapMetrics(view, true);
    view.wordWrapManager.requestWrapPrefixRebuild(view);
    view.reloadWindowAroundVisible(false);
  }

  public int computeMinWindowSizeForPrefetch(int prefetch) {
    if (view.lineHeight <= 0f || view.getHeight() <= 0) return 10;
    float effectiveHeight = (view.keyboardHeight > 0) ? view.getHeight() - view.keyboardHeight : view.getHeight();
    int visibleLines = Math.max(1, (int) Math.ceil(effectiveHeight / view.lineHeight) + 2);
    int minTotal = Math.max(visibleLines * 2, visibleLines + 6);
    int minWindow = minTotal - (Math.max(0, prefetch) * 2);
    return Math.max(10, minWindow);
  }

  public void reloadWindowAroundVisible(boolean recalcWidthSync) {
    if (view.getWidth() == 0 || view.getHeight() == 0) {
      view.invalidate();
      return;
    }
    int firstVisibleLine = Math.max(0, view.getGlobalLineForY(view.scrollManager.scrollY));
    int targetStart = Math.max(0, firstVisibleLine - view.prefetchLines);
    view.loadWindowAround(targetStart, null, recalcWidthSync);
  }

  public void setLineWidthCacheSize(int size) {
    int safe = Math.max(10, size);
    if (view.lineWidthCacheSize == safe) return;
    view.lineWidthCacheSize = safe;
    synchronized (view.lineWidthCache) {
      if (view.lineWidthCache.size() > view.lineWidthCacheSize) {
        java.util.Iterator<java.util.Map.Entry<Integer, Float>> it = view.lineWidthCache.entrySet().iterator();
        while (view.lineWidthCache.size() > view.lineWidthCacheSize && it.hasNext()) {
          it.next();
          it.remove();
        }
      }
    }
  }

  public void setPrefetchCols(int cols) {
    int safe = Math.max(0, cols);
    if (view.prefetchCols == safe) return;
    view.prefetchCols = safe;
    view.invalidate();
  }

  public void setColsWidthCacheSize(int size) {
    int safe = Math.max(16, size);
    if (view.colsWidthCacheSize == safe) return;
    view.colsWidthCacheSize = safe;
    synchronized (view.avgCharWidthCache) {
      if (view.avgCharWidthCache.size() > view.colsWidthCacheSize) {
        java.util.Iterator<java.util.Map.Entry<Integer, Float>> it = view.avgCharWidthCache.entrySet().iterator();
        while (view.avgCharWidthCache.size() > view.colsWidthCacheSize && it.hasNext()) {
          it.next();
          it.remove();
        }
      }
    }
  }

  public void checkAndLoadWindow() {
    if (view.fileManager.getSourceFile() == null || view.fileManager.isFileCleared()) return;
    if (view.getWidth() == 0 || view.getHeight() == 0) return;
    if (view.isWindowLoading) return;

    int firstVisibleIndex = (int) (view.scrollManager.scrollY / view.lineHeight);
    int lastVisibleIndex = firstVisibleIndex + (int) Math.ceil(view.getHeight() / view.lineHeight);
    int firstVisibleLine;
    int lastVisibleLine;
    if (view.wordWrapManager.isWordWrapEnabled) {
      firstVisibleLine = view.wordWrapManager.getVisualPositionForIndex(view, firstVisibleIndex).line;
      lastVisibleLine = view.wordWrapManager.getVisualPositionForIndex(view, lastVisibleIndex).line;
    } else {
      firstVisibleLine = view.mapVisibleIndexToGlobal(firstVisibleIndex);
      lastVisibleLine = view.mapVisibleIndexToGlobal(lastVisibleIndex);
    }
    firstVisibleLine = Math.max(0, firstVisibleLine);
    lastVisibleLine = Math.max(firstVisibleLine, lastVisibleLine);
    int winEnd;
    synchronized (view.linesWindow) {
      winEnd = view.windowStartLine + view.linesWindow.size() - 1;
    }

    int topMargin = Math.max(0, view.prefetchLines);
    int bottomMargin = Math.max(0, view.prefetchLines);

    boolean needTop = view.windowStartLine > 0 && firstVisibleLine < view.windowStartLine + topMargin;
    boolean needBottom = !view.isEof && lastVisibleLine > winEnd - bottomMargin;
    boolean outside = firstVisibleLine < view.windowStartLine || firstVisibleLine > winEnd;

    if (needTop || needBottom || outside) {
      int targetStart = Math.max(0, firstVisibleLine - view.prefetchLines);
      loadWindowAround(targetStart, null, false);
    }
  }

  public void loadWindowAround(int startLine, @Nullable Runnable onComplete) {
    loadWindowAround(startLine, onComplete, true);
  }

  public void loadWindowAround(
      int startLine, @Nullable Runnable onComplete, boolean recalculateWidthSync) {
    if (view.isWindowLoading) return;
    view.maxWidthRecalcToken++;

    if (view.fileManager.isFileCleared()) {
      if (onComplete != null) {
        view.post(onComplete);
      }
      return;
    }

    if (view.fileManager.getSourceFile() == null) {
      if (onComplete != null) view.post(onComplete);
      return;
    }

    view.isWindowLoading = true;
    final int taskVersion = view.ioTaskVersion.incrementAndGet();
    final int requestedStart = Math.max(0, startLine);

    view.ioHandler.post(
        () -> {
          try {
            if (taskVersion != view.ioTaskVersion.get()) {
              view.post(
                  () -> {
                    view.isWindowLoading = false;
                    checkAndLoadWindow();
                  });
              return;
            }

            int actualStart = requestedStart;

            if (view.isIndexReady) {
              synchronized (view.lineOffsetsLock) {
                if (view.lineOffsets.length > 0 && actualStart >= view.lineOffsets.length) {
                  actualStart = Math.max(0, view.lineOffsets.length - 1);
                }
              }
            }

            java.util.List<String> newWin = new java.util.ArrayList<>();
            android.util.SparseIntArray newStreamedLengths = new android.util.SparseIntArray();
            android.util.SparseIntArray newStreamedSliceStarts = new android.util.SparseIntArray();
            boolean fileEndsWithNewline = false;
            boolean reachedEof = false;
            boolean trailingEmptyFromIndex = false;

            if (view.fileManager.isIndexReady()) {
              try (java.io.RandomAccessFile raf = new java.io.RandomAccessFile(view.fileManager.getSourceFile(), "r")) {
                long fileLen = raf.length();
                if (fileLen > 0) {
                  raf.seek(fileLen - 1);
                  fileEndsWithNewline = (raf.read() == '\n');
                }
                int limit = view.windowSize + (view.prefetchLines * 2);
                int lineIndex = actualStart;
                int maxLine;
                synchronized (view.fileManager.lineOffsetsLock) {
                  maxLine = view.fileManager.getLineOffsets().length;
                }
                while (newWin.size() < limit) {
                  if (lineIndex >= maxLine) {
                    reachedEof = true;
                    break;
                  }
                  long lineStart;
                  synchronized (view.lineOffsetsLock) {
                    lineStart = view.lineOffsets[lineIndex];
                  }
                  long lineByteLen = view.getLineByteLengthFromIndex(raf, lineIndex, fileLen);
                  int lineLen = (int) Math.min(Integer.MAX_VALUE, lineByteLen);
                  if (view.fileManager.shouldStreamLineLength(lineLen)) {
                    int sliceStart = 0;
                    int sliceEnd =
                        Math.max(1, Math.min(lineLen, view.getInitialStreamedSliceSize()));
                    if (view.fileManager.isSingleByteCharset()) {
                      String slice =
                          view.readLineSliceAtByte(raf, lineStart, lineByteLen, sliceStart, sliceEnd);
                      newWin.add(slice);
                      newStreamedLengths.put(lineIndex, lineLen);
                      newStreamedSliceStarts.put(lineIndex, sliceStart);
                    } else {
                      sliceEnd = Math.max(1, view.getInitialStreamedSliceSize());
                      SodiumEditorView.StreamedCharSlice slice =
                          view.readLineSliceByChars(raf, lineStart, sliceStart, sliceEnd, true);
                      newWin.add(slice.text);
                      newStreamedLengths.put(lineIndex, slice.length);
                      newStreamedSliceStarts.put(lineIndex, sliceStart);
                    }
                  } else {
                    String ln = view.readLineUtf8AtByte(raf, lineStart);
                    newWin.add(ln);
                  }
                  lineIndex++;
                }
                if (fileEndsWithNewline) {
                  synchronized (view.fileManager.lineOffsetsLock) {
                    trailingEmptyFromIndex =
                        view.fileManager.getLineOffsets().length > 0 && view.fileManager.getLineOffsets()[view.fileManager.getLineOffsets().length - 1] == fileLen;
                  }
                }
              }
            } else {
              try (java.io.RandomAccessFile raf = new java.io.RandomAccessFile(view.fileManager.getSourceFile(), "r")) {
                long fileLen = raf.length();
                if (fileLen > 0) {
                  raf.seek(fileLen - 1);
                  fileEndsWithNewline = (raf.read() == '\n');
                }
                raf.seek(0);
                int skipped = 0;
                while (skipped < actualStart) {
                  SodiumEditorView.LineScanResult scan = view.scanLineLength(raf);
                  if (scan.reachedEof) break;
                  skipped++;
                }
                actualStart = skipped;

                int limit = view.windowSize + (view.prefetchLines * 2);
                int lineIndex = actualStart;
                while (newWin.size() < limit) {
                  long lineStart = raf.getFilePointer();
                  if (lineStart >= fileLen) {
                    reachedEof = true;
                    break;
                  }
                  SodiumEditorView.LineScanResult scan = view.scanLineLength(raf);
                  long afterPos = raf.getFilePointer();
                  long lineByteLen = scan.length;
                  int lineLen = (int) Math.min(Integer.MAX_VALUE, lineByteLen);
                  if (view.fileManager.shouldStreamLineLength(lineLen)) {
                    int sliceStart = 0;
                    int sliceEnd =
                        Math.max(1, Math.min(lineLen, view.getInitialStreamedSliceSize()));
                    if (view.fileManager.isSingleByteCharset()) {
                      String slice =
                          view.fileManager.readLineSliceAtByte(raf, lineStart, lineByteLen, sliceStart, sliceEnd);
                      newWin.add(slice);
                      newStreamedLengths.put(lineIndex, lineLen);
                      newStreamedSliceStarts.put(lineIndex, sliceStart);
                    } else {
                      sliceEnd = Math.max(1, view.getInitialStreamedSliceSize());
                      SodiumEditorView.StreamedCharSlice slice =
                          view.fileManager.readLineSliceByChars(raf, lineStart, sliceStart, sliceEnd, true);
                      newWin.add(slice.text);
                      newStreamedLengths.put(lineIndex, slice.length);
                      newStreamedSliceStarts.put(lineIndex, sliceStart);
                    }
                  } else {
                    raf.seek(lineStart);
                    byte[] buf = new byte[lineLen];
                    if (lineLen > 0) raf.readFully(buf);
                    String ln =
                        (lineLen > 0)
                            ? (view.binarySafeRenderingEnabled
                                ? view.bytesToControlVisible(buf, buf.length)
                                : new String(buf, view.fileManager.fileCharset))
                            : "";
                    newWin.add(ln);
                  }
                  raf.seek(afterPos);
                  if (scan.reachedEof) {
                    reachedEof = true;
                    break;
                  }
                  lineIndex++;
                }
              } catch (Exception ignored) {
              }
            }

            if (newWin.isEmpty()) {
              newWin.add("");
              actualStart = 0;
            }
            if (reachedEof && fileEndsWithNewline && !trailingEmptyFromIndex) {
              newWin.add("");
            }

            boolean eof = newWin.size() < view.windowSize + (view.prefetchLines * 2);

            synchronized (view.modifiedLines) {
              for (int i = 0; i < newWin.size(); i++) {
                int globalLineNum = actualStart + i;
                if (view.modifiedLines.containsKey(globalLineNum)) {
                  String modifiedLine = view.modifiedLines.get(globalLineNum);
                  if (modifiedLine != null) newWin.set(i, modifiedLine);
                  newStreamedLengths.delete(globalLineNum);
                  newStreamedSliceStarts.delete(globalLineNum);
                }
              }
            }

            if (taskVersion != view.ioTaskVersion.get()) {
              view.post(
                  () -> {
                    view.isWindowLoading = false;
                    checkAndLoadWindow();
                  });
              return;
            }

            final int finalStart = actualStart;
            final android.util.SparseIntArray finalStreamedLengths = newStreamedLengths;
            final android.util.SparseIntArray finalStreamedSliceStarts = newStreamedSliceStarts;
            view.post(
                () -> {
                  view.isWindowLoading = false;
                  if (taskVersion != view.ioTaskVersion.get()) {
                    checkAndLoadWindow();
                    return;
                  }
                  synchronized (view.linesWindow) {
                    view.linesWindow.clear();
                    view.linesWindow.addAll(newWin);
                    view.windowStartLine = finalStart;
                    view.isEof = eof;
                  }
                  synchronized (view.streamedLinesLock) {
                    view.streamedLineLengths.clear();
                    view.streamedLineSliceStarts.clear();
                    for (int i = 0; i < finalStreamedLengths.size(); i++) {
                      int key = finalStreamedLengths.keyAt(i);
                      view.streamedLineLengths.put(key, finalStreamedLengths.valueAt(i));
                      view.streamedLineSliceStarts.put(
                          key, finalStreamedSliceStarts.get(key, 0));
                    }
                  }
                  view.lineNumberManager.invalidateCache();
                  view.invalidateHighlightEnsureRange();
                  view.bracketGuideManager.invalidateCache();
                  if (recalculateWidthSync) {
                    view.recalculateMaxLineWidth();
                  } else {
                    synchronized (view.lineWidthCache) {
                      view.lineWidthCache.clear();
                    }
                    view.currentMaxWindowLineWidth = 0f;
                    view.globalMaxLineWidth = 0f;
                    view.recalculateMaxLineWidthAsync();
                  }
                  if (view.wordWrapManager.isWordWrapEnabled) {
                    if (view.wordWrapManager.shouldSuppressWrapMetricsForFastSelectAll(view)) {
                      view.wordWrapManager.wrapMetricsReady = false;
                    } else {
                      if (!view.wordWrapManager.wrapMetricsReady || view.wordWrapManager.wrapLineCounts == null || view.wordWrapManager.wrapLinePrefix == null) {
                        if (view.getWidth() > 0) {
                          view.wordWrapManager.buildWrapMetricsForWindowSnapshot(view);
                        }
                      }
                      view.wordWrapManager.scheduleWrapMetricsSnapshotIfNeeded(view, Math.max(1, Math.round(view.wordWrapManager.getWrapWidth(view))));
                      view.wordWrapManager.requestWrapPrefixRebuild(view);
                    }
                  }
                  view.invalidate();
                  if (onComplete != null) onComplete.run();
                });
          } catch (Exception e) {
            e.printStackTrace();
            view.post(
                () -> {
                  view.isWindowLoading = false;
                  if (onComplete != null) onComplete.run();
                });
          }
        });
  }

  public void maybeUpdateStreamedSlicesForVisibleRange(int firstVisibleLine, int lastVisibleLine) {
    if (view.wordWrapManager.isWordWrapEnabled) return;
    if (!view.fileManager.isIndexReady() || view.fileManager.getSourceFile() == null || !view.fileManager.getSourceFile().exists()) return;
    if (view.isWindowLoading) return;

    java.util.ArrayList<StreamedSliceRequest> requests = new java.util.ArrayList<>();
    synchronized (view.linesWindow) {
      int winStart = view.windowStartLine;
      int winEnd = view.windowStartLine + view.linesWindow.size() - 1;
      int start = Math.max(firstVisibleLine, winStart);
      int end = Math.min(lastVisibleLine, winEnd);
      if (start > end) return;
      for (int line = start; line <= end; line++) {
        if (view.modifiedLines.containsKey(line)) continue;
        int len = view.fileManager.getStreamedLineLength(line);
        if (len <= 0) continue;
        String slice = view.linesWindow.get(line - winStart);
        int sliceStart = view.fileManager.getStreamedLineSliceStart(line);
        int sliceEnd = sliceStart + ((slice == null) ? 0 : slice.length());
        view.computeStreamedSliceBounds(slice, line, len, view.streamedSliceTmp);
        int desiredStart = view.streamedSliceTmp[0];
        int desiredEnd = view.streamedSliceTmp[1];
        if (sliceStart <= desiredStart && sliceEnd >= desiredEnd) continue;
        requests.add(new StreamedSliceRequest(line, desiredStart, desiredEnd));
      }
    }

    if (requests.isEmpty()) return;
    if (view.streamedSliceUpdatePending) return;
    view.streamedSliceUpdatePending = true;
    final int token = ++view.streamedSliceUpdateToken;
    final int taskVersion = view.ioTaskVersion.get();

    view.ioHandler.post(
        () -> {
          if (token != view.streamedSliceUpdateToken) return;
          if (taskVersion != view.ioTaskVersion.get()) return;
          if (view.fileManager.getSourceFile() == null || !view.fileManager.getSourceFile().exists()) {
            view.post(() -> view.fileManager.streamedSliceUpdatePending = false);
            return;
          }
          java.util.LinkedHashMap<Integer, String> results = new java.util.LinkedHashMap<>();
          android.util.SparseIntArray starts = new android.util.SparseIntArray();
          try (java.io.RandomAccessFile raf = new java.io.RandomAccessFile(view.fileManager.getSourceFile(), "r")) {
            long fileLen = raf.length();
            for (StreamedSliceRequest req : requests) {
              long lineStart;
              synchronized (view.lineOffsetsLock) {
                if (req.line < 0 || req.line >= view.lineOffsets.length) continue;
                lineStart = view.lineOffsets[req.line];
              }
              if (view.fileManager.isSingleByteCharset()) {
                long lineByteLen = view.getLineByteLengthFromIndex(raf, req.line, fileLen);
                String slice =
                    view.readLineSliceAtByte(raf, lineStart, lineByteLen, req.start, req.end);
                results.put(req.line, slice);
                starts.put(req.line, req.start);
              } else {
                SodiumEditorView.StreamedCharSlice slice =
                    view.readLineSliceByChars(raf, lineStart, req.start, req.end, false);
                results.put(req.line, slice.text);
                starts.put(req.line, req.start);
              }
            }
          } catch (Exception ignored) {
          }

          view.post(
              () -> {
                if (token != view.streamedSliceUpdateToken) {
                  view.streamedSliceUpdatePending = false;
                  return;
                }
                if (taskVersion != view.ioTaskVersion.get()) {
                  view.streamedSliceUpdatePending = false;
                  return;
                }
                synchronized (view.linesWindow) {
                  int winStart = view.windowStartLine;
                  int winEnd = view.windowStartLine + view.linesWindow.size() - 1;
                  for (java.util.Map.Entry<Integer, String> e : results.entrySet()) {
                    int line = e.getKey();
                    if (line < winStart || line > winEnd) continue;
                    if (view.modifiedLines.containsKey(line)) continue;
                    int local = line - winStart;
                    if (local < 0 || local >= view.linesWindow.size()) continue;
                    view.linesWindow.set(local, (e.getValue() == null) ? "" : e.getValue());
                    int len = view.fileManager.getStreamedLineLength(line);
                    if (len > 0) {
                      view.fileManager.setStreamedLineInfo(line, len, starts.get(line));
                    }
                  }
                }
                view.streamedSliceUpdatePending = false;
                view.invalidate();
              });
        });
  }
  
  private int getBraceGuideColumnForLine(
      String line, int globalLine, int braceIndex, int firstNonSpace) {
    int column = (firstNonSpace >= 0) ? firstNonSpace : braceIndex;
    if (firstNonSpace >= 0 && braceIndex > firstNonSpace) {
      char first = line.charAt(firstNonSpace);
      if (first == ')' || first == ']') {
        int prevIndent = getPreviousNonEmptyIndentColumn(globalLine - 1);
        if (prevIndent >= 0) {
          column = prevIndent;
        }
      }
    }
    return column;
  }

  private int getPreviousNonEmptyIndentColumn(int line) {
    for (int l = line; l >= 0; l--) {
      String prev = textRender.getLineTextForRender(l);
      if (prev == null) continue;
      int idx = getFirstNonSpaceIndex(prev);
      if (idx >= 0) return idx;
    }
    return -1;
  }
  
  public long[] buildIndexJava(String path) {
    if (path == null) return null;
    java.io.File file = new java.io.File(path);
    if (!file.exists()) return null;
    java.util.ArrayList<Long> offsets = new java.util.ArrayList<>();
    offsets.add(0L);
    try (java.io.RandomAccessFile raf = new java.io.RandomAccessFile(file, "r")) {
      long pos = 0L;
      byte[] buf = new byte[64 * 1024];
      int read;
      while ((read = raf.read(buf)) != -1) {
        for (int i = 0; i < read; i++) {
          if (buf[i] == '\n') {
            offsets.add(pos + i + 1);
          }
        }
        pos += read;
      }
    } catch (Exception ignored) {
      return null;
    }
    long[] out = new long[offsets.size()];
    for (int i = 0; i < offsets.size(); i++) out[i] = offsets.get(i);
    return out;
  }

  private void clearSelectionStateAfterDelete() {
    view.selectionManager.clearSelection();
    view.popupMenuManager.hidePopup();
    view.cursorAnimationManager.resetCursorBlink();
  }

  private void applyMultiLineReplaceInWindowNow(
      int sL, int sC, int eL, int eC, String insertText, SodiumEditorView.CursorTarget target) {
    synchronized (view.linesWindow) {
      int oldLineCount = textRender.getLinesCount();
      int sLocal = sL - view.windowStartLine;
      int eLocal = eL - view.windowStartLine;
      if (sLocal < 0 || eLocal < 0 || sLocal >= view.linesWindow.size() || eLocal >= view.linesWindow.size())
        return;
      if (sLocal > eLocal) {
        int t = sLocal;
        sLocal = eLocal;
        eLocal = t;
      }

      String startLine = textRender.getLineFromWindowLocal(sLocal);
      String endLine = textRender.getLineFromWindowLocal(eLocal);
      if (startLine == null) startLine = "";
      if (endLine == null) endLine = "";

      int startIdx = Math.max(0, Math.min(sC, startLine.length()));
      int endIdx = Math.max(0, Math.min(eC, endLine.length()));

      String left = startLine.substring(0, startIdx);
      String right = endLine.substring(endIdx);

      String mergedText = left + (insertText == null ? "" : insertText) + right;
      String[] parts = mergedText.split("\n", -1);

      view.linesWindow.set(sLocal, parts[0]);
      if (eLocal >= sLocal + 1) {
        view.linesWindow.subList(sLocal + 1, eLocal + 1).clear();
      }

      if (parts.length > 1) {
        List<String> toInsert = new ArrayList<>(parts.length - 1);
        for (int i = 1; i < parts.length; i++) toInsert.add(parts[i]);
        view.linesWindow.addAll(sLocal + 1, toInsert);
      }

      view.cursorManager.setLineAndChar(Math.max(0, target.line), Math.max(0, target.ch));

      int newLineCount = textRender.getLinesCount();
      if (oldLineCount != newLineCount) {
        view.wordWrapManager.onLineCountChanged(view);
      }
      view.recalculateMaxLineWidth();
    }
  }

  private void applyMultiLineDeleteInWindowNow(int sL, int sC, int eL, int eC) {
    synchronized (view.linesWindow) {
      int oldLineCount = textRender.getLinesCount();
      int sLocal = sL - view.windowStartLine;
      int eLocal = eL - view.windowStartLine;
      if (sLocal < 0 || eLocal >= view.linesWindow.size() || sLocal > eLocal) return;

      String startLine = textRender.getLineFromWindowLocal(sLocal);
      String endLine = textRender.getLineFromWindowLocal(eLocal);
      if (startLine == null) startLine = "";
      if (endLine == null) endLine = "";

      int startIdx = Math.max(0, Math.min(sC, startLine.length()));
      int endIdx = Math.max(0, Math.min(eC, endLine.length()));

      String left = startLine.substring(0, startIdx);
      String right = endLine.substring(endIdx);

      String merged = left + right;

      view.linesWindow.set(sLocal, merged);
      if (eLocal > sLocal) {
        view.linesWindow.subList(sLocal + 1, eLocal + 1).clear();
      }

      view.modifiedLines.put(view.windowStartLine + sLocal, merged);
      for (int i = sLocal + 1; i < view.linesWindow.size(); i++) {
        view.modifiedLines.put(view.windowStartLine + i, view.linesWindow.get(i));
      }

      view.cursorManager.setLineAndChar(sL, left.length());

      view.recalculateMaxLineWidth();
      int newLineCount = textRender.getLinesCount();
      if (oldLineCount != newLineCount) {
        view.wordWrapManager.onLineCountChanged(view);
      }
    }
  }

  private static int getFirstNonSpaceIndex(String line) {
    for (int i = 0; i < line.length(); i++) {
      if (!Character.isWhitespace(line.charAt(i))) return i;
    }
    return -1;
  }
}
