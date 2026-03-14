package com.yn.sodiumeditor;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import androidx.annotation.Nullable;

/**
 * Manages line number display, caching, and drawing for the SodiumEditor.
 */
public class LineNumber {

    // --- Line Number State ---
    public boolean showLineNumbers = true;
    public boolean highlightCurrentLineInGutter = true;
    public boolean lineNumberSelectionEnabled = true;
    public float lineNumbersGutterWidth = 0f;
    public final Paint lineNumbersPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    public final Paint gutterPaint = new Paint();
    public final Paint gutterSeparatorPaint = new Paint();
    public Bitmap lineNumberCacheBitmap;
    public Canvas lineNumberCacheCanvas;
    public int lineNumberCacheWidth = 0;
    public int lineNumberCacheHeight = 0;
    public int lineNumberCacheFirstIndex = -1;
    public int lineNumberCacheLastIndex = -1;
    public float lineNumberCacheBaseScrollY = 0f;
    public float lineNumberCacheTextSize = -1f;
    @Nullable public Typeface lineNumberCacheTypeface;
    public boolean lineNumberCacheRtl = false;
    public boolean lineNumberCacheWrapped = false;
    public boolean lineNumberCacheCodeFolding = false;
    public float lineNumberCacheGutterWidth = 0f;
    public float lineNumberCacheFoldMarkerWidth = 0f;
    public float lineNumberCacheLineHeight = 0f;
    public int lineNumberCacheColor = 0;
    public float gutterSeparatorWidth = 5f;
    public int currentLineNumberColor = 0xFF2196F3;
    public final char[] lineNumberChars = new char[16];
    public static final float GUTTER_TEXT_PADDING = 20f;

    public final SodiumEditor editor;
    public final Paint currentLinePaint = new Paint();

    public LineNumber(SodiumEditor editor) {
        this.editor = editor;
        // Initialization for line numbers
        lineNumbersPaint.setTextAlign(Paint.Align.RIGHT);
        lineNumbersPaint.setColor(0xFF888888); // Default gray color
        lineNumbersPaint.setTextSize(editor.paint.getTextSize());
        lineNumbersPaint.setTypeface(editor.paint.getTypeface());
        gutterPaint.setColor(0xFFFAFAFA); // Default light gray background
        gutterSeparatorPaint.setColor(0xFF555555);
        currentLinePaint.setColor(0x202196F3); // Default: translucent gray
    }

    public void setShowLineNumbers(boolean show) {
        if (this.showLineNumbers == show) return;
        this.showLineNumbers = show;
        invalidateLineNumberCache();
        editor.requestLayout();
    }

    public boolean getShowLineNumbers() {
        return showLineNumbers;
    }

    public void setLineNumberColor(int color) {
        lineNumbersPaint.setColor(color);
        lineNumberCacheColor = color;
        invalidateLineNumberCache();
        if (showLineNumbers) editor.invalidate();
    }

    public int getLineNumberColor() {
        return lineNumbersPaint.getColor();
    }

    public void setCurrentLineGutterHighlightEnabled(boolean enabled) {
        if (highlightCurrentLineInGutter == enabled) return;
        highlightCurrentLineInGutter = enabled;
        if (showLineNumbers) editor.invalidate();
    }

    public boolean isCurrentLineGutterHighlightEnabled() {
        return highlightCurrentLineInGutter;
    }

    public void setLineNumberSelectionEnabled(boolean enabled) {
        if (lineNumberSelectionEnabled == enabled) return;
        lineNumberSelectionEnabled = enabled;
    }

    public boolean isLineNumberSelectionEnabled() {
        return lineNumberSelectionEnabled;
    }

    public void setGutterBackgroundColor(int color) {
        gutterPaint.setColor(color);
        if (showLineNumbers) editor.invalidate();
    }

    public int getGutterBackgroundColor() {
        return gutterPaint.getColor();
    }

    public void setGutterSeparatorColor(int color) {
        gutterSeparatorPaint.setColor(color);
        if (showLineNumbers) {
            editor.invalidate();
        }
    }

    public int getGutterSeparatorColor() {
        return gutterSeparatorPaint.getColor();
    }

    public void setGutterSeparatorWidth(float width) {
        float safe = Math.max(0f, width);
        if (gutterSeparatorWidth == safe) return;
        gutterSeparatorWidth = safe;
        editor.requestLayout();
        if (showLineNumbers) editor.invalidate();
    }

