package com.yn.sodiumeditor.io;

import com.yn.sodiumeditor.SodiumEditor;
import java.io.File;
import java.io.RandomAccessFile;

/** Handles file metadata, binary detection, and low-level range reading. */
public class FileMetadata {
  private static final long MAX_BINARY_LINE_SCAN_BYTES = 4096L;

  private final SodiumEditor editor;
  private final FileIO fileIO;

  public FileMetadata(SodiumEditor editor, FileIO fileIO) {
    this.editor = editor;
    this.fileIO = fileIO;
  }

  public boolean isBinaryFile(File file) {
    if (!fileIO.autoDetectBinaryFiles || file == null || !file.exists()) return false;
    long fileSize = file.length();
    if (fileSize == 0) return false;
    int sampleSize = (int) Math.min(fileIO.binaryDetectionSampleSize, fileSize);
    try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
      byte[] buffer = new byte[sampleSize];
      int bytesRead = raf.read(buffer);
      if (bytesRead <= 0) return false;
      if (hasKnownBinaryMagic(buffer, bytesRead)) return true;
      int nonPrintableCount = 0;
      int totalChars = 0;
      for (int i = 0; i < bytesRead; i++) {
        int b =
            buffer[i]
                & 0xFF; // IMPORTANT: avoid signed-byte misclassification for UTF-8 (e.g. Arabic)
        totalChars++;
        if (b == 0 || b < 9 || (b > 13 && b < 32) || b == 127) nonPrintableCount++;
      }
      if (totalChars == 0) return false;
      return ((double) nonPrintableCount / totalChars) > fileIO.binaryDetectionThreshold;
    } catch (Exception e) {
      return false;
    }
  }

  private boolean hasKnownBinaryMagic(byte[] buffer, int len) {
    if (len >= 4) {
      int b0 = buffer[0] & 0xFF;
      int b1 = buffer[1] & 0xFF;
      int b2 = buffer[2] & 0xFF;
      int b3 = buffer[3] & 0xFF;
      if (b0 == 0x7F && b1 == 'E' && b2 == 'L' && b3 == 'F') return true;
      if (b0 == 'M' && b1 == 'Z') return true;
      if (b0 == 0xCA && b1 == 0xFE && b2 == 0xBA && b3 == 0xBE) return true;
      if (b0 == 0xFE && b1 == 0xED && b2 == 0xFA && (b3 == 0xCE || b3 == 0xCF)) return true;
      if (b0 == 0xCE && b1 == 0xFA && b2 == 0xED && b3 == 0xFE) return true;
      if (b0 == 0xCF && b1 == 0xFA && b2 == 0xED && b3 == 0xFE) return true;
      if (b0 == 0x89 && b1 == 'P' && b2 == 'N' && b3 == 'G') return true;
      if (b0 == 'P'
          && b1 == 'K'
          && (b2 == 0x03 || b2 == 0x05 || b2 == 0x07)
          && (b3 == 0x04 || b3 == 0x06 || b3 == 0x08)) return true;
    }
    if (len >= 3) {
      int b0 = buffer[0] & 0xFF;
      int b1 = buffer[1] & 0xFF;
      int b2 = buffer[2] & 0xFF;
      if (b0 == 0xFF && b1 == 0xD8 && b2 == 0xFF) return true;
    }
    return false;
  }

  public static class LineScanResult {
    public final long length;
    public final boolean reachedEof;

    public LineScanResult(long length, boolean reachedEof) {
      this.length = length;
      this.reachedEof = reachedEof;
    }
  }

  public LineScanResult scanLineLength(RandomAccessFile raf) throws java.io.IOException {
    long start = raf.getFilePointer();
    long len = 0;
    boolean binaryCapActive = editor.binaryRender.binaryFileFeaturePolicyActive;
    byte[] buf = new byte[FileIO.FILE_IO_BUFFER_SIZE];
    while (true) {
      int readLimit = buf.length;
      if (binaryCapActive) {
        long remaining = MAX_BINARY_LINE_SCAN_BYTES - len;
        if (remaining <= 0) {
          raf.seek(start + len);
          return new LineScanResult(len, false);
        }
        readLimit = (int) Math.min(readLimit, remaining);
      }
      int n = raf.read(buf, 0, readLimit);
      if (n <= 0) return new LineScanResult(len, true);
      for (int i = 0; i < n; i++) {
        if (buf[i] == '\n') {
          int lineLen = i;
          if (i > 0 && buf[i - 1] == '\r') lineLen -= 1;
          len += Math.max(0, lineLen);
          raf.seek(start + (len + 1));
          return new LineScanResult(len, false);
        }
      }
      len += n;
      if (binaryCapActive && len >= MAX_BINARY_LINE_SCAN_BYTES) {
        raf.seek(start + len);
        return new LineScanResult(len, false);
      }
    }
  }

  public String readRangeText(File sourceFile, int sL, int sC, int eL, int eC) {
    if (editor.editOperators.comparePos(sL, sC, eL, eC) > 0) {
      int tL = sL, tC = sC;
      sL = eL;
      sC = eC;
      eL = tL;
      eC = tC;
    }
    if (sL >= editor.windowRender.windowStartLine
        && eL < editor.windowRender.windowStartLine + editor.windowRender.linesWindow.size()) {
      StringBuilder sb = new StringBuilder();
      for (int line = sL; line <= eL; line++) {
        String ln =
            editor.windowRender.getLineFromWindowLocal(line - editor.windowRender.windowStartLine);
        if (ln == null) ln = "";
        int from = (line == sL) ? Math.min(sC, ln.length()) : 0;
        int to = (line == eL) ? Math.min(eC, ln.length()) : ln.length();
        if (from < to) sb.append(ln, from, to);
        if (line < eL) sb.append('\n');
      }
      return sb.toString();
    }
    if (sourceFile == null || !sourceFile.exists()) return "";
    EditOp.RangeBytes range =
        editor.editOperators.computeByteRangeFastOrScan(sourceFile, sL, sC, eL, eC);
    if (range == null) return "";
    try (RandomAccessFile raf = new RandomAccessFile(sourceFile, "r")) {
      long len = raf.length();
      long startByte = Math.max(0, Math.min(range.startByte, len));
      long endByte = Math.max(0, Math.min(range.endByte, len));
      int size = (int) Math.min(Integer.MAX_VALUE, Math.abs(endByte - startByte));
      byte[] buf = new byte[size];
      raf.seek(Math.min(startByte, endByte));
      raf.readFully(buf);
      return new String(buf, fileIO.fileCharset);
    } catch (Exception ignore) {
      return "";
    }
  }
}
