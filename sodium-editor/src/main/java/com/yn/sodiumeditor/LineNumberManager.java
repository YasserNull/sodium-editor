package com.yn.sodiumeditor;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import androidx.annotation.Nullable;

public final class LineNumberManager {
  private static final float GUTTER_TEXT_PADDING = 20f;
  private final SodiumEditorView view;
  private final char[] lineNumberChars = new char[16];

  LineNumberManager(SodiumEditorView view) {
    this.view = view;
  }
  private boolean showLineNumbers = false;
  private boolean highlightCurrentLineInGutter = true;
  private int currentLineNumberColor = 0xFF2196F3;
  private boolean lineNumberSelectionEnabled = true;
  private float lineNumbersGutterWidth = 0f;
  final Paint lineNumbersPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  final Paint gutterPaint = new Paint();
  final Paint gutterSeparatorPaint = new Paint();

  Bitmap lineNumberCacheBitmap;
  Canvas lineNumberCacheCanvas;
  int lineNumberCacheWidth = 0;
  int lineNumberCacheHeight = 0;
  int lineNumberCacheFirstIndex = -1;
  int lineNumberCacheLastIndex = -1;
  float lineNumberCacheBaseScrollY = 0f;
  float lineNumberCacheTextSize = -1f;
  @Nullable Typeface lineNumberCacheTypeface;
  boolean lineNumberCacheRtl = false;
  boolean lineNumberCacheWrapped = false;
  boolean lineNumberCacheCodeFolding = false;
  float lineNumberCacheGutterWidth = 0f;
  float lineNumberCacheFoldMarkerWidth = 0f;
  float lineNumberCacheLineHeight = 0f;
  int lineNumberCacheColor = 0;

  float gutterSeparatorWidth;

  public boolean isShowLineNumbers() {
    return showLineNumbers;
  }

  public void setShowLineNumbers(boolean show) {
    if (showLineNumbers == show) return;
    showLineNumbers = show;
    view.scrollManager.maxTextStartXForScroll = 0f;
    invalidateCache();
    view.requestLayout();
    if (view.wordWrapManager.isWordWrapEnabled) {
      view.wordWrapManager.invalidateWrapMetrics(view, true);
    }
    view.invalidate();
  }

  public boolean isHighlightCurrentLineInGutter() {
    return highlightCurrentLineInGutter;
  }

  public void setHighlightCurrentLineInGutter(boolean enabled) {
    if (highlightCurrentLineInGutter == enabled) return;
    highlightCurrentLineInGutter = enabled;
    view.invalidate();
  }

  public int getCurrentLineNumberColor() {
    return currentLineNumberColor;
  }

  public void setCurrentLineNumberColor(int color) {
    if (currentLineNumberColor == color) return;
    currentLineNumberColor = color;
    if (showLineNumbers) {
      view.invalidate();
    }
  }

  void initDefaults(Paint basePaint, float density) {
    lineNumbersPaint.setTextAlign(Paint.Align.RIGHT);
    lineNumbersPaint.setColor(0xFF888888);
    lineNumbersPaint.setTextSize(basePaint.getTextSize());
    lineNumbersPaint.setTypeface(basePaint.getTypeface());
    gutterPaint.setColor(0xFFFAFAFA);
    gutterSeparatorWidth = 4 * density;
    gutterSeparatorPaint.setColor(0xFF555555);
  }

  public void setLineNumberColor(int color) {
    lineNumbersPaint.setColor(color);
    lineNumberCacheColor = color;
    invalidateCache();
    if (showLineNumbers) {
      view.invalidate();
    }
  }

  public void setGutterBackgroundColor(int color) {
    gutterPaint.setColor(color);
    if (showLineNumbers) {
      view.invalidate();
    }
  }

  public void setGutterSeparatorColor(int color) {
    gutterSeparatorPaint.setColor(color);
    if (showLineNumbers) {
      view.invalidate();
    }
  }

