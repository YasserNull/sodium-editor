package com.yn.sodiumeditor.core.wordwrap;

import android.graphics.Paint;
import com.yn.sodiumeditor.SodiumEditor;
import java.io.RandomAccessFile;

/** Reads file-backed lines and estimates large-line wrap counts for metrics. */
public class WordWrapFileLineReader {
  private static final int MAX_FILE_WRAP_LINE_BYTES = 64 * 1024;

  private final SodiumEditor editor;
  private final WordWrap wordWrap;

  public WordWrapFileLineReader(SodiumEditor editor, WordWrap wordWrap) {
    this.editor = editor;
    this.wordWrap = wordWrap;
  }

  public int getWrapCountForFileLine(
      int globalLine, RandomAccessFile raf, long fileLen, int widthPx, Paint paint)
      throws Exception {
    String modified = editor.windowRender.getModifiedLine(globalLine);
    if (modified != null) {
      return wordWrap.engine.getWrapCountForLine(globalLine, modified, widthPx, paint);
    }
    if (!editor.fileIO.isIndexReady) return wordWrap.engine.getDefaultWrapCountForLine(globalLine);

    long offset;
    synchronized (editor.fileIO.lineOffsetsLock) {
      if (globalLine < 0 || globalLine >= editor.fileIO.lineOffsets.length) {
        return wordWrap.engine.getDefaultWrapCountForLine(globalLine);
      }
      offset = editor.fileIO.lineOffsets[globalLine];
    }
    long byteLen = editor.fileIO.getLineByteLengthFromIndex(raf, globalLine, fileLen);
    if (byteLen > MAX_FILE_WRAP_LINE_BYTES) {
      return estimateWrapCountFromByteLength(byteLen, widthPx, paint);
    }
    String line =
        editor.fileIO.readLinePrefixUtf8AtByte(
            raf, offset, (int) Math.min(byteLen, MAX_FILE_WRAP_LINE_BYTES));
    return wordWrap.engine.getWrapCountForLine(globalLine, line, widthPx, paint);
  }

  public int estimateWrapCountFromByteLength(long byteLen, int widthPx, Paint paint) {
    float avgCharWidth = Math.max(1f, paint.measureText("m"));
    int charsPerVisualLine = Math.max(1, (int) (Math.max(1, widthPx) / avgCharWidth));
    long wraps = (Math.max(0L, byteLen) + charsPerVisualLine - 1L) / charsPerVisualLine;
    return (int) Math.max(1L, Math.min((long) Integer.MAX_VALUE, wraps));
  }
}