    public float getGutterSeparatorWidth() {
        return gutterSeparatorWidth;
    }

    public void setCurrentLineNumberColor(int color) {
        if (this.currentLineNumberColor == color) return;
        this.currentLineNumberColor = color;
        if (showLineNumbers) editor.invalidate();
    }

    public int getCurrentLineNumberColor() {
        return currentLineNumberColor;
    }

    public void setLineNumberTextSize(float sizePx) {
        lineNumbersPaint.setTextSize(sizePx);
        invalidateLineNumberCache();
        editor.requestLayout();
        if (showLineNumbers) editor.invalidate();
    }

    public float getLineNumberTextSize() {
        return lineNumbersPaint.getTextSize();
    }

    public void setLineNumberTypeface(@Nullable Typeface typeface) {
        Typeface finalTypeface = (typeface != null) ? typeface : editor.baseTypeface;
        lineNumbersPaint.setTypeface(finalTypeface);
        invalidateLineNumberCache();
        editor.requestLayout();
        if (showLineNumbers) editor.invalidate();
    }

    @Nullable
    public Typeface getLineNumberTypeface() {
        return lineNumbersPaint.getTypeface();
    }

    public void invalidateLineNumberCache() {
        lineNumberCacheBitmap = null;
        lineNumberCacheCanvas = null;
    }

    public float getGutterStartX() {
        return editor.isRtl ? editor.getWidth() - lineNumbersGutterWidth : 0;
    }

    public boolean isInLineNumberGutter(float x) {
        if (!showLineNumbers || lineNumbersGutterWidth <= 0f) return false;
        float start = getGutterStartX();
        return x >= start && x <= start + lineNumbersGutterWidth;
    }

    public void beginLineNumberSelection(int line) {
        int total = editor.getLinesCount();
        if (total <= 0) return;
        int clamped = Math.max(0, Math.min(line, total - 1));
        editor.selection.isLineNumberSelecting = true;
        editor.selection.lineNumberSelectAnchorLine = clamped;
        String lineText = editor.getLineTextForRender(clamped);
        if (lineText != null) {
            editor.setSelectionRange(clamped, 0, clamped, lineText.length());
        }
    }

    public void updateLineNumberSelection(int line) {
        if (!editor.selection.isLineNumberSelecting) return;
        int total = editor.getLinesCount();
        if (total <= 0) return;
        int clamped = Math.max(0, Math.min(line, total - 1));
        int startLine = Math.min(editor.selection.lineNumberSelectAnchorLine, clamped);
        int endLine = Math.max(editor.selection.lineNumberSelectAnchorLine, clamped);
        String endLineText = editor.getLineTextForRender(endLine);
        if (endLineText != null) {
            editor.setSelectionRange(startLine, 0, endLine, endLineText.length());
        }
    }

    public void endLineNumberSelection() {
        if (editor.selection.isLineNumberSelecting) {
            editor.selection.isLineNumberSelecting = false;
            editor.selection.lineNumberSelectAnchorLine = -1;
        }
    }

    public void updateGutterWidth() {
        float oldGutterWidth = lineNumbersGutterWidth;
        if (showLineNumbers) {
            int total = editor.getLinesCount();
            int maxLine = Math.max(1, total);
            String maxLineNum = String.valueOf(maxLine);
            float baseWidth = lineNumbersPaint.measureText(maxLineNum) + (GUTTER_TEXT_PADDING * 2);
            float foldMarkerGutterWidth;
            if (editor.isCodeFoldingEnabled) {
                foldMarkerGutterWidth =
                        editor.foldMarkerPaint.measureText("v")
                                + editor.foldMarkerSpacing
                                + editor.foldMarkerEdgePadding;
            } else {
                foldMarkerGutterWidth = 0f;
            }
            lineNumbersGutterWidth = baseWidth + foldMarkerGutterWidth + gutterSeparatorWidth;
        } else {
            lineNumbersGutterWidth = 0f;
        }

        if (editor.isWordWrapEnabled && Math.abs(lineNumbersGutterWidth - oldGutterWidth) > 0.1f) {
            editor.invalidateWrapMetrics(true);
            editor.requestWrapPrefixRebuild();
        }

        if (Math.abs(lineNumbersGutterWidth - oldGutterWidth) > 0.1f) {
            invalidateLineNumberCache();
            editor.requestLayout();
            editor.invalidate();
        }
    }

