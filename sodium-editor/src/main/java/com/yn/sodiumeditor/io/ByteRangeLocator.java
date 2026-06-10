package com.yn.sodiumeditor.io;

import com.yn.sodiumeditor.SodiumEditor;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.RandomAccessFile;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;

/** Handles byte range computation and line scanning in files. */
public class ByteRangeLocator {
  private final SodiumEditor editor;

  public ByteRangeLocator(SodiumEditor editor) {
    this.editor = editor;
  }

  public EditOp.RangeBytes computeByteRangeFastOrScan(File file, int sL, int sC, int eL, int eC) {
    if (comparePos(sL, sC, eL, eC) > 0) {
      int tmpL = sL;
      sL = eL;
      eL = tmpL;
      int tmpC = sC;
      sC = eC;
      eC = tmpC;
    }
    EditOp.RangeBytes indexed = computeByteRangeFromReadyIndex(file, sL, sC, eL, eC);
    if (indexed != null) return indexed;
    return computeByteRangeByScanning(file, sL, sC, eL, eC);
  }

  public EditOp.RangeBytes computeByteRangeUsingIndex(File file, int sL, int sC, int eL, int eC) {
    if (comparePos(sL, sC, eL, eC) > 0) {
      int tmpL = sL;
      sL = eL;
      eL = tmpL;
      int tmpC = sC;
      sC = eC;
      eC = tmpC;
    }
    EditOp.RangeBytes indexed = computeByteRangeFromReadyIndex(file, sL, sC, eL, eC);
    if (indexed != null) return indexed;
    try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
      long startLine = findLineStartByteByScanning(raf, sL);
      long endLine = findLineStartByteByScanning(raf, eL);
      long start = byteOffsetForChar(raf, startLine, sC);
      long end = byteOffsetForChar(raf, endLine, eC);
      return new EditOp.RangeBytes(start, end);
    } catch (Exception e) {
      e.printStackTrace();
      return new EditOp.RangeBytes(0, 0);
    }
  }

  private EditOp.RangeBytes computeByteRangeFromReadyIndex(
      File file, int sL, int sC, int eL, int eC) {
    if (editor == null || editor.fileIO == null || file == null || !editor.fileIO.isIndexReady)
      return null;
    File source = editor.fileIO.sourceFile;
    if (source == null || !sameFile(source, file)) return null;
    long startLine;
    long endLine;
    synchronized (editor.fileIO.lineOffsetsLock) {
      long[] offsets = editor.fileIO.lineOffsets;
      if (offsets == null || offsets.length == 0) return null;
      if (sL < 0 || eL < 0 || sL >= offsets.length || eL >= offsets.length) return null;
      startLine = offsets[sL];
      endLine = offsets[eL];
    }
    try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
      return new EditOp.RangeBytes(
          byteOffsetForChar(raf, startLine, sC), byteOffsetForChar(raf, endLine, eC));
    } catch (Exception ignored) {
      return null;
    }
  }

  private boolean sameFile(File a, File b) {
    try {
      return a.getCanonicalFile().equals(b.getCanonicalFile());
    } catch (Exception ignored) {
      return a.equals(b);
    }
  }

  public EditOp.RangeBytes computeByteRangeByScanning(File file, int sL, int sC, int eL, int eC) {
    if (comparePos(sL, sC, eL, eC) > 0) {
      int tmpL = sL;
      sL = eL;
      eL = tmpL;
      int tmpC = sC;
      sC = eC;
      eC = tmpC;
    }
    try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
      long[] offsets = findTwoLineStartBytesByScanning(raf, sL, eL);
      return new EditOp.RangeBytes(
          byteOffsetForChar(raf, offsets[0], sC), byteOffsetForChar(raf, offsets[1], eC));
    } catch (Exception e) {
      e.printStackTrace();
      return new EditOp.RangeBytes(0, 0);
    }
  }

  public int comparePos(int l1, int c1, int l2, int c2) {
    if (l1 < l2) return -1;
    if (l1 > l2) return 1;
    return Integer.compare(c1, c2);
  }

  public long[] findTwoLineStartBytesByScanning(RandomAccessFile raf, int lineA, int lineB)
      throws Exception {
    if (lineA < 0) lineA = 0;
    if (lineB < 0) lineB = 0;
    long[] result = new long[2];
    int currentLine = 0;
    raf.seek(0);

    long lineStart = 0;
    long filePos = 0L;
    byte[] buffer = new byte[64 * 1024];
    int n;
    while ((n = raf.read(buffer)) != -1) {
      for (int i = 0; i < n; i++) {
        if (currentLine == lineB) {
          if (currentLine == lineA) result[0] = lineStart;
          result[1] = lineStart;
          return result;
        }
        if (currentLine == lineA) result[0] = lineStart;
        if (buffer[i] == '\n') {
          currentLine++;
          lineStart = filePos + i + 1;
        }
      }
      filePos += n;
    }
    long eof = filePos;
    if (currentLine == lineA) result[0] = lineStart;
    else if (currentLine < lineA) result[0] = eof;
    if (currentLine == lineB) result[1] = lineStart;
    else if (currentLine < lineB) result[1] = eof;
    return result;
  }

  public long findLineStartByteByScanning(RandomAccessFile raf, int targetLine) throws Exception {
    if (targetLine <= 0) return 0L;
    int currentLine = 0;
    raf.seek(0);
    long offset = 0;
    byte[] buffer = new byte[64 * 1024];
    int n;
    while ((n = raf.read(buffer)) != -1) {
      for (int i = 0; i < n; i++) {
        if (buffer[i] == '\n') {
          currentLine++;
          if (currentLine == targetLine) return offset + i + 1;
        }
      }
      offset += n;
    }
    return offset;
  }

  private long byteOffsetForChar(RandomAccessFile raf, long lineStart, int charIndex)
      throws Exception {
    int safeChar = Math.max(0, charIndex);
    if (isSingleByteCharset()) return lineStart + safeChar;

    byte[] lineBytes = readLineBytes(raf, lineStart);
    Charset charset = editor.fileIO.fileCharset;
    CharsetDecoder decoder = charset.newDecoder();
    String line = new String(lineBytes, charset);
    int safeEnd = Math.max(0, Math.min(safeChar, line.length()));
    String prefix = line.substring(0, safeEnd);
    decoder.reset();
    return lineStart + prefix.getBytes(charset).length;
  }

  private byte[] readLineBytes(RandomAccessFile raf, long lineStart) throws Exception {
    raf.seek(lineStart);
    ByteArrayOutputStream out = new ByteArrayOutputStream(256);
    byte[] buffer = new byte[64 * 1024];
    while (true) {
      int n = raf.read(buffer);
      if (n <= 0) break;
      int stop = -1;
      for (int i = 0; i < n; i++) {
        if (buffer[i] == '\n') {
          stop = i;
          break;
        }
      }
      int count = stop >= 0 ? stop : n;
      if (count > 0 && buffer[count - 1] == '\r') count--;
      if (count > 0) out.write(buffer, 0, count);
      if (stop >= 0) break;
    }
    return out.toByteArray();
  }

  private boolean isSingleByteCharset() {
    if (editor == null || editor.fileIO == null) return true;
    try {
      return editor.windowRender.isSingleByteCharset();
    } catch (Exception ignored) {
      return editor.fileIO.fileCharset.newEncoder().maxBytesPerChar() <= 1f;
    }
  }
}
