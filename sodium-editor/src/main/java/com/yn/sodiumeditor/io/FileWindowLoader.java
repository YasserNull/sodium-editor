package com.yn.sodiumeditor.io;

import android.util.SparseIntArray;
import androidx.annotation.Nullable;
import com.yn.sodiumeditor.SodiumEditor;
import com.yn.sodiumeditor.core.StreamedCharSlice;
import com.yn.sodiumeditor.core.binary.BinaryDocument;
import java.io.RandomAccessFile;
import java.nio.charset.Charset;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/** Handles loading chunks of files into the in-memory window. */
public class FileWindowLoader {
  private final SodiumEditor editor;
  private final FileIO fileIO;

  public FileWindowLoader(SodiumEditor editor, FileIO fileIO) {
    this.editor = editor;
    this.fileIO = fileIO;
  }

  public void checkAndLoadWindow() {
    // While there are pending structural edits (line insert/delete), the in-memory window
    // does not match the on-disk file. Reloading from disk reintroduces deleted lines.
    if (hasPendingInMemoryEdits()) return;
    if (fileIO.sourceFile == null
        || fileIO.isFileCleared
        || editor.getWidth() == 0
        || editor.getHeight() == 0
        || fileIO.isWindowLoading) return;
    int firstIdx = (int) (editor.scroll.scrollY / editor.textRender.lineHeight);
    int lastIdx = firstIdx + (int) Math.ceil(editor.getHeight() / editor.textRender.lineHeight);
    int fL, lL;
    if (editor.wordWrap.isWordWrapEnabled) {
      fL = editor.wordWrap.getVisualPositionForIndex(firstIdx).line;
      lL = editor.wordWrap.getVisualPositionForIndex(lastIdx).line;
    } else {
      fL = firstIdx;
      lL = lastIdx;
    }
    int loadTarget = fL;
    int winEnd;
    synchronized (editor.windowRender.linesWindow) {
      winEnd = editor.windowRender.windowStartLine + editor.windowRender.linesWindow.size() - 1;
    }
    boolean top =
        editor.windowRender.windowStartLine > 0
            && loadTarget < editor.windowRender.windowStartLine + editor.windowRender.prefetchLines;
    boolean bottom = !fileIO.isEof && lL > winEnd - editor.windowRender.prefetchLines;
    boolean out = loadTarget < editor.windowRender.windowStartLine || loadTarget > winEnd;
    if (top || bottom || out)
      loadWindowAround(Math.max(0, loadTarget - editor.windowRender.prefetchLines), null, false);
  }

  public void loadWindowAround(
      int startLine, @Nullable Runnable onComplete, boolean recalcWidthSync) {
    if (hasPendingInMemoryEdits()) {
      if (onComplete != null) editor.post(onComplete);
      return;
    }
    if (fileIO.isWindowLoading) {
      if (onComplete != null) {
        editor.postDelayed(() -> loadWindowAround(startLine, onComplete, recalcWidthSync), 16L);
      }
      return;
    }
    editor.loadingCircle.maxWidthRecalcToken++;
    if (fileIO.isFileCleared || fileIO.sourceFile == null) {
      if (onComplete != null) editor.post(onComplete);
      return;
    }
    fileIO.isWindowLoading = true;
    final int taskVersion = fileIO.ioTaskVersion.incrementAndGet();
    fileIO.ioHandler.post(
        () -> {
          try {
            if (taskVersion != fileIO.ioTaskVersion.get()) {
              editor.post(
                  () -> {
                    fileIO.isWindowLoading = false;
                    checkAndLoadWindow();
                  });
              return;
            }
            int actualStart = Math.max(0, startLine);
            if (fileIO.isIndexReady()) {
              synchronized (fileIO.getLineOffsetsLock()) {
                long[] offsets = fileIO.getLineOffsets();
                if (offsets.length > 0 && actualStart >= offsets.length)
                  actualStart = Math.max(0, offsets.length - 1);
              }
            }
            loadWindowInternal(actualStart, taskVersion, onComplete, recalcWidthSync);
          } catch (Exception e) {
            editor.post(
                () -> {
                  fileIO.isWindowLoading = false;
                  if (onComplete != null) onComplete.run();
                });
          }
        });
  }