  public void setGutterSeparatorWidth(float width) {
    float safe = Math.max(0f, width);
    if (gutterSeparatorWidth == safe) return;
    gutterSeparatorWidth = safe;
    view.requestLayout();
    if (view.wordWrapManager.isWordWrapEnabled) {
      view.wordWrapManager.invalidateWrapMetrics(view, true);
    }
    if (showLineNumbers) {
      view.invalidate();
    }
  }

  public float getGutterSeparatorWidth() {
    return gutterSeparatorWidth;
  }

  public float getGutterTextPadding() {
    return GUTTER_TEXT_PADDING;
  }

  void setTextAlign(boolean rtl) {
    lineNumbersPaint.setTextAlign(rtl ? Paint.Align.LEFT : Paint.Align.RIGHT);
  }

  void setTextSize(float sizePx) {
    lineNumbersPaint.setTextSize(sizePx);
  }

  void setTypeface(Typeface typeface) {
    lineNumbersPaint.setTypeface(typeface);
  }

  Paint getLineNumberPaint() {
    return lineNumbersPaint;
  }

  public Paint getGutterPaint() {
    return gutterPaint;
  }

  public Paint getGutterSeparatorPaint() {
    return gutterSeparatorPaint;
  }

  public void drawCurrentLineHighlightInGutter(Canvas canvas, float top, float bottom, Paint paint) {
    if (!showLineNumbers || !highlightCurrentLineInGutter || lineNumbersGutterWidth <= 0f) return;
    float left = view.getGutterStartX();
    float right = left + lineNumbersGutterWidth;
    float sep = gutterSeparatorWidth;
    if (sep > 0f) {
      if (view.isRtl) {
        left = Math.min(right, left + sep);
      } else {
        right = Math.max(left, right - sep);
      }
    }
    if (right <= left) return;
    canvas.drawRect(left, top, right, bottom, paint);
  }

  public boolean isLineNumberSelectionEnabled() {
    return lineNumberSelectionEnabled;
  }

  public void setLineNumberSelectionEnabled(boolean enabled) {
    if (lineNumberSelectionEnabled == enabled) return;
    lineNumberSelectionEnabled = enabled;
    if (!enabled && view.selectionManager.isLineNumberSelecting()) {
      view.selectionManager.setLineNumberSelecting(false, -1);
    }
  }

  float getGutterWidth() {
    return lineNumbersGutterWidth;
  }

  void setGutterWidth(float width) {
    lineNumbersGutterWidth = width;
  }

  float computeGutterWidth(
      int maxLines,
      boolean codeFoldingEnabled,
      float foldMarkerWidth) {
    if (!showLineNumbers) return 0f;
    String maxLineNum = String.valueOf(Math.max(0, maxLines));
    float baseWidth =
        lineNumbersPaint.measureText(maxLineNum) + (GUTTER_TEXT_PADDING * 2f);
    float foldWidth = codeFoldingEnabled ? foldMarkerWidth : 0f;
    return baseWidth + foldWidth + gutterSeparatorWidth;
  }

  float getTextStartX(float paddingLeft, boolean rtl) {
    return rtl ? paddingLeft : paddingLeft + lineNumbersGutterWidth;
  }

  float getTextAvailableWidth(float viewWidth, float paddingLeft) {
    return Math.max(0f, viewWidth - lineNumbersGutterWidth - paddingLeft);
  }

  float getLineNumberViewLeft(float viewWidth, boolean rtl) {
    return rtl ? viewWidth - lineNumbersGutterWidth : 0f;
  }

  boolean isInLineNumberGutter(float x, float startX) {
    if (!showLineNumbers || lineNumbersGutterWidth <= 0f) return false;
    return x >= startX && x <= startX + lineNumbersGutterWidth;
  }

  public float getContentClipLeft(boolean rtl) {
    return rtl ? 0f : lineNumbersGutterWidth;
  }