    public void drawCurrentLineHighlightInGutter(Canvas canvas, float top, float bottom) {
        if (!showLineNumbers || !highlightCurrentLineInGutter || lineNumbersGutterWidth <= 0f) return;
        float left = getGutterStartX();
        float right = left + lineNumbersGutterWidth;
        float sep = gutterSeparatorWidth;
        if (sep > 0f) {
            if (editor.isRtl) {
                left = Math.min(right, left + sep);
            } else {
                right = Math.max(left, right - sep);
            }
        }
        if (right <= left) return;
        canvas.drawRect(left, top, right, bottom, currentLinePaint);
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
        if (editor.isCodeFoldingEnabled) {
            int visibleCount = editor.getVisibleLineCount();
            if (visibleCount > 0) {
                drawLastIndex = Math.min(lastVisibleIndex + 1, visibleCount - 1);
            }
        } else {
            int total = editor.getLinesCount();
            if (total > 0) {
                drawLastLine = Math.min(lastVisibleLine + 1, total - 1);
            }
        }

        int gutterWidth = Math.max(1, Math.round(lineNumbersGutterWidth));
        float padPx = editor.lineHeight;
        int height = editor.getHeight() + Math.round(padPx * 2f);
        float baseScrollY = (float) Math.floor(editor.scroll.scrollY / editor.lineHeight) * editor.lineHeight - padPx;

        boolean needsRebuild =
                lineNumberCacheBitmap == null
                        || lineNumberCacheWidth != gutterWidth
                        || lineNumberCacheHeight != height
                        || lineNumberCacheFirstIndex != firstVisibleIndex
                        || lineNumberCacheLastIndex != drawLastIndex
                        || Math.abs(lineNumberCacheBaseScrollY - baseScrollY) > 0.1f
                        || lineNumberCacheTextSize != lineNumbersPaint.getTextSize()
                        || lineNumberCacheTypeface != lineNumbersPaint.getTypeface()
                        || lineNumberCacheRtl != editor.isRtl
                        || lineNumberCacheWrapped
                        || lineNumberCacheCodeFolding != editor.isCodeFoldingEnabled
                        || Math.abs(lineNumberCacheGutterWidth - lineNumbersGutterWidth) > 0.1f
                        || Math.abs(lineNumberCacheFoldMarkerWidth - editor.foldMarkerGutterWidth) > 0.1f
                        || Math.abs(lineNumberCacheLineHeight - editor.lineHeight) > 0.1f
                        || lineNumberCacheColor != lineNumbersPaint.getColor();

        if (needsRebuild) {
            ensureLineNumberCacheBitmap(gutterWidth, height);
            lineNumberCacheBitmap.eraseColor(0);

            float lineNumX =
                    editor.isRtl
                            ? getGutterStartX()
                            + GUTTER_TEXT_PADDING
                            + (editor.isCodeFoldingEnabled ? editor.foldMarkerGutterWidth : 0f)
                            : getGutterStartX()
                            + lineNumbersGutterWidth
                            - (editor.isCodeFoldingEnabled ? editor.foldMarkerGutterWidth : 0f)
                            - GUTTER_TEXT_PADDING;
            float lineNumXLocal = lineNumX - getGutterStartX();

            if (editor.isCodeFoldingEnabled) {
                for (int v = firstVisibleIndex; v <= drawLastIndex; v++) {
                    int i = editor.mapVisibleIndexToGlobal(v);
                    int start = writeIntToChars(i + 1, lineNumberChars);
                    int count = lineNumberChars.length - start;
                    float y = Math.round(v * editor.lineHeight - baseScrollY + editor.lineHeight - editor.paint.descent());
                    lineNumberCacheCanvas.drawText(
                            lineNumberChars, start, count, lineNumXLocal, y, lineNumbersPaint);
                }
            } else {
                for (int i = firstVisibleLine; i <= drawLastLine; i++) {
                    int start = writeIntToChars(i + 1, lineNumberChars);
                    int count = lineNumberChars.length - start;
                    float y = Math.round(i * editor.lineHeight - baseScrollY + editor.lineHeight - editor.paint.descent());
                    lineNumberCacheCanvas.drawText(
                            lineNumberChars, start, count, lineNumXLocal, y, lineNumbersPaint);
                }
            }

            lineNumberCacheFirstIndex = firstVisibleIndex;
            lineNumberCacheLastIndex = drawLastIndex;
            lineNumberCacheBaseScrollY = baseScrollY;
            lineNumberCacheTextSize = lineNumbersPaint.getTextSize();
            lineNumberCacheTypeface = lineNumbersPaint.getTypeface();
            lineNumberCacheRtl = editor.isRtl;
            lineNumberCacheWrapped = false;
            lineNumberCacheCodeFolding = editor.isCodeFoldingEnabled;
            lineNumberCacheGutterWidth = lineNumbersGutterWidth;
            lineNumberCacheFoldMarkerWidth = editor.foldMarkerGutterWidth;
            lineNumberCacheLineHeight = editor.lineHeight;
            lineNumberCacheColor = lineNumbersPaint.getColor();
        }

        float offsetY = lineNumberCacheBaseScrollY - editor.scroll.scrollY;
        canvas.drawBitmap(lineNumberCacheBitmap, getGutterStartX(), offsetY, null);
        drawCurrentLineNumberUnwrapped(canvas, firstVisibleIndex, lastVisibleIndex);
    }

