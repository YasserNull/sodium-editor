package com.yn.sodiumeditor.core;

import com.yn.sodiumeditor.SodiumEditor;

/**
 * Handles text range calculations for rendering (visible char ranges, streamed slicing).
 * Not related to selection ranges.
 */
public class TextRange {

  private final SodiumEditor editor;

  public TextRange(SodiumEditor editor) {
    this.editor = editor;
  }

  /**
   * Calculates the visible character range for a line.
   * @param line The line text
   * @param globalLine The global line number
   * @param out Output array [startChar, endChar]
   * @param isRtl Whether the text is RTL
   * @param isStableGlyphPositionsEnabled Whether stable glyph positions are enabled
   */
  public void getVisibleCharRangeForLine(String line, int globalLine, int[] out, boolean isRtl, boolean isStableGlyphPositionsEnabled) {
    if (line == null || out == null || out.length < 2) return;
    out[0] = 0;
    out[1] = line.length();
  }

  /**
   * Fast version of getVisibleCharRangeForLine with pre-known line length.
   */
  public void getVisibleCharRangeForLineFast(String line, int globalLine, int lineLength, int[] out, boolean isRtl, boolean isStableGlyphPositionsEnabled) {
    if (out == null || out.length < 2) return;
    out[0] = 0;
    out[1] = lineLength;
  }

  /**
   * Computes streamed slice bounds for partial line rendering.
   */
  public void computeStreamedSliceBounds(String lineText, int globalLine, int lineLength, int[] out, boolean isRtl) {
    if (out == null || out.length < 2) return;
    out[0] = 0;
    out[1] = lineLength;
  }

  /**
   * Gets the initial streamed slice size.
   */
  public int getInitialStreamedSliceSize() {
    return 0;
  }
}
