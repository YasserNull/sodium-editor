package com.yn.sodiumeditor.renderer;

import android.util.SparseIntArray;
import androidx.annotation.Nullable;
import com.yn.sodiumeditor.SodiumEditor;
import com.yn.sodiumeditor.core.StreamedCharSlice;
import com.yn.sodiumeditor.core.StreamedSliceRequest;
import com.yn.sodiumeditor.io.EditOp;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class WindowRender {

  private final SodiumEditor editor;

  // Window/Line Management variables
  public final List<String> linesWindow = new ArrayList<>();
  public int windowStartLine = 0;
  public int windowSize = 250;
  public int prefetchLines = 250;
  public final java.util.LinkedHashMap<Integer, String> modifiedLines =
      new java.util.LinkedHashMap<Integer, String>(1000, 0.75f, false);
  private int firstModifiedLine = Integer.MAX_VALUE;
  public final android.util.SparseArray<Float> lineWidthCache = new android.util.SparseArray<>(400);
  public int lineWidthCacheSize = 250;
  public float currentMaxWindowLineWidth = 0f;
  public float globalMaxLineWidth = 0f;
  public final android.util.SparseArray<Float> avgCharWidthCache =
      new android.util.SparseArray<>(400);

  public final Object streamedLinesLock = new Object();
  public final SparseIntArray streamedLineLengths = new SparseIntArray();
  public final SparseIntArray streamedLineSliceStarts = new SparseIntArray();
  public boolean streamedSliceUpdatePending = false;
  public int streamedSliceUpdateToken = 0;
  public final int[] streamedSliceTmp = new int[2];
  public final Object streamedLinesLockLinesLock = new Object();
  public final SparseIntArray streamedLinesLockLineLengths = new SparseIntArray();
  public final SparseIntArray streamedLinesLockLineSliceStarts = new SparseIntArray();
  public boolean streamedLinesLockSliceUpdatePending = false;
  public int streamedLinesLockSliceUpdateToken = 0;
  public final java.util.HashMap<Integer, String> directLinesTmp = new java.util.HashMap<>();

  public WindowRender(SodiumEditor editor) {
    this.editor = editor;
  }

  // ========================================================================
  // Line Text Access Methods
  // ========================================================================

  /** Get line text for render with direct lines support */
  public String getLineTextForRenderWithDirect(
      int line, @Nullable java.util.Map<Integer, String> direct) {
    if (line < 0) return "";

    // Modified lines first — always the most recent edits
    String mod = getModifiedLine(line);
    if (mod != null) return mod;

    // Then the window
    if (line >= windowStartLine && line < windowStartLine + linesWindow.size()) {
      String text =
          getLineFromWindowLocal(
              line
                  - windowStartLine); // Corrected this.editor.windowRender.getLineFromWindowLocal
                                      // to local call
      return (text != null) ? text : "";
    }

    // Direct batch (during fast fling). Modified lines were
    // checked above, so disk-backed lines are safe for unchanged positions.
    boolean canUseFileLine = canUseFileBackedLineForRender(line);
    if (direct != null && canUseFileLine) {
      String d = direct.get(line);
      if (d != null) return d;
    }

    // Cache
    if (canUseFileLine) {
      String c = editor.fileIO.directLineCache.get(line);
      if (c != null) return c;
    }

    return "";
  }

  public boolean canUseFileBackedLineForRender(int line) {
    if (line < 0 || !editor.fileIO.isIndexReady) return false;
    if (editor.editOperators.lineCountDelta == 0) return true;
    int firstModifiedLine = getFirstModifiedLine();
    return firstModifiedLine != Integer.MAX_VALUE && line < firstModifiedLine;
  }

  public int getFirstModifiedLine() {
    synchronized (modifiedLines) {
      if (firstModifiedLine != Integer.MAX_VALUE && !modifiedLines.containsKey(firstModifiedLine)) {
        recomputeFirstModifiedLineLocked();
      }
      return firstModifiedLine;
    }
  }

  public boolean hasModifiedLine(int line) {
    synchronized (modifiedLines) {
      return modifiedLines.containsKey(line);
    }
  }

  public boolean hasAnyModifiedLines() {
    synchronized (modifiedLines) {
      return !modifiedLines.isEmpty();
    }
  }

  public String getModifiedLine(int line) {
    synchronized (modifiedLines) {
      return modifiedLines.get(line);
    }
  }

  public void putModifiedLine(int line, String text) {
    synchronized (modifiedLines) {
      modifiedLines.put(line, text);
      if (line < firstModifiedLine) firstModifiedLine = line;
    }
  }

  public void clearModifiedLines() {
    synchronized (modifiedLines) {
      modifiedLines.clear();
      firstModifiedLine = Integer.MAX_VALUE;
    }
  }

  public void removeModifiedLine(int line) {
    synchronized (modifiedLines) {
      if (modifiedLines.remove(line) != null) {
        onModifiedLineRemovedLocked(line);
      }
    }
  }

  public void recomputeFirstModifiedLine() {
    synchronized (modifiedLines) {
      recomputeFirstModifiedLineLocked();
    }
  }

  public void recomputeFirstModifiedLineLocked() {
    int first = Integer.MAX_VALUE;
    for (Integer line : modifiedLines.keySet()) {
      if (line != null && line < first) first = line;
    }
    firstModifiedLine = first;
  }

  private void onModifiedLineRemovedLocked(int line) {
    if (line == firstModifiedLine) {
      recomputeFirstModifiedLineLocked();
    }
  }

  /** Get line text for render (render-safe, no file random read) */
  public String getLineTextForRender(int line) {
    if (line < 0) return "";

    // Modified lines first — always the most recent edits
    String mod = getModifiedLine(line);
    if (mod != null) return mod;

    // Then the window
    if (line >= windowStartLine && line < windowStartLine + linesWindow.size()) {
      String text =
          getLineFromWindowLocal(
              line
                  - windowStartLine); // Corrected this.editor.windowRender.getLineFromWindowLocal
                                      // to local call
      return (text != null) ? text : "";
    }

    return "";
  }

  public void maybeUpdateStreamedSlicesForVisibleRange(int firstVisibleLine, int lastVisibleLine) {
    if (editor.wordWrap.isWordWrapEnabled) return;
    if (!editor.fileIO.isIndexReady
        || editor.fileIO.sourceFile == null
        || !editor.fileIO.sourceFile.exists()) return;
    if (editor.fileIO.isWindowLoading) return;

    ArrayList<StreamedSliceRequest> requests = new ArrayList<>();
    synchronized (linesWindow) {
      int winStart = windowStartLine;
      int winEnd = windowStartLine + linesWindow.size() - 1;
      int start = Math.max(firstVisibleLine, winStart);
      int end = Math.min(lastVisibleLine, winEnd);
      if (start > end) return;
      for (int line = start; line <= end; line++) {
        if (hasModifiedLine(line)) continue;
        int len = getStreamedLineLength(line);
        if (len <= 0) continue;
        String slice = linesWindow.get(line - winStart);
        int sliceStart = getStreamedLineSliceStart(line);
        int sliceEnd = sliceStart + ((slice == null) ? 0 : slice.length());
        editor.textRange.computeStreamedSliceBounds(
            slice, line, len, streamedSliceTmp, false); // Delegated to textRange
        int desiredStart = streamedSliceTmp[0];
        int desiredEnd = streamedSliceTmp[1];
        if (sliceStart <= desiredStart && sliceEnd >= desiredEnd) continue;
        requests.add(new StreamedSliceRequest(line, desiredStart, desiredEnd));
      }
    }

    if (requests.isEmpty()) return;

    if (streamedLinesLockSliceUpdatePending) return;
    streamedLinesLockSliceUpdatePending = true;
    final int token = ++streamedLinesLockSliceUpdateToken;
    final int taskVersion = editor.fileIO.ioTaskVersion.get();

    editor.fileIO.ioHandler.post(
        () -> {
          if (token != streamedLinesLockSliceUpdateToken) return;
          if (taskVersion != editor.fileIO.ioTaskVersion.get()) return;
          if (editor.fileIO.sourceFile == null || !editor.fileIO.sourceFile.exists()) {
            editor.post(() -> streamedLinesLockSliceUpdatePending = false);
            return;
          }
          LinkedHashMap<Integer, String> results = new LinkedHashMap<>();
          SparseIntArray starts = new SparseIntArray();
          try (java.io.RandomAccessFile raf =
              new java.io.RandomAccessFile(editor.fileIO.sourceFile, "r")) {
            long fileLen = raf.length();
            for (StreamedSliceRequest req : requests) {
              long lineStart;
              synchronized (editor.fileIO.lineOffsetsLock) {
                if (req.line < 0 || req.line >= editor.fileIO.lineOffsets.length) continue;
                lineStart = editor.fileIO.lineOffsets[req.line];
              }
              if (isSingleByteCharset()) {
                long lineByteLen = editor.fileIO.getLineByteLengthFromIndex(raf, req.line, fileLen);
                String slice =
                    editor.fileIO.readLineSliceAtByte(
                        raf, lineStart, lineByteLen, req.start, req.end);
                results.put(req.line, slice);
                starts.put(req.line, req.start);
              } else {
                StreamedCharSlice slice =
                    editor.fileIO.readLineSliceByChars(raf, lineStart, req.start, req.end, false);
                results.put(req.line, slice.text);
                starts.put(req.line, req.start);
              }
            }
          } catch (Exception ignored) {
          }

          editor.post(
              () -> {
                if (token != streamedLinesLockSliceUpdateToken) {
                  streamedLinesLockSliceUpdatePending = false;
                  return;
                }
                if (taskVersion != editor.fileIO.ioTaskVersion.get()) {
                  streamedLinesLockSliceUpdatePending = false;
                  return;
                }
                synchronized (linesWindow) {
                  int winStart = windowStartLine;
                  int winEnd = windowStartLine + linesWindow.size() - 1;
                  for (Map.Entry<Integer, String> e : results.entrySet()) {
                    int line = e.getKey();
                    if (line < winStart || line > winEnd) continue;
                    if (hasModifiedLine(line)) continue;
                    int local = line - winStart;
                    if (local < 0 || local >= linesWindow.size()) continue;
                    linesWindow.set(local, (e.getValue() == null) ? "" : e.getValue());
                    int len = getStreamedLineLength(line);
                    if (len > 0) {
                      setStreamedLineInfo(line, len, starts.get(line));
                    }
                  }
                }
                streamedLinesLockSliceUpdatePending = false;

                editor.invalidate();
              });
        });
  }

  public int getStreamLineThreshold() {
    return Math.max(4096, editor.highlightRender.maxSyntaxLineLength);
  }

  public boolean shouldStreamLineLength(int length) {
    if (editor.wordWrap.isWordWrapEnabled) return false;
    if (editor.binaryRender.isBinarySafeRenderingEnabled()) {
      return length
          > Math.max(
              256, editor.textRange.getInitialStreamedSliceSize()); // Corrected to editor.textRange
    }
    if (!editor.fileIO.isIndexReady) return false;
    return length > getStreamLineThreshold();
  }

  public int getStreamedLineLength(int globalLine) {
    synchronized (streamedLinesLockLinesLock) {
      int v = streamedLinesLockLineLengths.get(globalLine, -1);
      if (v >= 0) return v;
    }
    synchronized (streamedLinesLock) {
      return streamedLineLengths.get(globalLine, -1);
    }
  }

  public int getStreamedLineSliceStart(int globalLine) {
    synchronized (streamedLinesLockLinesLock) {
      int v = streamedLinesLockLineSliceStarts.get(globalLine, 0);
      if (v != 0) return v;
    }
    synchronized (streamedLinesLock) {
      return streamedLineSliceStarts.get(globalLine, 0);
    }
  }

  public void setStreamedLineInfo(int globalLine, int length, int sliceStart) {
    synchronized (streamedLinesLockLinesLock) {
      streamedLinesLockLineLengths.put(globalLine, length);
      streamedLinesLockLineSliceStarts.put(globalLine, sliceStart);
    }
  }

  public void clearStreamedLineInfo(int globalLine) {
    synchronized (streamedLinesLockLinesLock) {
      streamedLinesLockLineLengths.delete(globalLine);
      streamedLinesLockLineSliceStarts.delete(globalLine);
    }
  }

  public void clearStreamedLineCaches() {
    synchronized (streamedLinesLockLinesLock) {
      streamedLinesLockLineLengths.clear();
      streamedLinesLockLineSliceStarts.clear();
    }
    synchronized (streamedLinesLock) {
      streamedLineLengths.clear();
      streamedLineSliceStarts.clear();
    }
    streamedLinesLockSliceUpdatePending = false;
    streamedLinesLockSliceUpdateToken++;
  }

  public boolean isSingleByteCharset() {
    try {
      if (editor.binaryRender.isBinarySafeRenderingEnabled()) return true;
      return editor.fileIO.fileCharset.newEncoder().maxBytesPerChar() <= 1.01f;
    } catch (Exception ignored) {
      return true;
    }
  }

  // ========================================================================
  // Window Management Methods
  // ========================================================================

  public int getWindowEndLine() {
    synchronized (linesWindow) {
      return Math.max(0, windowStartLine + linesWindow.size() - 1);
    }
  }

  public String getLineFromWindowLocal(int localIdx) {
    if (localIdx < 0 || localIdx >= linesWindow.size()) return null;
    int globalLine = windowStartLine + localIdx;
    synchronized (modifiedLines) {
      String mod = modifiedLines.get(globalLine);
      if (mod != null) return mod;
    }
    return linesWindow.get(localIdx);
  }

  public void maybeKickWindowLoad(int firstVisibleLine) {
    if (editor.zoom.isZoomGestureActive()) return;
    if (editor.fileIO.sourceFile == null || editor.fileIO.isFileCleared) return;
    if (editor.fileIO.isWindowLoading) return;

    if (editor.getWidth() == 0 || editor.getHeight() == 0) return;
    int firstVisibleIndex =
        Math.max(
            0,
            (int)
                (editor.scroll.scrollY
                    / editor.textRender.lineHeight)); // Use editor.textRender.lineHeight
    int lastVisibleIndex =
        firstVisibleIndex
            + (int)
                Math.ceil(
                    editor.getHeight()
                        / editor.textRender.lineHeight); // Use editor.textRender.lineHeight
    int firstVisibleGlobal;
    int lastVisibleGlobal;
    if (editor.wordWrap.isWordWrapEnabled) {
      firstVisibleGlobal = editor.wordWrap.getVisualPositionForIndex(firstVisibleIndex).line;
      lastVisibleGlobal = editor.wordWrap.getVisualPositionForIndex(lastVisibleIndex).line;
    } else {
      firstVisibleGlobal = firstVisibleIndex;
      lastVisibleGlobal = lastVisibleIndex;
    }
    firstVisibleGlobal = Math.max(0, firstVisibleGlobal);
    lastVisibleGlobal = Math.max(firstVisibleGlobal, lastVisibleGlobal);

    int winStart = windowStartLine;
    int winEnd = winStart + linesWindow.size() - 1;
    int buffer = Math.max(0, prefetchLines / 2);
    boolean outside = firstVisibleGlobal < winStart || firstVisibleGlobal > winEnd;
    boolean nearTop = winStart > 0 && firstVisibleGlobal < winStart + buffer;
    boolean nearBottom = !editor.fileIO.isEof && lastVisibleGlobal > winEnd - buffer;
    if (outside || nearTop || nearBottom) {
      int targetStart = Math.max(0, firstVisibleGlobal - prefetchLines);
      editor.fileIO.loadWindowAround(targetStart, null, false);
    }
  }

  public void recalculateMaxLineWidth() {
    float maxW = 0f;
    synchronized (linesWindow) {
      for (int i = 0; i < linesWindow.size(); i++) {
        int globalLine = windowStartLine + i;
        String line = linesWindow.get(i);
        float w = editor.view.getWidthForLine(globalLine, line);
        if (w > maxW) maxW = w;
      }
    }
    currentMaxWindowLineWidth = maxW;
    globalMaxLineWidth = maxW;
    editor.scroll.maxLineWidthForScroll = maxW;
    editor.scroll.maxTextStartXForScroll = 0f;
    editor.scroll.maxScrollXForScroll = 0f;
  }

  public void applyMultiLineReplaceInWindowNow(
      int sL, int sC, int eL, int eC, String insertText, EditOp.CursorTarget target) {
    synchronized (linesWindow) {
      int oldLineCount = editor.view.getLinesCount();
      int sLocal = sL - windowStartLine;
      int eLocal = eL - windowStartLine;
      if (sLocal < 0 || eLocal < 0 || sLocal >= linesWindow.size() || eLocal >= linesWindow.size())
        return;
      if (sLocal > eLocal) {
        int t = sLocal;
        sLocal = eLocal;
        eLocal = t;
      }

      String startLine = linesWindow.get(sLocal);
      String endLine = linesWindow.get(eLocal);
      if (startLine == null) startLine = "";
      if (endLine == null) endLine = "";

      int startIdx = Math.max(0, Math.min(sC, startLine.length()));
      int endIdx = Math.max(0, Math.min(eC, endLine.length()));

      String left = startLine.substring(0, startIdx);
      String right = endLine.substring(endIdx);

      String mergedText = left + (insertText == null ? "" : insertText) + right;
      String[] parts = mergedText.split("\n", -1);

      int oldRangeLineCount = eL - sL + 1;
      int newRangeLineCount = parts.length;
      int delta = newRangeLineCount - oldRangeLineCount;
      int expectedNewLineCount = Math.max(1, oldLineCount + delta);

      if (delta != 0) {
        editor.editOperators.shifter.shiftModifiedLines(sL + 1, delta);
      }

      linesWindow.set(sLocal, parts[0]);
      if (eLocal >= sLocal + 1) {
        linesWindow.subList(sLocal + 1, eLocal + 1).clear();
      }

      if (parts.length > 1) {
        List<String> toInsert = new ArrayList<>(parts.length - 1);
        for (int i = 1; i < parts.length; i++) toInsert.add(parts[i]);
        linesWindow.addAll(sLocal + 1, toInsert);
      }

      for (int i = 0; i < parts.length; i++) {
        putModifiedLine(sL + i, parts[i]);
      }

      editor.cursor.cursorLine = Math.max(0, target.line);
      editor.cursor.cursorChar = Math.max(0, target.ch);

      int newLineCount = expectedNewLineCount;
      if (editor.lineNumber.showLineNumbers
          && oldLineCount > 0
          && String.valueOf(oldLineCount).length() != String.valueOf(newLineCount).length()) {
        editor.lineNumber.updateGutterWidth();
        editor.requestLayout();
      }
      if (delta != 0) {
        editor.wordWrap.onLineCountChanged();
      }
      editor.lineNumber.invalidateLineNumberCache();

      recalculateMaxLineWidth(); // Corrected this.editor.windowRender.recalculateMaxLineWidth to
                                 // local call
    }
  }

  public void applyMultiLineDeleteInWindowNow(int sL, int sC, int eL, int eC) {
    synchronized (linesWindow) {
      int oldLineCount = editor.view.getLinesCount();
      int sLocal = sL - windowStartLine;
      int eLocal = eL - windowStartLine;
      if (sLocal < 0 || eLocal >= linesWindow.size() || sLocal > eLocal) return;

      String startLine = linesWindow.get(sLocal);
      String endLine = linesWindow.get(eLocal);
      if (startLine == null) startLine = "";
      if (endLine == null) endLine = "";

      int startIdx = Math.max(0, Math.min(sC, startLine.length()));
      int endIdx = Math.max(0, Math.min(eC, endLine.length()));

      String left = startLine.substring(0, startIdx);
      String right = endLine.substring(endIdx);

      String merged = left + right;
      int delta = sL - eL;
      int expectedNewLineCount = Math.max(1, oldLineCount + delta);

      linesWindow.set(sLocal, merged);
      if (eLocal > sLocal) {
        linesWindow.subList(sLocal + 1, eLocal + 1).clear();
      }

      editor.editOperators.shifter.shiftModifiedLines(sL + 1, sL - eL);
      putModifiedLine(sL, merged);

      editor.cursor.cursorLine = sL;
      editor.cursor.cursorChar = left.length();

      recalculateMaxLineWidth(); // Corrected this.editor.windowRender.recalculateMaxLineWidth to
                                 // local call
      if (editor.lineNumber.showLineNumbers
          && oldLineCount > 0
          && String.valueOf(oldLineCount).length()
              != String.valueOf(expectedNewLineCount).length()) {
        editor.lineNumber.updateGutterWidth();
        editor.requestLayout();
      }
      if (delta != 0) {
        editor.wordWrap.onLineCountChanged();
      }
      editor.lineNumber.invalidateLineNumberCache();
    }
  }

  public void setWindowSize(int size) {
    int safe = Math.max(10, size);
    int minWindow = computeMinWindowSize();
    if (safe < minWindow) safe = minWindow;
    if (windowSize == safe) return;
    windowSize = safe;
    editor.highlight.invalidateHighlightEnsureRange();
    editor.bracketGuides.invalidateBracketGuideCache();
    if (editor.wordWrap.isWordWrapEnabled) editor.wordWrap.invalidateWrapMetrics(true);
    editor.wordWrap.requestWrapPrefixRebuild();
    reloadWindowAroundVisible(false);
  }

  public void setPrefetchLines(int lines) {
    int safe = Math.max(0, lines);
    if (prefetchLines == safe) return;
    prefetchLines = safe;
    int minWindow = computeMinWindowSize();
    if (windowSize < minWindow) windowSize = minWindow;
    editor.highlight.invalidateHighlightEnsureRange();
    editor.bracketGuides.invalidateBracketGuideCache();
    if (editor.wordWrap.isWordWrapEnabled) editor.wordWrap.invalidateWrapMetrics(true);
    editor.wordWrap.requestWrapPrefixRebuild();
    reloadWindowAroundVisible(false);
  }

  public void setLineWidthCacheSize(int size) {
    int safe = Math.max(10, size);
    if (lineWidthCacheSize == safe) return;
    lineWidthCacheSize = safe;
    if (lineWidthCache.size() > lineWidthCacheSize) {
      int excess = lineWidthCache.size() - lineWidthCacheSize;
      for (int i = lineWidthCache.size() - 1; i >= 0 && excess > 0; i--) {
        lineWidthCache.removeAt(i);
        excess--;
      }
    }
  }

  public void setRenderWindow(int windowSize, int prefetchLines) {
    setRenderWindow(windowSize, prefetchLines, true);
  }

  public void setRenderWindow(int windowSize, int prefetchLines, boolean reload) {
    int safeWindow = Math.max(10, windowSize);
    int safePrefetch = Math.max(0, prefetchLines);
    int minWindow = computeMinWindowSizeForPrefetch(safePrefetch);
    if (safeWindow < minWindow) safeWindow = minWindow;
    if (this.windowSize == safeWindow && this.prefetchLines == safePrefetch) return;
    this.windowSize = safeWindow;
    this.prefetchLines = safePrefetch;
    editor.highlight.invalidateHighlightEnsureRange();
    editor.bracketGuides.invalidateBracketGuideCache();
    if (editor.wordWrap.isWordWrapEnabled) editor.wordWrap.invalidateWrapMetrics(true);
    editor.wordWrap.requestWrapPrefixRebuild();
    if (reload) reloadWindowAroundVisible(false);
  }

  public int computeMinWindowSize() {
    return computeMinWindowSizeForPrefetch(prefetchLines);
  }

  public int computeMinWindowSizeForPrefetch(int prefetch) {
    if (editor.textRender.lineHeight <= 0f || editor.getHeight() <= 0) return 10;
    float effectiveHeight = editor.getHeight();
    int visibleLines =
        Math.max(1, (int) Math.ceil(effectiveHeight / editor.textRender.lineHeight) + 2);
    int minTotal = Math.max(visibleLines * 2, visibleLines + 6);
    int minWindow = minTotal - (Math.max(0, prefetch) * 2);
    return Math.max(10, minWindow);
  }

  public void reloadWindowAroundVisible(boolean recalcWidthSync) {
    if (editor.getWidth() == 0 || editor.getHeight() == 0) {
      editor.invalidate();
      return;
    }
    int firstVisibleLine = Math.max(0, editor.wordWrap.getGlobalLineForY(editor.scroll.scrollY));
    int targetStart = Math.max(0, firstVisibleLine - prefetchLines);
    editor.fileIO.loadWindowAround(targetStart, null, recalcWidthSync);
  }
}