  public float getContentClipRight(float viewWidth, boolean rtl) {
    return rtl ? viewWidth - lineNumbersGutterWidth : viewWidth;
  }

  public float getContentViewLeft(boolean rtl) {
    return rtl ? 0f : lineNumbersGutterWidth;
  }

  public float getContentViewRight(float viewWidth, boolean rtl) {
    return rtl ? (viewWidth - lineNumbersGutterWidth) : viewWidth;
  }

  public float getSeparatorLeft(float gutterStartX) {
    return gutterStartX + lineNumbersGutterWidth - gutterSeparatorWidth;
  }

  public float getGutterRight(float gutterStartX) {
    return gutterStartX + lineNumbersGutterWidth;
  }

  boolean shouldUseLineNumberCache() {
    return showLineNumbers && lineNumbersGutterWidth > 0f && view.getHeight() > 0;
  }

  void ensureLineNumberCacheBitmap(int width, int height) {
    if (lineNumberCacheBitmap != null
        && lineNumberCacheWidth == width
        && lineNumberCacheHeight == height) {
      return;
    }
    lineNumberCacheBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
    lineNumberCacheCanvas = new Canvas(lineNumberCacheBitmap);
    lineNumberCacheWidth = width;
    lineNumberCacheHeight = height;
  }

  public void drawLineNumbersCachedUnwrapped(
      Canvas canvas,
      int firstVisibleIndex,
      int lastVisibleIndex,
      int firstVisibleLine,
      int lastVisibleLine) {
    if (!shouldUseLineNumberCache()) {
      drawLineNumbersDirectUnwrapped(
          canvas, firstVisibleIndex, lastVisibleIndex, firstVisibleLine, lastVisibleLine);
      return;
    }

    int drawLastIndex = lastVisibleIndex;
    int drawLastLine = lastVisibleLine;
    if (view.foldManager.isCodeFoldingEnabled) {
      int visibleCount = view.getVisibleLineCount();
      if (visibleCount > 0) {
        drawLastIndex = Math.min(lastVisibleIndex + 1, visibleCount - 1);
      }
    } else {
      int total = view.getLinesCount();
      if (total > 0) {
        drawLastLine = Math.min(lastVisibleLine + 1, total - 1);
      }
    }

    int gutterWidth = Math.max(1, Math.round(lineNumbersGutterWidth));
    float lineHeight = view.lineHeight;
    float padPx = lineHeight;
    int height = view.getHeight() + Math.round(padPx * 2f);
    float baseScrollY =
        (float) Math.floor(view.scrollManager.scrollY / lineHeight) * lineHeight - padPx;

    boolean needsRebuild =
        lineNumberCacheBitmap == null
            || lineNumberCacheWidth != gutterWidth
            || lineNumberCacheHeight != height
            || lineNumberCacheFirstIndex != firstVisibleIndex
            || lineNumberCacheLastIndex != drawLastIndex
            || Math.abs(lineNumberCacheBaseScrollY - baseScrollY) > 0.1f
            || lineNumberCacheTextSize != lineNumbersPaint.getTextSize()
            || lineNumberCacheTypeface != lineNumbersPaint.getTypeface()
            || lineNumberCacheRtl != view.isRtl
            || lineNumberCacheWrapped
            || lineNumberCacheCodeFolding != view.foldManager.isCodeFoldingEnabled
            || Math.abs(lineNumberCacheGutterWidth - lineNumbersGutterWidth) > 0.1f
            || Math.abs(lineNumberCacheFoldMarkerWidth - view.foldManager.foldMarkerGutterWidth)
                > 0.1f
            || Math.abs(lineNumberCacheLineHeight - lineHeight) > 0.1f
            || lineNumberCacheColor != lineNumbersPaint.getColor();

    if (needsRebuild) {
      ensureLineNumberCacheBitmap(gutterWidth, height);
      lineNumberCacheBitmap.eraseColor(0);

      float lineNumX =
          view.isRtl
              ? view.getGutterStartX()
                  + GUTTER_TEXT_PADDING
                  + (view.foldManager.isCodeFoldingEnabled ? view.foldManager.foldMarkerGutterWidth : 0f)
              : view.getGutterStartX()
                  + lineNumbersGutterWidth
                  - (view.foldManager.isCodeFoldingEnabled ? view.foldManager.foldMarkerGutterWidth : 0f)
                  - GUTTER_TEXT_PADDING;
      float lineNumXLocal = lineNumX - view.getGutterStartX();

      if (view.foldManager.isCodeFoldingEnabled) {
        for (int v = firstVisibleIndex; v <= drawLastIndex; v++) {
          int i = view.mapVisibleIndexToGlobal(v);
          int start = writeIntToChars(i + 1, lineNumberChars);
          int count = lineNumberChars.length - start;
          float y =
              Math.round(
                  v * lineHeight - baseScrollY + lineHeight - view.paint.descent());
          lineNumberCacheCanvas.drawText(
              lineNumberChars, start, count, lineNumXLocal, y, lineNumbersPaint);
        }
      } else {
        for (int i = firstVisibleLine; i <= drawLastLine; i++) {
          int start = writeIntToChars(i + 1, lineNumberChars);
          int count = lineNumberChars.length - start;
          float y =
              Math.round(
                  i * lineHeight - baseScrollY + lineHeight - view.paint.descent());
          lineNumberCacheCanvas.drawText(
              lineNumberChars, start, count, lineNumXLocal, y, lineNumbersPaint);
        }
      }

      lineNumberCacheFirstIndex = firstVisibleIndex;
      lineNumberCacheLastIndex = drawLastIndex;
      lineNumberCacheBaseScrollY = baseScrollY;
      lineNumberCacheTextSize = lineNumbersPaint.getTextSize();
      lineNumberCacheTypeface = lineNumbersPaint.getTypeface();
      lineNumberCacheRtl = view.isRtl;
      lineNumberCacheWrapped = false;
      lineNumberCacheCodeFolding = view.foldManager.isCodeFoldingEnabled;
      lineNumberCacheGutterWidth = lineNumbersGutterWidth;
      lineNumberCacheFoldMarkerWidth = view.foldManager.foldMarkerGutterWidth;
      lineNumberCacheLineHeight = lineHeight;
      lineNumberCacheColor = lineNumbersPaint.getColor();
    }

    float offsetY = lineNumberCacheBaseScrollY - view.scrollManager.scrollY;
    canvas.drawBitmap(lineNumberCacheBitmap, view.getGutterStartX(), offsetY, null);
    drawCurrentLineNumberUnwrapped(canvas, firstVisibleIndex, lastVisibleIndex);
  }