    public void drawLineNumbersCachedWrapped(
            Canvas canvas, int firstVisualIndex, int lastVisualIndex) {
        if (!shouldUseLineNumberCache()) {
            drawLineNumbersDirectWrapped(canvas, firstVisualIndex, lastVisualIndex);
            return;
        }

        int drawLastIndex = lastVisualIndex;
        int totalVisual = editor.getTotalVisualLineCount();
        if (totalVisual > 0) {
            drawLastIndex = Math.min(lastVisualIndex + 1, totalVisual - 1);
        }

        int gutterWidth = Math.max(1, Math.round(lineNumbersGutterWidth));
        float padPx = editor.lineHeight;
        int height = editor.getHeight() + Math.round(padPx * 2f);
        float baseScrollY = (float) Math.floor(editor.scroll.scrollY / editor.lineHeight) * editor.lineHeight - padPx;

        boolean needsRebuild =
                lineNumberCacheBitmap == null
                        || lineNumberCacheWidth != gutterWidth
                        || lineNumberCacheHeight != height
                        || lineNumberCacheFirstIndex != firstVisualIndex
                        || lineNumberCacheLastIndex != drawLastIndex
                        || Math.abs(lineNumberCacheBaseScrollY - baseScrollY) > 0.1f
                        || lineNumberCacheTextSize != lineNumbersPaint.getTextSize()
                        || lineNumberCacheTypeface != lineNumbersPaint.getTypeface()
                        || lineNumberCacheRtl != editor.isRtl
                        || !lineNumberCacheWrapped
                        || lineNumberCacheCodeFolding != editor.isCodeFoldingEnabled
                        || Math.abs(lineNumberCacheGutterWidth - lineNumbersGutterWidth) > 0.1f
                        || Math.abs(lineNumberCacheLineHeight - editor.lineHeight) > 0.1f
                        || lineNumberCacheColor != lineNumbersPaint.getColor();

        if (needsRebuild) {
            ensureLineNumberCacheBitmap(gutterWidth, height);
            lineNumberCacheBitmap.eraseColor(0);

            float lineNumX =
                    editor.isRtl
                            ? getGutterStartX() + GUTTER_TEXT_PADDING
                            : getGutterStartX() + lineNumbersGutterWidth - GUTTER_TEXT_PADDING;
            float lineNumXLocal = lineNumX - getGutterStartX();

            for (int v = firstVisualIndex; v <= drawLastIndex; v++) {
                SodiumEditor.VisualLinePosition pos = editor.getVisualPositionForIndex(v);
                if (pos.segment != 0) continue;
                int start = writeIntToChars(pos.line + 1, lineNumberChars);
                int count = lineNumberChars.length - start;
                float y = Math.round(v * editor.lineHeight - baseScrollY + editor.lineHeight - editor.paint.descent());
                lineNumberCacheCanvas.drawText(
                        lineNumberChars, start, count, lineNumXLocal, y, lineNumbersPaint);
            }

            lineNumberCacheFirstIndex = firstVisualIndex;
            lineNumberCacheLastIndex = drawLastIndex;
            lineNumberCacheBaseScrollY = baseScrollY;
            lineNumberCacheTextSize = lineNumbersPaint.getTextSize();
            lineNumberCacheTypeface = lineNumbersPaint.getTypeface();
            lineNumberCacheRtl = editor.isRtl;
            lineNumberCacheWrapped = true;
            lineNumberCacheCodeFolding = editor.isCodeFoldingEnabled;
            lineNumberCacheGutterWidth = lineNumbersGutterWidth;
            lineNumberCacheFoldMarkerWidth = editor.foldMarkerGutterWidth;
            lineNumberCacheLineHeight = editor.lineHeight;
            lineNumberCacheColor = lineNumbersPaint.getColor();
        }

        float offsetY = lineNumberCacheBaseScrollY - editor.scroll.scrollY;
        canvas.drawBitmap(lineNumberCacheBitmap, getGutterStartX(), offsetY, null);
        drawCurrentLineNumberWrapped(canvas, firstVisualIndex, lastVisualIndex);
    }

