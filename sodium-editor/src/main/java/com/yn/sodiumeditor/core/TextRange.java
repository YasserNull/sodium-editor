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
    computeStreamedSliceBounds(line, globalLine, line.length(), out, isRtl);
  }

  /**
   * Fast version of getVisibleCharRangeForLine with pre-known line length.
   */
  public void getVisibleCharRangeForLineFast(String line, int globalLine, int lineLength, int[] out, boolean isRtl, boolean isStableGlyphPositionsEnabled) {
    if (out == null || out.length < 2) return;
    computeStreamedSliceBounds(line, globalLine, lineLength, out, isRtl);
  }

  /**
   * Computes streamed slice bounds for partial line rendering.
   */
  public void computeStreamedSliceBounds(String lineText, int globalLine, int lineLength, int[] out, boolean isRtl) {
    if (out == null || out.length < 2) return;
    int safeLength = Math.max(0, lineLength);
    if (safeLength == 0) {
      out[0] = 0;
      out[1] = 0;
      return;
    }

    float avg =
        Math.max(
            1f,
            editor.textRender.getAverageCharWidthForLine(lineText, globalLine));
    int padding = 256;
    float scrollX = Math.max(0f, editor.scroll.getEffectiveScrollX());
    int viewportWidth = Math.max(1, editor.getWidth());

    int start = (int) (scrollX / avg) - padding;
    int end = (int) ((scrollX + viewportWidth) / avg) + padding;

    if (isRtl) {
      start = Math.max(0, start);
      end = Math.max(start, end);
    }

    start = Math.max(0, Math.min(start, safeLength));
    end = Math.max(start, Math.min(end, safeLength));

    out[0] = start;
    out[1] = end;
  }

  /**
   * Gets the initial streamed slice size.
   */
  public int getInitialStreamedSliceSize() {
    float avg = Math.max(1f, editor.textRender.paint.measureText("m"));
    int width = Math.max(1, editor.getWidth());
    int visibleCols = (int) Math.ceil(width / avg);
    return Math.max(2048, visibleCols + 512);
  }
}