  public void drawLineNumbersCachedWrapped(Canvas canvas, int firstVisualIndex, int lastVisualIndex) {
    if (!shouldUseLineNumberCache()) {
      drawLineNumbersDirectWrapped(canvas, firstVisualIndex, lastVisualIndex);
      return;
    }

    int drawLastIndex = lastVisualIndex;
    int totalVisual = view.wordWrapManager.getTotalVisualLineCount(view);
    if (totalVisual > 0) {
      drawLastIndex = Math.min(lastVisualIndex + 1, totalVisual - 1);
    }

    int gutterWidth = Math.max(1, Math.round(lineNumbersGutterWidth));
    float lineHeight = view.lineHeight;
    float padPx = lineHeight;
    int height = view.getHeight() + Math.round(padPx * 2f);
    float baseScrollY =
        (float) Math.floor(view.scrollManager.scrollY / lineHeight) * lineHeight - padPx;

    boolean needsRebuild =
        lineNumberCacheBitmap == null
            || lineNumberCacheWidth != gutterWidth
            || lineNumberCacheHeight != height
            || lineNumberCacheFirstIndex != firstVisualIndex
            || lineNumberCacheLastIndex != drawLastIndex
            || Math.abs(lineNumberCacheBaseScrollY - baseScrollY) > 0.1f
            || lineNumberCacheTextSize != lineNumbersPaint.getTextSize()
            || lineNumberCacheTypeface != lineNumbersPaint.getTypeface()
            || lineNumberCacheRtl != view.isRtl
            || !lineNumberCacheWrapped
            || lineNumberCacheCodeFolding != view.foldManager.isCodeFoldingEnabled
            || Math.abs(lineNumberCacheGutterWidth - lineNumbersGutterWidth) > 0.1f
            || Math.abs(lineNumberCacheLineHeight - lineHeight) > 0.1f
            || lineNumberCacheColor != lineNumbersPaint.getColor();

    if (needsRebuild) {
      ensureLineNumberCacheBitmap(gutterWidth, height);
      lineNumberCacheBitmap.eraseColor(0);

      float lineNumX =
          view.isRtl
              ? view.getGutterStartX() + GUTTER_TEXT_PADDING
              : view.getGutterStartX()
                  + lineNumbersGutterWidth
                  - GUTTER_TEXT_PADDING;
      float lineNumXLocal = lineNumX - view.getGutterStartX();

      for (int v = firstVisualIndex; v <= drawLastIndex; v++) {
        SodiumEditorView.VisualLinePosition pos =
            view.wordWrapManager.getVisualPositionForIndex(view, v);
        if (pos.segment != 0) continue;
        int start = writeIntToChars(pos.line + 1, lineNumberChars);
        int count = lineNumberChars.length - start;
        float y =
            Math.round(
                v * lineHeight - baseScrollY + lineHeight - view.paint.descent());
        lineNumberCacheCanvas.drawText(
            lineNumberChars, start, count, lineNumXLocal, y, lineNumbersPaint);
      }

      lineNumberCacheFirstIndex = firstVisualIndex;
      lineNumberCacheLastIndex = drawLastIndex;
      lineNumberCacheBaseScrollY = baseScrollY;
      lineNumberCacheTextSize = lineNumbersPaint.getTextSize();
      lineNumberCacheTypeface = lineNumbersPaint.getTypeface();
      lineNumberCacheRtl = view.isRtl;
      lineNumberCacheWrapped = true;
      lineNumberCacheCodeFolding = view.foldManager.isCodeFoldingEnabled;
      lineNumberCacheGutterWidth = lineNumbersGutterWidth;
      lineNumberCacheFoldMarkerWidth = view.foldManager.foldMarkerGutterWidth;
      lineNumberCacheLineHeight = lineHeight;
      lineNumberCacheColor = lineNumbersPaint.getColor();
    }

    float offsetY = lineNumberCacheBaseScrollY - view.scrollManager.scrollY;
    canvas.drawBitmap(lineNumberCacheBitmap, view.getGutterStartX(), offsetY, null);
    drawCurrentLineNumberWrapped(canvas, firstVisualIndex, lastVisualIndex);
  }