  public void loadTailWindowForSelectAll(int lastLine, @Nullable Runnable onComplete) {
    if (hasPendingInMemoryEdits()) {
      if (onComplete != null) editor.post(onComplete);
      return;
    }
    if (fileIO.isWindowLoading) {
      editor.postDelayed(() -> loadTailWindowForSelectAll(lastLine, onComplete), 16L);
      return;
    }
    editor.loadingCircle.maxWidthRecalcToken++;
    if (fileIO.isFileCleared || fileIO.sourceFile == null) {
      if (onComplete != null) editor.post(onComplete);
      return;
    }
    fileIO.isWindowLoading = true;
    final int taskVersion = fileIO.ioTaskVersion.incrementAndGet();
    final int requestedLastLine = Math.max(0, lastLine);
    fileIO.ioHandler.post(
        () -> {
          try {
            TailWindowResult result = loadTailWindowInternal(requestedLastLine);
            editor.post(() -> applyTailWindowResult(taskVersion, result, onComplete));
          } catch (Exception e) {
            editor.post(
                () -> {
                  fileIO.isWindowLoading = false;
                  if (onComplete != null) onComplete.run();
                });
          }
        });
  }

  private TailWindowResult loadTailWindowInternal(int lastLine) throws Exception {
    ArrayList<String> newWin = new ArrayList<>();
    SparseIntArray newLengths = new SparseIntArray();
    SparseIntArray newSliceStarts = new SparseIntArray();
    int limit = editor.windowRender.windowSize + (editor.windowRender.prefetchLines * 2);
    ArrayList<Long> starts = new ArrayList<>();
    try (RandomAccessFile raf = new RandomAccessFile(fileIO.sourceFile, "r")) {
      long fileLen = raf.length();
      if (fileLen == 0L) {
        starts.add(0L);
      } else {
        boolean endsWithNewline;
        raf.seek(fileLen - 1L);
        endsWithNewline = raf.read() == '\n';
        ArrayDeque<Long> startsDescending = new ArrayDeque<>();
        if (endsWithNewline) startsDescending.add(fileLen);
        byte[] buffer = new byte[FileIO.FILE_IO_BUFFER_SIZE];
        long readEnd = fileLen;
        while (readEnd > 0L && startsDescending.size() < limit) {
          int toRead = (int) Math.min(buffer.length, readEnd);
          long readStart = readEnd - toRead;
          raf.seek(readStart);
          raf.readFully(buffer, 0, toRead);
          for (int i = toRead - 1; i >= 0 && startsDescending.size() < limit; i--) {
            if (buffer[i] == '\n') {
              long lineStart = readStart + i + 1L;
              Long last = startsDescending.peekLast();
              if (last == null || last.longValue() != lineStart) startsDescending.add(lineStart);
            }
          }
          readEnd = readStart;
        }
        if (startsDescending.size() < limit) {
          Long last = startsDescending.peekLast();
          if (last == null || last.longValue() != 0L) startsDescending.add(0L);
        }
        starts.addAll(startsDescending);
        Collections.reverse(starts);
      }

      int actualStart = Math.max(0, lastLine - starts.size() + 1);
      for (int i = 0; i < starts.size(); i++) {
        int globalLine = actualStart + i;
        long start = starts.get(i);
        long nextStart = (i + 1 < starts.size()) ? starts.get(i + 1) : fileLen;
        long bLen = getLineByteLengthFromTailStart(raf, start, nextStart, fileLen);
        int len = (int) Math.min(Integer.MAX_VALUE, bLen);
        if (editor.windowRender.shouldStreamLineLength(len)) {
          int sS = 0;
          if (editor.windowRender.isSingleByteCharset()) {
            int sE = Math.max(1, Math.min(len, editor.textRender.getInitialStreamedSliceSize()));
            newWin.add(fileIO.readLineSliceAtByte(raf, start, bLen, sS, sE));
          } else {
            StreamedCharSlice slice =
                fileIO.readLineSliceByChars(
                    raf,
                    start,
                    sS,
                    Math.max(1, editor.textRender.getInitialStreamedSliceSize()),
                    true);
            newWin.add(slice.text);
            len = slice.length;
          }
          newLengths.put(globalLine, len);
          newSliceStarts.put(globalLine, sS);
        } else {
          newWin.add(fileIO.readLineUtf8AtByte(raf, start));
        }
      }
      if (newWin.isEmpty()) newWin.add("");
      return new TailWindowResult(newWin, newLengths, newSliceStarts, actualStart);
    }
  }

  private long getLineByteLengthFromTailStart(
      RandomAccessFile raf, long start, long nextStart, long fileLen) throws Exception {
    long end = Math.max(start, Math.min(nextStart, fileLen));
    long len = Math.max(0L, end - start);
    if (len > 0L && end <= fileLen) {
      raf.seek(end - 1L);
      if (raf.read() == '\n') {
        len--;
        if (len > 0L) {
          raf.seek(start + len - 1L);
          if (raf.read() == '\r') len--;
        }
      }
    }
    return Math.max(0L, len);
  }

