package com.yn.sodiumeditor.view;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import androidx.annotation.Nullable;

final class LineNumberManager {
  private final SodiumEditorView view;
  private final char[] lineNumberChars = new char[16];

  LineNumberManager(SodiumEditorView view) {
    this.view = view;
  }
  boolean lineNumberSelectionEnabled = true;
  float lineNumbersGutterWidth = 0f;
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

  boolean shouldUseLineNumberCache() {
    return view.isShowLineNumbersForLineNumbers()
        && lineNumbersGutterWidth > 0f
        && view.getHeight() > 0;
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

  void drawLineNumbersCachedUnwrapped(
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
    if (view.isCodeFoldingEnabledForLineNumbers()) {
      int visibleCount = view.getVisibleLineCountForLineNumbers();
      if (visibleCount > 0) {
        drawLastIndex = Math.min(lastVisibleIndex + 1, visibleCount - 1);
      }
    } else {
      int total = view.getLinesCountForLineNumbers();
      if (total > 0) {
        drawLastLine = Math.min(lastVisibleLine + 1, total - 1);
      }
    }

    int gutterWidth = Math.max(1, Math.round(lineNumbersGutterWidth));
    float lineHeight = view.getLineHeightForLineNumbers();
    float padPx = lineHeight;
    int height = view.getHeight() + Math.round(padPx * 2f);
    float baseScrollY =
        (float) Math.floor(view.getScrollYForLineNumbers() / lineHeight) * lineHeight - padPx;

    boolean needsRebuild =
        lineNumberCacheBitmap == null
            || lineNumberCacheWidth != gutterWidth
            || lineNumberCacheHeight != height
            || lineNumberCacheFirstIndex != firstVisibleIndex
            || lineNumberCacheLastIndex != drawLastIndex
            || Math.abs(lineNumberCacheBaseScrollY - baseScrollY) > 0.1f
            || lineNumberCacheTextSize != lineNumbersPaint.getTextSize()
            || lineNumberCacheTypeface != lineNumbersPaint.getTypeface()
            || lineNumberCacheRtl != view.isRtlForLineNumbers()
            || lineNumberCacheWrapped
            || lineNumberCacheCodeFolding != view.isCodeFoldingEnabledForLineNumbers()
            || Math.abs(lineNumberCacheGutterWidth - lineNumbersGutterWidth) > 0.1f
            || Math.abs(lineNumberCacheFoldMarkerWidth - view.getFoldMarkerGutterWidthForLineNumbers())
                > 0.1f
            || Math.abs(lineNumberCacheLineHeight - lineHeight) > 0.1f
            || lineNumberCacheColor != lineNumbersPaint.getColor();

    if (needsRebuild) {
      ensureLineNumberCacheBitmap(gutterWidth, height);
      lineNumberCacheBitmap.eraseColor(0);

      float lineNumX =
          view.isRtlForLineNumbers()
              ? view.getGutterStartXForLineNumbers()
                  + view.getGutterTextPaddingForLineNumbers()
                  + (view.isCodeFoldingEnabledForLineNumbers()
                      ? view.getFoldMarkerGutterWidthForLineNumbers()
                      : 0f)
              : view.getGutterStartXForLineNumbers()
                  + lineNumbersGutterWidth
                  - (view.isCodeFoldingEnabledForLineNumbers()
                      ? view.getFoldMarkerGutterWidthForLineNumbers()
                      : 0f)
                  - view.getGutterTextPaddingForLineNumbers();
      float lineNumXLocal = lineNumX - view.getGutterStartXForLineNumbers();

      if (view.isCodeFoldingEnabledForLineNumbers()) {
        for (int v = firstVisibleIndex; v <= drawLastIndex; v++) {
          int i = view.mapVisibleIndexToGlobalForLineNumbers(v);
          int start = writeIntToChars(i + 1, lineNumberChars);
          int count = lineNumberChars.length - start;
          float y =
              Math.round(
                  v * lineHeight - baseScrollY + lineHeight - view.getTextPaintDescentForLineNumbers());
          lineNumberCacheCanvas.drawText(
              lineNumberChars, start, count, lineNumXLocal, y, lineNumbersPaint);
        }
      } else {
        for (int i = firstVisibleLine; i <= drawLastLine; i++) {
          int start = writeIntToChars(i + 1, lineNumberChars);
          int count = lineNumberChars.length - start;
          float y =
              Math.round(
                  i * lineHeight - baseScrollY + lineHeight - view.getTextPaintDescentForLineNumbers());
          lineNumberCacheCanvas.drawText(
              lineNumberChars, start, count, lineNumXLocal, y, lineNumbersPaint);
        }
      }

      lineNumberCacheFirstIndex = firstVisibleIndex;
      lineNumberCacheLastIndex = drawLastIndex;
      lineNumberCacheBaseScrollY = baseScrollY;
      lineNumberCacheTextSize = lineNumbersPaint.getTextSize();
      lineNumberCacheTypeface = lineNumbersPaint.getTypeface();
      lineNumberCacheRtl = view.isRtlForLineNumbers();
      lineNumberCacheWrapped = false;
      lineNumberCacheCodeFolding = view.isCodeFoldingEnabledForLineNumbers();
      lineNumberCacheGutterWidth = lineNumbersGutterWidth;
      lineNumberCacheFoldMarkerWidth = view.getFoldMarkerGutterWidthForLineNumbers();
      lineNumberCacheLineHeight = lineHeight;
      lineNumberCacheColor = lineNumbersPaint.getColor();
    }

    float offsetY = lineNumberCacheBaseScrollY - view.getScrollYForLineNumbers();
    canvas.drawBitmap(lineNumberCacheBitmap, view.getGutterStartXForLineNumbers(), offsetY, null);
    drawCurrentLineNumberUnwrapped(canvas, firstVisibleIndex, lastVisibleIndex);
  }

  void drawLineNumbersCachedWrapped(Canvas canvas, int firstVisualIndex, int lastVisualIndex) {
    if (!shouldUseLineNumberCache()) {
      drawLineNumbersDirectWrapped(canvas, firstVisualIndex, lastVisualIndex);
      return;
    }

    int drawLastIndex = lastVisualIndex;
    int totalVisual = view.getTotalVisualLineCountForLineNumbers();
    if (totalVisual > 0) {
      drawLastIndex = Math.min(lastVisualIndex + 1, totalVisual - 1);
    }

    int gutterWidth = Math.max(1, Math.round(lineNumbersGutterWidth));
    float lineHeight = view.getLineHeightForLineNumbers();
    float padPx = lineHeight;
    int height = view.getHeight() + Math.round(padPx * 2f);
    float baseScrollY =
        (float) Math.floor(view.getScrollYForLineNumbers() / lineHeight) * lineHeight - padPx;

    boolean needsRebuild =
        lineNumberCacheBitmap == null
            || lineNumberCacheWidth != gutterWidth
            || lineNumberCacheHeight != height
            || lineNumberCacheFirstIndex != firstVisualIndex
            || lineNumberCacheLastIndex != drawLastIndex
            || Math.abs(lineNumberCacheBaseScrollY - baseScrollY) > 0.1f
            || lineNumberCacheTextSize != lineNumbersPaint.getTextSize()
            || lineNumberCacheTypeface != lineNumbersPaint.getTypeface()
            || lineNumberCacheRtl != view.isRtlForLineNumbers()
            || !lineNumberCacheWrapped
            || lineNumberCacheCodeFolding != view.isCodeFoldingEnabledForLineNumbers()
            || Math.abs(lineNumberCacheGutterWidth - lineNumbersGutterWidth) > 0.1f
            || Math.abs(lineNumberCacheLineHeight - lineHeight) > 0.1f
            || lineNumberCacheColor != lineNumbersPaint.getColor();

    if (needsRebuild) {
      ensureLineNumberCacheBitmap(gutterWidth, height);
      lineNumberCacheBitmap.eraseColor(0);

      float lineNumX =
          view.isRtlForLineNumbers()
              ? view.getGutterStartXForLineNumbers() + view.getGutterTextPaddingForLineNumbers()
              : view.getGutterStartXForLineNumbers()
                  + lineNumbersGutterWidth
                  - view.getGutterTextPaddingForLineNumbers();
      float lineNumXLocal = lineNumX - view.getGutterStartXForLineNumbers();

      for (int v = firstVisualIndex; v <= drawLastIndex; v++) {
        SodiumEditorView.VisualLinePosition pos = view.getVisualPositionForIndexForLineNumbers(v);
        if (pos.segment != 0) continue;
        int start = writeIntToChars(pos.line + 1, lineNumberChars);
        int count = lineNumberChars.length - start;
        float y =
            Math.round(
                v * lineHeight - baseScrollY + lineHeight - view.getTextPaintDescentForLineNumbers());
        lineNumberCacheCanvas.drawText(
            lineNumberChars, start, count, lineNumXLocal, y, lineNumbersPaint);
      }

      lineNumberCacheFirstIndex = firstVisualIndex;
      lineNumberCacheLastIndex = drawLastIndex;
      lineNumberCacheBaseScrollY = baseScrollY;
      lineNumberCacheTextSize = lineNumbersPaint.getTextSize();
      lineNumberCacheTypeface = lineNumbersPaint.getTypeface();
      lineNumberCacheRtl = view.isRtlForLineNumbers();
      lineNumberCacheWrapped = true;
      lineNumberCacheCodeFolding = view.isCodeFoldingEnabledForLineNumbers();
      lineNumberCacheGutterWidth = lineNumbersGutterWidth;
      lineNumberCacheFoldMarkerWidth = view.getFoldMarkerGutterWidthForLineNumbers();
      lineNumberCacheLineHeight = lineHeight;
      lineNumberCacheColor = lineNumbersPaint.getColor();
    }

    float offsetY = lineNumberCacheBaseScrollY - view.getScrollYForLineNumbers();
    canvas.drawBitmap(lineNumberCacheBitmap, view.getGutterStartXForLineNumbers(), offsetY, null);
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
    if (view.isCodeFoldingEnabledForLineNumbers()) {
      int visibleCount = view.getVisibleLineCountForLineNumbers();
      if (visibleCount > 0) drawLastIndex = Math.min(lastVisibleIndex + 1, visibleCount - 1);
    } else {
      int total = view.getLinesCountForLineNumbers();
      if (total > 0) drawLastLine = Math.min(lastVisibleLine + 1, total - 1);
    }

    float lineHeight = view.getLineHeightForLineNumbers();
    float lineNumX =
        view.isRtlForLineNumbers()
            ? view.getGutterStartXForLineNumbers()
                + view.getGutterTextPaddingForLineNumbers()
                + (view.isCodeFoldingEnabledForLineNumbers()
                    ? view.getFoldMarkerGutterWidthForLineNumbers()
                    : 0f)
            : view.getGutterStartXForLineNumbers()
                + lineNumbersGutterWidth
                - (view.isCodeFoldingEnabledForLineNumbers()
                    ? view.getFoldMarkerGutterWidthForLineNumbers()
                    : 0f)
                - view.getGutterTextPaddingForLineNumbers();

    if (view.isCodeFoldingEnabledForLineNumbers()) {
      for (int v = firstVisibleIndex; v <= drawLastIndex; v++) {
        int i = view.mapVisibleIndexToGlobalForLineNumbers(v);
        int start = writeIntToChars(i + 1, lineNumberChars);
        int count = lineNumberChars.length - start;
        float y =
            Math.round(
                v * lineHeight
                    - view.getScrollYForLineNumbers()
                    + lineHeight
                    - view.getTextPaintDescentForLineNumbers());
        if (i == view.getCursorLineForLineNumbers()) {
          int originalColor = lineNumbersPaint.getColor();
          lineNumbersPaint.setColor(view.getCurrentLineNumberColorForLineNumbers());
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
                    - view.getScrollYForLineNumbers()
                    + lineHeight
                    - view.getTextPaintDescentForLineNumbers());
        if (i == view.getCursorLineForLineNumbers()) {
          int originalColor = lineNumbersPaint.getColor();
          lineNumbersPaint.setColor(view.getCurrentLineNumberColorForLineNumbers());
          canvas.drawText(lineNumberChars, start, count, lineNumX, y, lineNumbersPaint);
          lineNumbersPaint.setColor(originalColor);
        } else {
          canvas.drawText(lineNumberChars, start, count, lineNumX, y, lineNumbersPaint);
        }
      }
    }
  }

  void drawLineNumbersDirectWrapped(Canvas canvas, int firstVisualIndex, int lastVisualIndex) {
    float lineHeight = view.getLineHeightForLineNumbers();
    float lineNumX =
        view.isRtlForLineNumbers()
            ? view.getGutterStartXForLineNumbers() + view.getGutterTextPaddingForLineNumbers()
            : view.getGutterStartXForLineNumbers()
                + lineNumbersGutterWidth
                - view.getGutterTextPaddingForLineNumbers();

    int drawLastIndex = lastVisualIndex;
    int totalVisual = view.getTotalVisualLineCountForLineNumbers();
    if (totalVisual > 0) drawLastIndex = Math.min(lastVisualIndex + 1, totalVisual - 1);

    for (int v = firstVisualIndex; v <= drawLastIndex; v++) {
      SodiumEditorView.VisualLinePosition pos = view.getVisualPositionForIndexForLineNumbers(v);
      if (pos.segment != 0) continue;
      int start = writeIntToChars(pos.line + 1, lineNumberChars);
      int count = lineNumberChars.length - start;
      float y =
          Math.round(
              v * lineHeight
                  - view.getScrollYForLineNumbers()
                  + lineHeight
                  - view.getTextPaintDescentForLineNumbers());
      if (pos.line == view.getCursorLineForLineNumbers()) {
        int originalColor = lineNumbersPaint.getColor();
        lineNumbersPaint.setColor(view.getCurrentLineNumberColorForLineNumbers());
        canvas.drawText(lineNumberChars, start, count, lineNumX, y, lineNumbersPaint);
        lineNumbersPaint.setColor(originalColor);
      } else {
        canvas.drawText(lineNumberChars, start, count, lineNumX, y, lineNumbersPaint);
      }
    }
  }

  void drawCurrentLineNumberUnwrapped(Canvas canvas, int firstVisibleIndex, int lastVisibleIndex) {
    if (!view.isShowLineNumbersForLineNumbers()) return;
    if (view.isCodeFoldingEnabledForLineNumbers()
        && view.isLineHiddenByFoldForLineNumbers(view.getCursorLineForLineNumbers())) return;

    int cursorLine = view.getCursorLineForLineNumbers();
    int visibleIndex =
        view.isCodeFoldingEnabledForLineNumbers()
            ? view.getVisibleIndexForGlobalLineForLineNumbers(cursorLine)
            : cursorLine;
    if (visibleIndex < firstVisibleIndex || visibleIndex > lastVisibleIndex) return;

    float lineHeight = view.getLineHeightForLineNumbers();
    float lineNumX =
        view.isRtlForLineNumbers()
            ? view.getGutterStartXForLineNumbers()
                + view.getGutterTextPaddingForLineNumbers()
                + (view.isCodeFoldingEnabledForLineNumbers()
                    ? view.getFoldMarkerGutterWidthForLineNumbers()
                    : 0f)
            : view.getGutterStartXForLineNumbers()
                + lineNumbersGutterWidth
                - (view.isCodeFoldingEnabledForLineNumbers()
                    ? view.getFoldMarkerGutterWidthForLineNumbers()
                    : 0f)
                - view.getGutterTextPaddingForLineNumbers();
    int start = writeIntToChars(cursorLine + 1, lineNumberChars);
    int count = lineNumberChars.length - start;
    float y =
        Math.round(
            visibleIndex * lineHeight
                - view.getScrollYForLineNumbers()
                + lineHeight
                - view.getTextPaintDescentForLineNumbers());
    int originalColor = lineNumbersPaint.getColor();
    lineNumbersPaint.setColor(view.getCurrentLineNumberColorForLineNumbers());
    canvas.drawText(lineNumberChars, start, count, lineNumX, y, lineNumbersPaint);
    lineNumbersPaint.setColor(originalColor);
  }

  void drawCurrentLineNumberWrapped(Canvas canvas, int firstVisualIndex, int lastVisualIndex) {
    if (!view.isShowLineNumbersForLineNumbers()) return;
    int visualIndex =
        view.getVisualIndexForLineAndCharForLineNumbers(view.getCursorLineForLineNumbers(), 0);
    if (visualIndex < firstVisualIndex || visualIndex > lastVisualIndex) return;

    float lineHeight = view.getLineHeightForLineNumbers();
    float lineNumX =
        view.isRtlForLineNumbers()
            ? view.getGutterStartXForLineNumbers() + view.getGutterTextPaddingForLineNumbers()
            : view.getGutterStartXForLineNumbers()
                + lineNumbersGutterWidth
                - view.getGutterTextPaddingForLineNumbers();
    int start = writeIntToChars(view.getCursorLineForLineNumbers() + 1, lineNumberChars);
    int count = lineNumberChars.length - start;
    float y =
        Math.round(
            visualIndex * lineHeight
                - view.getScrollYForLineNumbers()
                + lineHeight
                - view.getTextPaintDescentForLineNumbers());
    int originalColor = lineNumbersPaint.getColor();
    lineNumbersPaint.setColor(view.getCurrentLineNumberColorForLineNumbers());
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

  void drawLineNumber(Canvas canvas, int line, float x, float y, int currentLineColor, boolean isCurrentLine) {
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

  void invalidateCache() {
    lineNumberCacheBitmap = null;
    lineNumberCacheCanvas = null;
  }
}