  void drawLineNumbersDirectUnwrapped(
      Canvas canvas,
      int firstVisibleIndex,
      int lastVisibleIndex,
      int firstVisibleLine,
      int lastVisibleLine) {
    int drawLastIndex = lastVisibleIndex;
    int drawLastLine = lastVisibleLine;
    if (view.foldManager.isCodeFoldingEnabled) {
      int visibleCount = view.getVisibleLineCount();
      if (visibleCount > 0) drawLastIndex = Math.min(lastVisibleIndex + 1, visibleCount - 1);
    } else {
      int total = view.getLinesCount();
      if (total > 0) drawLastLine = Math.min(lastVisibleLine + 1, total - 1);
    }

    float lineHeight = view.lineHeight;
    float lineNumX =
        view.isRtl
            ? view.getGutterStartX()
                + GUTTER_TEXT_PADDING
                + (view.foldManager.isCodeFoldingEnabled ? view.foldManager.foldMarkerGutterWidth : 0f)
            : view.getGutterStartX()
                + lineNumbersGutterWidth
                - (view.foldManager.isCodeFoldingEnabled ? view.foldManager.foldMarkerGutterWidth : 0f)
                - GUTTER_TEXT_PADDING;

    if (view.foldManager.isCodeFoldingEnabled) {
      for (int v = firstVisibleIndex; v <= drawLastIndex; v++) {
        int i = view.mapVisibleIndexToGlobal(v);
        int start = writeIntToChars(i + 1, lineNumberChars);
        int count = lineNumberChars.length - start;
        float y =
            Math.round(
                v * lineHeight
                    - view.scrollManager.scrollY
                    + lineHeight
                    - view.paint.descent());
        if (i == view.cursorManager.getLine()) {
          int originalColor = lineNumbersPaint.getColor();
          lineNumbersPaint.setColor(currentLineNumberColor);
          canvas.drawText(lineNumberChars, start, count, lineNumX, y, lineNumbersPaint);
          lineNumbersPaint.setColor(originalColor);
        } else {
          canvas.drawText(lineNumberChars, start, count, lineNumX, y, lineNumbersPaint);
        }
      }
    } else {
      for (int i = firstVisibleLine; i <= drawLastLine; i++) {
        int start = writeIntToChars(i + 1, lineNumberChars);
        int count = lineNumberChars.length - start;
        float y =
            Math.round(
                i * lineHeight
                    - view.scrollManager.scrollY
                    + lineHeight
                    - view.paint.descent());
        if (i == view.cursorManager.getLine()) {
          int originalColor = lineNumbersPaint.getColor();
          lineNumbersPaint.setColor(currentLineNumberColor);
          canvas.drawText(lineNumberChars, start, count, lineNumX, y, lineNumbersPaint);
          lineNumbersPaint.setColor(originalColor);
        } else {
          canvas.drawText(lineNumberChars, start, count, lineNumX, y, lineNumbersPaint);
        }
      }
    }
  }