  private void applyTailWindowResult(
      int taskVersion, TailWindowResult result, @Nullable Runnable onComplete) {
    fileIO.isWindowLoading = false;
    if (taskVersion != fileIO.ioTaskVersion.get()) {
      checkAndLoadWindow();
      return;
    }
    if (hasPendingInMemoryEdits()) {
      if (onComplete != null) onComplete.run();
      return;
    }
    synchronized (editor.windowRender.linesWindow) {
      editor.windowRender.linesWindow.clear();
      editor.windowRender.linesWindow.addAll(result.lines);
      editor.windowRender.windowStartLine = result.windowStartLine;
      fileIO.isEof = true;
      synchronized (editor.windowRender.modifiedLines) {
        for (Map.Entry<Integer, String> entry :
            new java.util.ArrayList<>(editor.windowRender.modifiedLines.entrySet())) {
          int line = entry.getKey();
          if (line >= result.windowStartLine
              && line < result.windowStartLine + result.lines.size()) {
            editor.windowRender.linesWindow.set(line - result.windowStartLine, entry.getValue());
          }
        }
      }
    }
    applyStreamedInfo(result.streamedLengths, result.streamedSliceStarts);
    editor.autoPair.clearBalanceCache();
    editor.lineNumber.invalidateLineNumberCache();
    editor.highlight.clearHighlightCaches();
    fileIO.recalculateMaxLineWidthAsync();
    if (editor.wordWrap.isWordWrapEnabled
        && !editor.wordWrap.shouldSuppressWrapMetricsForFastSelectAll()) {
      if (editor.getWidth() > 0) editor.wordWrap.buildWrapMetricsForWindowSnapshot();
      editor.wordWrap.requestWrapPrefixRebuild();
    }
    editor.requestLayout();
    editor.invalidate();
    if (onComplete != null) onComplete.run();
  }

