package com.yn.sodiumeditor.io;

import com.yn.sodiumeditor.SodiumEditor;
import java.io.RandomAccessFile;
import java.io.BufferedReader;
import java.io.FileInputStream;

/**
 * Handles building selected text from file or window.
 */
public class SelectionTextBuilder {

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
      long startByte;
      if (editor.fileIO.isIndexReady) {
        synchronized (editor.fileIO.lineOffsetsLock) {
          if (sL >= 0 && sL < editor.fileIO.lineOffsets.length) startByte = editor.fileIO.lineOffsets[sL];
          else startByte = raf.length();
        }
      } else {
        startByte = editor.editOperators.findLineStartByteByScanning(raf, sL);
      }

      raf.seek(startByte);
      try (BufferedReader br =
          new BufferedReader(
              new java.io.InputStreamReader(new FileInputStream(raf.getFD()), editor.fileIO.fileCharset), 8192)) {

        StringBuilder sb = new StringBuilder();
        for (int L = sL; L <= eL; L++) {
          String fileLine = br.readLine();
          if (fileLine == null) fileLine = "";

          String ln;
          synchronized (editor.windowRender.modifiedLines) {
            ln = editor.windowRender.modifiedLines.containsKey(L) ? editor.windowRender.modifiedLines.get(L) : fileLine;
          }
          if (ln == null) ln = "";

          int startIdx = (L == sL) ? Math.min(sC, ln.length()) : 0;
          int endIdx = (L == eL) ? Math.min(eC, ln.length()) : ln.length();
          if (endIdx > startIdx) sb.append(ln, startIdx, endIdx);
          if (L < eL) sb.append('\n');

          if (sb.length() > maxChars) return sb.substring(0, maxChars);
        }
        return sb.toString();
      }
    } catch (Exception e) {
      return null;
    }
  }
}
