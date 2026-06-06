package com.yn.sodiumeditor.io;

import com.yn.sodiumeditor.SodiumEditor;
import java.io.RandomAccessFile;

/**
 * Handles building selected text from file or window.
 */
public class SelectionTextBuilder {
  private static final int MAX_SELECTION_LINE_READ_BYTES = 64 * 1024;

  private final SodiumEditor editor;

  public SelectionTextBuilder(SodiumEditor editor) {
    this.editor = editor;
  }

  /**
   * Build selected text from window (fast path).
   */
  public String buildSelectedTextFromWindow(int sL, int sC, int eL, int eC, int maxChars) {
    StringBuilder sb = new StringBuilder();
    synchronized (editor.windowRender.linesWindow) {
      for (int L = sL; L <= eL; L++) {
        int local = L - editor.windowRender.windowStartLine;
        String ln = (local >= 0 && local < editor.windowRender.linesWindow.size()) ? editor.windowRender.linesWindow.get(local) : "";
        if (ln == null) ln = "";
        int startIdx = (L == sL) ? Math.min(sC, ln.length()) : 0;
        int endIdx = (L == eL) ? Math.min(eC, ln.length()) : ln.length();
        if (endIdx > startIdx) sb.append(ln, startIdx, endIdx);
        if (L < eL) sb.append('\n');

        if (sb.length() > maxChars) return sb.substring(0, maxChars);
      }
    }
    return sb.toString();
  }

  /**
   * Build selected text blocking (reads from file if needed).
   */
  public String buildSelectedTextBlocking(int sL, int sC, int eL, int eC, int maxChars) {
    if (editor.selection.comparePos(sL, sC, eL, eC) > 0) {
      int tL = sL, tC = sC;
      sL = eL;
      sC = eC;
      eL = tL;
      eC = tC;
    }

    // In-memory (no file backing): build from render-safe access
    if (editor.fileIO.sourceFile == null || editor.fileIO.isFileCleared) {
      return editor.selection.buildSelectedTextFromWindow(sL, sC, eL, eC, maxChars);
    }

    // If the selection is fully inside the current window, prefer the window snapshot
    boolean fullyInWindow = (sL >= editor.windowRender.windowStartLine) && (eL < editor.windowRender.windowStartLine + editor.windowRender.linesWindow.size());
    if (fullyInWindow) {
      return editor.selection.buildSelectedTextFromWindow(sL, sC, eL, eC, maxChars);
    }

    // File-backed: sequential read from start line
    try (RandomAccessFile raf = new RandomAccessFile(editor.fileIO.sourceFile, "r")) {
      StringBuilder sb = new StringBuilder();
      for (int L = sL; L <= eL; L++) {
        String ln;
        synchronized (editor.windowRender.modifiedLines) {
          ln = editor.windowRender.modifiedLines.get(L);
        }
        if (ln == null) {
          long lineOffset = getLineOffset(raf, L);
          ln = lineOffset >= 0
              ? editor.fileIO.readLinePrefixUtf8AtByte(raf, lineOffset, getSelectionLineReadLimit(sC, eC, maxChars, sb.length()))
              : "";
        }
        if (ln == null) ln = "";

        int startIdx = (L == sL) ? Math.min(sC, ln.length()) : 0;
        int endIdx = (L == eL) ? Math.min(eC, ln.length()) : ln.length();
        if (endIdx > startIdx) sb.append(ln, startIdx, endIdx);
        if (L < eL) sb.append('\n');

        if (sb.length() > maxChars) return sb.substring(0, maxChars);
      }
      return sb.toString();
    } catch (Exception e) {
      return null;
    }
  }

  private long getLineOffset(RandomAccessFile raf, int line) throws Exception {
    if (editor.fileIO.isIndexReady) {
      synchronized (editor.fileIO.lineOffsetsLock) {
        if (line >= 0 && line < editor.fileIO.lineOffsets.length) return editor.fileIO.lineOffsets[line];
      }
      return -1L;
    }
    return editor.editOperators.findLineStartByteByScanning(raf, line);
  }

  private int getSelectionLineReadLimit(int sC, int eC, int maxChars, int currentChars) {
    int remaining = Math.max(0, maxChars - currentChars);
    int requestedColumn = Math.max(Math.max(0, sC), Math.max(0, eC));
    long desired = Math.max(1024L, (long) requestedColumn + remaining + 1L);
    return (int) Math.min(MAX_SELECTION_LINE_READ_BYTES, desired);
  }
}