  private void loadWindowInternal(
      int actualStart, int taskVersion, @Nullable Runnable onComplete, boolean recalcWidthSync)
      throws Exception {
    if (editor.binaryRender.binaryFileFeaturePolicyActive
        && editor.binaryRender.binaryDocument != null) {
      loadBinaryWindowInternal(actualStart, taskVersion, onComplete, recalcWidthSync);
      return;
    }
    List<String> newWin = new ArrayList<>();
    SparseIntArray newLengths = new SparseIntArray();
    SparseIntArray newSliceStarts = new SparseIntArray();
    boolean endsWithNl = false, reachedEof = false, trailingEmpty = false;

    try (RandomAccessFile raf = new RandomAccessFile(fileIO.sourceFile, "r")) {
      long fileLen = raf.length();
      if (fileLen > 0) {
        raf.seek(fileLen - 1);
        endsWithNl = (raf.read() == '\n');
      }
      int limit = editor.windowRender.windowSize + (editor.windowRender.prefetchLines * 2);
      int lineIdx = actualStart;

      if (fileIO.isIndexReady()) {
        while (newWin.size() < limit) {
          synchronized (fileIO.getLineOffsetsLock()) {
            if (lineIdx >= fileIO.getLineOffsets().length) {
              reachedEof = true;
              break;
            }
          }
          long start = fileIO.getLineOffsets()[lineIdx];
          long bLen = fileIO.indexer.getLineByteLengthFromIndex(raf, lineIdx, fileLen);
          int len = (int) Math.min(Integer.MAX_VALUE, bLen);
          if (editor.windowRender.shouldStreamLineLength(len)) {
            int sS = 0;
            if (editor.windowRender.isSingleByteCharset()) {
              int sE = Math.max(1, Math.min(len, editor.textRender.getInitialStreamedSliceSize()));
              newWin.add(fileIO.readLineSliceAtByte(raf, start, bLen, sS, sE));
            } else {
              StreamedCharSlice slice =
                  fileIO.readLineSliceByChars(
                      raf,
                      start,
                      sS,
                      Math.max(1, editor.textRender.getInitialStreamedSliceSize()),
                      true);
              newWin.add(slice.text);
              len = slice.length;
            }
            newLengths.put(lineIdx, len);
            newSliceStarts.put(lineIdx, sS);
          } else {
            newWin.add(fileIO.readLineUtf8AtByte(raf, start));
          }
          lineIdx++;
        }
        if (endsWithNl) {
          synchronized (fileIO.getLineOffsetsLock()) {
            trailingEmpty =
                fileIO.getLineOffsets().length > 0
                    && fileIO.getLineOffsets()[fileIO.getLineOffsets().length - 1] == fileLen;
          }
        }
      } else {
        raf.seek(0);
        int skipped = 0;
        while (skipped < actualStart) {
          if (fileIO.metadata.scanLineLength(raf).reachedEof) break;
          skipped++;
        }
        actualStart = skipped;
        lineIdx = actualStart;
        while (newWin.size() < limit) {
          long start = raf.getFilePointer();
          if (start >= fileLen) {
            reachedEof = true;
            break;
          }
          FileMetadata.LineScanResult scan = fileIO.metadata.scanLineLength(raf);
          long after = raf.getFilePointer();
          int len = (int) Math.min(Integer.MAX_VALUE, scan.length);
          if (editor.windowRender.shouldStreamLineLength(len)) {
            int sS = 0;
            if (editor.windowRender.isSingleByteCharset()) {
              int sE = Math.max(1, Math.min(len, editor.textRender.getInitialStreamedSliceSize()));
              newWin.add(fileIO.readLineSliceAtByte(raf, start, scan.length, sS, sE));
            } else {
              StreamedCharSlice slice =
                  fileIO.readLineSliceByChars(
                      raf,
                      start,
                      sS,
                      Math.max(1, editor.textRender.getInitialStreamedSliceSize()),
                      true);
              newWin.add(slice.text);
              len = slice.length;
            }
            newLengths.put(lineIdx, len);
            newSliceStarts.put(lineIdx, sS);
          } else {
            raf.seek(start);
            byte[] buf = new byte[len];
            if (len > 0) raf.readFully(buf);
            newWin.add(
                len > 0
                    ? (editor.binaryRender.isBinarySafeRenderingEnabled()
                        ? (editor.binaryRender.binaryFileFeaturePolicyActive
                            ? editor.binaryRender.rawBytesToControlVisibleAndCacheSpans(
                                buf, buf.length, lineIdx)
                            : bytesToControlVisibleAndCacheSpans(buf, buf.length, lineIdx, fileIO.fileCharset))
                        : new String(buf, fileIO.fileCharset))
                    : "");
          }
          raf.seek(after);
          if (scan.reachedEof) {
            reachedEof = true;
            break;
          }
          lineIdx++;
        }
      }
    }

    if (newWin.isEmpty()) {
      newWin.add("");
      actualStart = 0;
    }
    if (reachedEof && endsWithNl && !trailingEmpty) newWin.add("");
    final boolean finalEof =
        newWin.size() < editor.windowRender.windowSize + (editor.windowRender.prefetchLines * 2);
    final int fStart = actualStart;

    editor.post(
        () -> {
          fileIO.isWindowLoading = false;
          if (taskVersion != fileIO.ioTaskVersion.get()) {
            checkAndLoadWindow();
            return;
          }
          if (hasPendingInMemoryEdits()) {
            if (onComplete != null) onComplete.run();
            return;
          }
          synchronized (editor.windowRender.linesWindow) {
            editor.windowRender.linesWindow.clear();
            editor.windowRender.linesWindow.addAll(newWin);
            editor.windowRender.windowStartLine = fStart;
            fileIO.isEof = finalEof;
            synchronized (editor.windowRender.modifiedLines) {
              for (Map.Entry<Integer, String> entry :
                  new java.util.ArrayList<>(editor.windowRender.modifiedLines.entrySet())) {
                int line = entry.getKey();
                if (line >= fStart && line < fStart + newWin.size())
                  editor.windowRender.linesWindow.set(line - fStart, entry.getValue());
              }
            }
          }
          applyStreamedInfo(newLengths, newSliceStarts);
          editor.autoPair.clearBalanceCache();
          editor.lineNumber.invalidateLineNumberCache();
          editor.highlight.clearHighlightCaches();
          if (recalcWidthSync) editor.windowRender.recalculateMaxLineWidth();
          else fileIO.recalculateMaxLineWidthAsync();
          if (editor.wordWrap.isWordWrapEnabled) {
            if (!editor.wordWrap.shouldSuppressWrapMetricsForFastSelectAll()) {
              if (editor.getWidth() > 0) editor.wordWrap.buildWrapMetricsForWindowSnapshot();
              editor.wordWrap.requestWrapPrefixRebuild();
            }
          }
          editor.requestLayout();
          editor.invalidate();
          if (onComplete != null) onComplete.run();
        });
  }

  private String bytesToControlVisibleAndCacheSpans(
      byte[] buf, int len, int lineIdx, Charset charset) {
    return editor.binaryRender.bytesToControlVisibleAndCacheSpans(buf, len, lineIdx, charset);
  }