  void drawLineNumbersDirectWrapped(Canvas canvas, int firstVisualIndex, int lastVisualIndex) {
    float lineHeight = view.lineHeight;
    float lineNumX =
        view.isRtl
            ? view.getGutterStartX() + GUTTER_TEXT_PADDING
            : view.getGutterStartX()
                + lineNumbersGutterWidth
                - GUTTER_TEXT_PADDING;

    int drawLastIndex = lastVisualIndex;
    int totalVisual = view.wordWrapManager.getTotalVisualLineCount(view);
    if (totalVisual > 0) drawLastIndex = Math.min(lastVisualIndex + 1, totalVisual - 1);

    for (int v = firstVisualIndex; v <= drawLastIndex; v++) {
      SodiumEditorView.VisualLinePosition pos =
          view.wordWrapManager.getVisualPositionForIndex(view, v);
      if (pos.segment != 0) continue;
      int start = writeIntToChars(pos.line + 1, lineNumberChars);
      int count = lineNumberChars.length - start;
      float y =
          Math.round(
              v * lineHeight
                  - view.scrollManager.scrollY
                  + lineHeight
                  - view.paint.descent());
      if (pos.line == view.cursorManager.getLine()) {
        int originalColor = lineNumbersPaint.getColor();
        lineNumbersPaint.setColor(currentLineNumberColor);
        canvas.drawText(lineNumberChars, start, count, lineNumX, y, lineNumbersPaint);
        lineNumbersPaint.setColor(originalColor);
      } else {
        canvas.drawText(lineNumberChars, start, count, lineNumX, y, lineNumbersPaint);
      }
    }
  }

