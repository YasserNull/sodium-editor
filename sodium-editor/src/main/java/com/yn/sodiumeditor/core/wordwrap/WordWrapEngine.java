package com.yn.sodiumeditor.core.wordwrap;

import android.graphics.Paint;
import androidx.annotation.Nullable;
import com.yn.sodiumeditor.SodiumEditor;

/** Core line wrapping helpers for WordWrap. */
public class WordWrapEngine {
  private final SodiumEditor editor;
  private final WordWrap wordWrap;

  public WordWrapEngine(SodiumEditor editor, WordWrap wordWrap) {
    this.editor = editor;
    this.wordWrap = wordWrap;
  }

  public int computeWrapCountForLine(String line, int widthPx) {
    return wordWrap.calculator.computeWrapCountForLine(
        line, widthPx, editor.textRender.paint, true);
  }

  public int getWrapCountForLine(int globalLine, @Nullable String line, int widthPx) {
    return getWrapCountForLine(globalLine, line, widthPx, editor.textRender.paint);
  }

  public int getWrapCountForLine(
      int globalLine, @Nullable String line, int widthPx, Paint paint) {
    return Math.max(
        1,
        wordWrap.calculator.computeWrapCountForLine(
            line, widthPx, paint, paint == editor.textRender.paint));
  }

  public int getDefaultWrapCountForLine(int globalLine) {
    return 1;
  }

  public int getWrapSegmentIndexForChar(int[] starts, int charIndex) {
    if (starts == null || starts.length == 0) return 0;
    int idx = 0;
    for (int i = 0; i < starts.length; i++) {
      if (starts[i] <= charIndex) idx = i;
      else break;
    }
    return idx;
  }

  public int getWrapSegmentStart(int[] starts, int segmentIndex) {
    if (starts == null || starts.length == 0) return 0;
    return starts[Math.min(Math.max(0, segmentIndex), starts.length - 1)];
  }

  public int getWrapSegmentEnd(int[] starts, int segmentIndex, int lineLength) {
    if (starts == null || starts.length == 0) return lineLength;
    int next = segmentIndex + 1;
    return (next >= 0 && next < starts.length) ? starts[next] : lineLength;
  }

  public int clampSegmentEndForWrapIndicator(String line, int segStart, int segEnd) {
    if (!wordWrap.isWordWrapEnabled || line == null) return segEnd;
    int len = line.length();
    return Math.max(segStart, Math.min(segEnd, len));
  }

  public int clampSegmentEndForWrapIndicator(String line, int segStart, int segEnd, int widthPx) {
    if (segEnd <= segStart) return segEnd;
    float reserved =
        wordWrap.indicator.wordWrapIndicatorWidth + (wordWrap.indicator.wordWrapIndicatorPadPx * 2f);
    float available = widthPx - reserved;
    if (available <= 0f) return segStart;
    if (editor.textRender.measureTextWithVisualSpaces(
            line, segStart, segEnd, editor.textRender.paint)
        <= available) return segEnd;
    int end = segEnd;
    while (end > segStart) {
      if (editor.textRender.measureTextWithVisualSpaces(
              line, segStart, --end, editor.textRender.paint)
          <= available) break;
    }
    return end;
  }

  public int getCharIndexForXInRange(
      String text, int globalLine, int start, int end, float x) {
    if (text == null || text.isEmpty()) return 0;
    start = Math.max(0, Math.min(start, text.length()));
    end = Math.max(start, Math.min(end, text.length()));
    if (editor.textRender.isRtl) {
      x =
          editor.highlight.measureHighlightedSegmentWidth(text, globalLine, start, end)
              - (x - editor.layout.getRtlSegmentBaseX(text, globalLine, start, end));
    }
    if (x <= 0f) return start;
    if (editor.binaryRender.isBinarySafeRenderingEnabled()) {
      int[] spans = editor.binaryRender.getBinaryTokenSpans(globalLine);
      if (spans != null && spans.length > 0) {
        return editor.binaryRender.getCharIndexForXBinary(
            text,
            start,
            end,
            x,
            editor.textRender.paint,
            spans,
            editor.binaryRender.binaryCaretNotationEnabled
                ? 0f
                : editor.binaryRender.binaryTokenPaddingX);
      }
    }
    int len = end - start;
    if (len <= 0) return start;
    if (editor.textRender.getVisualSpaceScale() == 1) {
      return Math.min(
          start
              + Math.max(
                  0, editor.textRender.paint.breakText(text, start, end, true, x, null)),
          end);
    }
    if (editor.view.measureWidthBuffer == null || editor.view.measureWidthBuffer.length < len) {
      editor.view.measureWidthBuffer = new float[len];
    }
    editor.textRender.paint.getTextWidths(text, start, end, editor.view.measureWidthBuffer);
    float cur = 0f;
    for (int i = 0; i < len; i++) {
      char c = text.charAt(start + i);
      float advance =
          (c == ' ' || c == '\t')
              ? (c == ' '
                      ? editor.view.measureWidthBuffer[i]
                      : editor.view.measureWidthBuffer[i]
                          * com.yn.sodiumeditor.core.view.View.DEFAULT_TAB_SIZE_SPACES)
                  * editor.textRender.getVisualSpaceScale()
              : editor.view.measureWidthBuffer[i];
      if (x < cur + advance * 0.5f) return start + i;
      if (x < cur + advance) return start + i + 1;
      cur += advance;
    }
    return end;
  }
}