  private void loadBinaryWindowInternal(
      int actualStart, int taskVersion, @Nullable Runnable onComplete, boolean recalcWidthSync)
      throws Exception {
    BinaryDocument document = editor.binaryRender.binaryDocument;
    ArrayList<String> newWin = new ArrayList<>();
    SparseIntArray newLengths = new SparseIntArray();
    SparseIntArray newSliceStarts = new SparseIntArray();
    int rowCount = document.getRowCount();
    int row = Math.max(0, Math.min(actualStart, Math.max(0, rowCount - 1)));
    int windowStart = row;
    int limit = editor.windowRender.windowSize + (editor.windowRender.prefetchLines * 2);

    try (RandomAccessFile raf = new RandomAccessFile(document.getFile(), "r")) {
      byte[] rowBuffer = new byte[BinaryDocument.BYTES_PER_ROW];
      while (newWin.size() < limit && row < rowCount) {
        long offset = document.getOffsetForRow(row);
        int len =
            (int)
                Math.min(
                    BinaryDocument.BYTES_PER_ROW, Math.max(0L, document.getFileLength() - offset));
        if (len <= 0) break;
        raf.seek(offset);
        raf.readFully(rowBuffer, 0, len);
        String line = new String(rowBuffer, 0, len, fileIO.fileCharset);
        newWin.add(line);
        newLengths.put(row, line.length());
        newSliceStarts.put(row, 0);
        row++;
      }
    }

    if (newWin.isEmpty()) newWin.add("");
    final boolean finalEof = windowStart + newWin.size() >= rowCount;
    final int fStart = windowStart;

    editor.post(
        () -> {
          fileIO.isWindowLoading = false;
          if (taskVersion != fileIO.ioTaskVersion.get()) {
            checkAndLoadWindow();
            return;
          }
          if (hasPendingInMemoryEdits()) {
            if (onComplete != null) onComplete.run();
            return;
          }
          synchronized (editor.windowRender.linesWindow) {
            editor.windowRender.linesWindow.clear();
            editor.windowRender.linesWindow.addAll(newWin);
            editor.windowRender.windowStartLine = fStart;
            fileIO.isEof = finalEof;
          }
          applyStreamedInfo(newLengths, newSliceStarts);
          editor.lineNumber.invalidateLineNumberCache();
          editor.highlight.clearHighlightCaches();
          if (recalcWidthSync) editor.windowRender.recalculateMaxLineWidth();
          else fileIO.recalculateMaxLineWidthAsync();
          editor.requestLayout();
          editor.invalidate();
          if (onComplete != null) onComplete.run();
        });
  }

  private boolean hasPendingInMemoryEdits() {
    if (editor.editOperators.lineCountDelta != 0) return true;
    if (editor.windowRender.hasAnyModifiedLines()) return true;
    synchronized (editor.editOperators.history.pendingEdits) {
      return !editor.editOperators.history.pendingEdits.isEmpty();
    }
  }

  private static class TailWindowResult {
    final ArrayList<String> lines;
    final SparseIntArray streamedLengths;
    final SparseIntArray streamedSliceStarts;
    final int windowStartLine;

    TailWindowResult(
        ArrayList<String> lines,
        SparseIntArray streamedLengths,
        SparseIntArray streamedSliceStarts,
        int windowStartLine) {
      this.lines = lines;
      this.streamedLengths = streamedLengths;
      this.streamedSliceStarts = streamedSliceStarts;
      this.windowStartLine = windowStartLine;
    }
  }

  private void applyStreamedInfo(SparseIntArray lengths, SparseIntArray starts) {
    synchronized (editor.windowRender.streamedLinesLock) {
      editor.windowRender.streamedLineLengths.clear();
      editor.windowRender.streamedLineSliceStarts.clear();
      for (int i = 0; i < lengths.size(); i++) {
        int key = lengths.keyAt(i);
        editor.windowRender.streamedLineLengths.put(key, lengths.valueAt(i));
        editor.windowRender.streamedLineSliceStarts.put(key, starts.get(key, 0));
      }
    }
    synchronized (editor.windowRender.streamedLinesLockLinesLock) {
      editor.windowRender.streamedLinesLockLineLengths.clear();
      editor.windowRender.streamedLinesLockLineSliceStarts.clear();
      for (int i = 0; i < lengths.size(); i++) {
        int key = lengths.keyAt(i);
        editor.windowRender.streamedLinesLockLineLengths.put(key, lengths.valueAt(i));
        editor.windowRender.streamedLinesLockLineSliceStarts.put(key, starts.get(key, 0));
      }
    }
  }
}
