package com.yn.sodiumeditor.core.view;

public class ViewMetrics {
  private final EditorView view;

  ViewMetrics(EditorView view) {
    this.view = view;
  }

  public int getLinesCount() {
    if (view.editor.fileIO.isFileCleared) {
      return Math.max(
          1, view.editor.windowRender.windowStartLine + view.editor.windowRender.linesWindow.size());
    }
    if (view.editor.binaryRender.binaryFileFeaturePolicyActive
        && view.editor.binaryRender.binaryDocument != null) {
      return view.editor.binaryRender.binaryDocument.getRowCount();
    }
    if (view.editor.fileIO.isIndexReady && view.editor.fileIO.lineOffsets.length > 0) {
      boolean hasEdits = view.editor.windowRender.hasAnyModifiedLines();
      if (!hasEdits && view.editor.editOperators.lineCountDelta == 0) {
        return view.editor.fileIO.lineOffsets.length;
      }
      int count = view.editor.fileIO.lineOffsets.length + view.editor.editOperators.lineCountDelta;
      if (count < 1) count = 1;
      return count;
    }
    if (view.editor.fileIO.isEof)
      return view.editor.windowRender.windowStartLine + view.editor.windowRender.linesWindow.size();
    if (!view.editor.windowRender.linesWindow.isEmpty())
      return view.editor.windowRender.windowStartLine + view.editor.windowRender.linesWindow.size();
    return -1;
  }

  public int getLogicalLineLength(int globalLine, String line) {
    String mod = view.editor.windowRender.getModifiedLine(globalLine);
    if (mod != null) return mod.length();
    int len = (line == null) ? 0 : line.length();
    int longLen = view.editor.windowRender.getStreamedLineLength(globalLine);
    return (longLen > len) ? longLen : len;
  }

  public void computeWidthForLine(int globalIndex, String line) {
    String safe = (line == null) ? "" : line;
    Float oldWidth = null;
    synchronized (view.editor.windowRender.lineWidthCache) {
      oldWidth = view.editor.windowRender.lineWidthCache.get(globalIndex);
    }
    float w;
    int logicalLen = getLogicalLineLength(globalIndex, safe);
    if (logicalLen > view.editor.highlightRender.maxSyntaxLineLength) {
      w = view.editor.textRender.getAverageCharWidthForLine(safe, globalIndex) * logicalLen;
    } else {
      w =
          view.editor.textRender.measureTextWithVisualSpaces(
              safe, 0, safe.length(), view.editor.textRender.paint);
    }
    synchronized (view.editor.windowRender.lineWidthCache) {
      view.editor.windowRender.lineWidthCache.put(globalIndex, w);
    }
    if (w > view.editor.windowRender.currentMaxWindowLineWidth) {
      view.editor.windowRender.currentMaxWindowLineWidth = w;
    }
    if (w > view.editor.windowRender.globalMaxLineWidth) {
      view.editor.windowRender.globalMaxLineWidth = w;
    }
    if (w > view.editor.scroll.maxLineWidthForScroll) {
      view.editor.scroll.maxLineWidthForScroll = w;
    }
    if (oldWidth != null && oldWidth >= view.editor.windowRender.globalMaxLineWidth && w < oldWidth) {
      view.editor.windowRender.recalculateMaxLineWidth();
    }
  }

  public float getWidthForLine(int globalIndex, String line) {
    synchronized (view.editor.windowRender.lineWidthCache) {
      Float v = view.editor.windowRender.lineWidthCache.get(globalIndex);
      if (v != null) return v;
    }
    String safe = (line == null) ? "" : line;
    float w;
    int logicalLen = getLogicalLineLength(globalIndex, safe);
    if (logicalLen > view.editor.highlightRender.maxSyntaxLineLength) {
      w = view.editor.textRender.getAverageCharWidthForLine(safe, globalIndex) * logicalLen;
    } else {
      w =
          view.editor.textRender.measureTextWithVisualSpaces(
              safe, 0, safe.length(), view.editor.textRender.paint);
    }
    synchronized (view.editor.windowRender.lineWidthCache) {
      view.editor.windowRender.lineWidthCache.put(globalIndex, w);
    }
    return w;
  }

  public int getCharIndexForX(String text, float x, int globalLine) {
    if (text == null || text.isEmpty()) return 0;
    if (view.editor.textRender.isRtl) {
      float baseX = view.editor.layout.getRtlLineBaseX(text, globalLine);
      x -= baseX;
      float w =
          view.editor.highlight.measureHighlightedSegmentWidth(
              text, globalLine, 0, getLogicalLineLength(globalLine, text));
      x = w - x;
    }
    if (x <= 0f) return 0;
    if (view.editor.binaryRender.shouldUseBinaryRenderingForLine(globalLine)) {
      int[] spans = view.editor.binaryRender.getBinaryTokenSpans(globalLine);
      float padX =
          view.editor.binaryRender.binaryCaretNotationEnabled
              ? 0f
              : view.editor.binaryRender.binaryTokenPaddingX;

      return view.editor.binaryRender.getCharIndexForXBinary(
          text, 0, text.length(), x, view.editor.textRender.paint, spans, padX);
    }

    int len = getLogicalLineLength(globalLine, text);
    if (len > view.editor.highlightRender.maxSyntaxLineLength) {
      float avg = view.editor.textRender.getAverageCharWidthForLine(text, globalLine);
      if (avg <= 0f) return 0;
      int idx = (int) Math.round(x / avg);
      return Math.max(0, Math.min(idx, len));
    }
    int textLen = text.length();
    if (view.editor.textRender.getVisualSpaceScale() == 1) {
      int count = view.editor.textRender.paint.breakText(text, true, x, null);
      if (count <= 0) return 0;
      if (count >= textLen) return textLen;

      float wPrev = (count > 1) ? view.editor.textRender.paint.measureText(text, 0, count - 1) : 0f;
      float wCount = view.editor.textRender.paint.measureText(text, 0, count);
      float mid = wPrev + (wCount - wPrev) * 0.5f;
      return (x < mid) ? (count - 1) : count;
    }

    if (view.measureWidthBuffer == null || view.measureWidthBuffer.length < textLen) {
      view.measureWidthBuffer = new float[textLen];
    }
    view.editor.textRender.paint.getTextWidths(text, 0, textLen, view.measureWidthBuffer);
    float current = 0f;
    for (int i = 0; i < textLen; i++) {
      float adv =
          view.editor.textRender.getCharAdvanceWidth(
              text.charAt(i), view.measureWidthBuffer[i], view.editor.textRender.paint);
      float mid = current + adv * 0.5f;
      if (x < mid) return i;
      if (x < current + adv) return i + 1;
      current += adv;
    }
    return textLen;
  }

  public long computeByteOffsetInLineUtf8(String lineText, int charIndex) {
    if (lineText == null) return 0L;
    int safe = Math.max(0, Math.min(charIndex, lineText.length()));
    if (safe == 0) return 0L;
    return lineText.substring(0, safe).getBytes(view.editor.fileIO.fileCharset).length;
  }

  public void updateLocalLine(int localIdx, String text) {
    if (localIdx >= 0 && localIdx < view.editor.windowRender.linesWindow.size()) {
      view.editor.windowRender.linesWindow.set(localIdx, text);
      view.editor.wordWrap.onLineContentChanged(view.editor.windowRender.windowStartLine + localIdx, text);
      view.editor.windowRender.clearStreamedLineInfo(view.editor.windowRender.windowStartLine + localIdx);
    }
  }
}