  void drawCurrentLineNumberUnwrapped(Canvas canvas, int firstVisibleIndex, int lastVisibleIndex) {
    if (!showLineNumbers) return;
    if (view.foldManager.isCodeFoldingEnabled
        && view.foldManager.isLineHiddenByFold(view.cursorManager.getLine())) return;

    int cursorLine = view.cursorManager.getLine();
    int visibleIndex =
        view.foldManager.isCodeFoldingEnabled
            ? view.getVisibleIndexForGlobalLine(cursorLine)
            : cursorLine;
    if (visibleIndex < firstVisibleIndex || visibleIndex > lastVisibleIndex) return;

    float lineHeight = view.lineHeight;
    float lineNumX =
        view.isRtl
            ? view.getGutterStartX()
                + GUTTER_TEXT_PADDING
                + (view.foldManager.isCodeFoldingEnabled ? view.foldManager.foldMarkerGutterWidth : 0f)
            : view.getGutterStartX()
                + lineNumbersGutterWidth
                - (view.foldManager.isCodeFoldingEnabled ? view.foldManager.foldMarkerGutterWidth : 0f)
                - GUTTER_TEXT_PADDING;
    int start = writeIntToChars(cursorLine + 1, lineNumberChars);
    int count = lineNumberChars.length - start;
    float y =
        Math.round(
            visibleIndex * lineHeight
                - view.scrollManager.scrollY
                + lineHeight
                - view.paint.descent());
    int originalColor = lineNumbersPaint.getColor();
    lineNumbersPaint.setColor(currentLineNumberColor);
    canvas.drawText(lineNumberChars, start, count, lineNumX, y, lineNumbersPaint);
    lineNumbersPaint.setColor(originalColor);
  }

  void drawCurrentLineNumberWrapped(Canvas canvas, int firstVisualIndex, int lastVisualIndex) {
    if (!showLineNumbers) return;
    int visualIndex =
        view.getVisualIndexForLineAndChar(view.cursorManager.getLine(), 0);
    if (visualIndex < firstVisualIndex || visualIndex > lastVisualIndex) return;

    float lineHeight = view.lineHeight;
    float lineNumX =
        view.isRtl
            ? view.getGutterStartX() + GUTTER_TEXT_PADDING
            : view.getGutterStartX()
                + lineNumbersGutterWidth
                - GUTTER_TEXT_PADDING;
    int start = writeIntToChars(view.cursorManager.getLine() + 1, lineNumberChars);
    int count = lineNumberChars.length - start;
    float y =
        Math.round(
            visualIndex * lineHeight
                - view.scrollManager.scrollY
                + lineHeight
                - view.paint.descent());
    int originalColor = lineNumbersPaint.getColor();
    lineNumbersPaint.setColor(currentLineNumberColor);
    canvas.drawText(lineNumberChars, start, count, lineNumX, y, lineNumbersPaint);
    lineNumbersPaint.setColor(originalColor);
  }

  private int writeIntToChars(int value, char[] out) {
    if (value == 0) {
      out[out.length - 1] = '0';
      return out.length - 1;
    }
    int v = value;
    if (v < 0) v = -v;
    int i = out.length;
    while (v > 0 && i > 0) {
      int digit = v % 10;
      v /= 10;
      out[--i] = (char) ('0' + digit);
    }
    if (value < 0 && i > 0) {
      out[--i] = '-';
    }
    return i;
  }

  public void drawLineNumber(Canvas canvas, int line, float x, float y, int currentLineColor, boolean isCurrentLine) {
    int start = writeIntToChars(line + 1, lineNumberChars);
    int count = lineNumberChars.length - start;
    if (isCurrentLine) {
      int originalColor = lineNumbersPaint.getColor();
      lineNumbersPaint.setColor(currentLineColor);
      canvas.drawText(lineNumberChars, start, count, x, y, lineNumbersPaint);
      lineNumbersPaint.setColor(originalColor);
    } else {
      canvas.drawText(lineNumberChars, start, count, x, y, lineNumbersPaint);
    }
  }

  public void invalidateCache() {
    lineNumberCacheBitmap = null;
    lineNumberCacheCanvas = null;
  }
}