    public void drawLineNumbersDirectUnwrapped(
            Canvas canvas,
            int firstVisibleIndex,
            int lastVisibleIndex,
            int firstVisibleLine,
            int lastVisibleLine) {
        int drawLastIndex = lastVisibleIndex;
        int drawLastLine = lastVisibleLine;
        if (editor.isCodeFoldingEnabled) {
            int visibleCount = editor.getVisibleLineCount();
            if (visibleCount > 0) drawLastIndex = Math.min(lastVisibleIndex + 1, visibleCount - 1);
        } else {
            int total = editor.getLinesCount();
            if (total > 0) drawLastLine = Math.min(lastVisibleLine + 1, total - 1);
        }

        float lineNumX =
                editor.isRtl
                        ? getGutterStartX()
                        + GUTTER_TEXT_PADDING
                        + (editor.isCodeFoldingEnabled ? editor.foldMarkerGutterWidth : 0f)
                        : getGutterStartX()
                        + lineNumbersGutterWidth
                        - (editor.isCodeFoldingEnabled ? editor.foldMarkerGutterWidth : 0f)
                        - GUTTER_TEXT_PADDING;

        if (editor.isCodeFoldingEnabled) {
            for (int v = firstVisibleIndex; v <= drawLastIndex; v++) {
                int i = editor.mapVisibleIndexToGlobal(v);
                int start = writeIntToChars(i + 1, lineNumberChars);
                int count = lineNumberChars.length - start;
                float y = Math.round(v * editor.lineHeight - editor.scroll.scrollY + editor.lineHeight - editor.paint.descent());
                if (i == editor.cursor.cursorLine) {
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
                float y = Math.round(i * editor.lineHeight - editor.scroll.scrollY + editor.lineHeight - editor.paint.descent());
                if (i == editor.cursor.cursorLine) {
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

    public void drawLineNumbersDirectWrapped(
            Canvas canvas, int firstVisualIndex, int lastVisualIndex) {
        float lineNumX =
                editor.isRtl
                        ? getGutterStartX() + GUTTER_TEXT_PADDING
                        : getGutterStartX() + lineNumbersGutterWidth - GUTTER_TEXT_PADDING;

        int drawLastIndex = lastVisualIndex;
        int totalVisual = editor.getTotalVisualLineCount();
        if (totalVisual > 0) drawLastIndex = Math.min(lastVisualIndex + 1, totalVisual - 1);

        for (int v = firstVisualIndex; v <= drawLastIndex; v++) {
            SodiumEditor.VisualLinePosition pos = editor.getVisualPositionForIndex(v);
            if (pos.segment != 0) continue;
            int start = writeIntToChars(pos.line + 1, lineNumberChars);
            int count = lineNumberChars.length - start;
            float y = Math.round(v * editor.lineHeight - editor.scroll.scrollY + editor.lineHeight - editor.paint.descent());
            if (pos.line == editor.cursor.cursorLine) {
                int originalColor = lineNumbersPaint.getColor();
                lineNumbersPaint.setColor(currentLineNumberColor);
                canvas.drawText(lineNumberChars, start, count, lineNumX, y, lineNumbersPaint);
                lineNumbersPaint.setColor(originalColor);
            } else {
                canvas.drawText(lineNumberChars, start, count, lineNumX, y, lineNumbersPaint);
            }
        }
    }

    public void drawCurrentLineNumberUnwrapped(
            Canvas canvas, int firstVisibleIndex, int lastVisibleIndex) {
        if (!showLineNumbers) return;
        if (editor.isCodeFoldingEnabled && editor.isLineHiddenByFold(editor.cursor.cursorLine)) return;

        int visibleIndex = editor.isCodeFoldingEnabled ? editor.getVisibleIndexForGlobalLine(editor.cursor.cursorLine) : editor.cursor.cursorLine;
        if (visibleIndex < firstVisibleIndex || visibleIndex > lastVisibleIndex) return;

        float lineNumX =
                editor.isRtl
                        ? getGutterStartX()
                        + GUTTER_TEXT_PADDING
                        + (editor.isCodeFoldingEnabled ? editor.foldMarkerGutterWidth : 0f)
                        : getGutterStartX()
                        + lineNumbersGutterWidth
                        - (editor.isCodeFoldingEnabled ? editor.foldMarkerGutterWidth : 0f)
                        - GUTTER_TEXT_PADDING;
        int start = writeIntToChars(editor.cursor.cursorLine + 1, lineNumberChars);
        int count = lineNumberChars.length - start;
        float y = Math.round(visibleIndex * editor.lineHeight - editor.scroll.scrollY + editor.lineHeight - editor.paint.descent());
        int originalColor = lineNumbersPaint.getColor();
        lineNumbersPaint.setColor(currentLineNumberColor);
        canvas.drawText(lineNumberChars, start, count, lineNumX, y, lineNumbersPaint);
        lineNumbersPaint.setColor(originalColor);
    }

    public void drawCurrentLineNumberWrapped(
            Canvas canvas, int firstVisualIndex, int lastVisualIndex) {
        if (!showLineNumbers) return;
        int visualIndex = editor.getVisualIndexForLineAndChar(editor.cursor.cursorLine, 0);
        if (visualIndex < firstVisualIndex || visualIndex > lastVisualIndex) return;

        float lineNumX =
                editor.isRtl
                        ? getGutterStartX() + GUTTER_TEXT_PADDING
                        : getGutterStartX() + lineNumbersGutterWidth - GUTTER_TEXT_PADDING;
        int start = writeIntToChars(editor.cursor.cursorLine + 1, lineNumberChars);
        int count = lineNumberChars.length - start;
        float y = Math.round(visualIndex * editor.lineHeight - editor.scroll.scrollY + editor.lineHeight - editor.paint.descent());
        int originalColor = lineNumbersPaint.getColor();
        lineNumbersPaint.setColor(currentLineNumberColor);
        canvas.drawText(lineNumberChars, start, count, lineNumX, y, lineNumbersPaint);
        lineNumbersPaint.setColor(originalColor);
    }

    private boolean shouldUseLineNumberCache() {
        return showLineNumbers && lineNumbersGutterWidth > 0f && editor.getHeight() > 0;
    }

    private void ensureLineNumberCacheBitmap(int width, int height) {
        if (lineNumberCacheBitmap != null
                && lineNumberCacheWidth == width
                && lineNumberCacheHeight == height) {
            return;
        }
        if (lineNumberCacheBitmap != null) {
            lineNumberCacheBitmap.recycle();
        }
        lineNumberCacheBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        lineNumberCacheCanvas = new Canvas(lineNumberCacheBitmap);
        lineNumberCacheWidth = width;
        lineNumberCacheHeight = height;
    }

    private int writeIntToChars(int value, char[] chars) {
        if (value == 0) {
            chars[chars.length - 1] = '0';
            return chars.length - 1;
        }

        int negative = value < 0 ? 1 : 0;
        value = Math.abs(value);

        int len = 0;
        int temp = value;
        while (temp > 0) {
            len++;
            temp /= 10;
        }
        len += negative;

        int start = chars.length - len;
        int idx = chars.length - 1;

        while (value > 0) {
            chars[idx--] = (char) ('0' + (value % 10));
            value /= 10;
        }

        if (negative > 0) {
            chars[idx] = '-';
        }

        return start;
    }
}
