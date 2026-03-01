package com.yn.sodiumeditor.renderer;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import com.yn.sodiumeditor.SodiumEditorView;
import com.yn.sodiumeditor.config.LineNumberConfig;
import com.yn.sodiumeditor.state.LineNumberState;

/**
 * Renderer class for line numbers.
 * Handles drawing line numbers with caching support for both wrapped and unwrapped text.
 */
public class LineNumberRenderer {

    private final SodiumEditorView view;
    private final LineNumberState state;
    private final LineNumberConfig config;
    private final char[] lineNumberChars = new char[16];

    public final Paint lineNumbersPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    public final Paint gutterPaint = new Paint();
    public final Paint gutterSeparatorPaint = new Paint();

    public LineNumberRenderer(SodiumEditorView view, LineNumberState state, LineNumberConfig config) {
        this.view = view;
        this.state = state;
        this.config = config;
        lineNumbersPaint.setTextAlign(Paint.Align.RIGHT);
        lineNumbersPaint.setColor(config.getLineNumberColor());
        gutterPaint.setColor(config.getGutterBackgroundColor());
        gutterSeparatorPaint.setColor(config.getGutterSeparatorColor());
    }

    public void initDefaults(Paint basePaint, float density) {
        lineNumbersPaint.setTextAlign(Paint.Align.RIGHT);
        lineNumbersPaint.setColor(config.getLineNumberColor());
        lineNumbersPaint.setTextSize(basePaint.getTextSize());
        lineNumbersPaint.setTypeface(basePaint.getTypeface());
        gutterPaint.setColor(config.getGutterBackgroundColor());
        config.setGutterSeparatorWidth(4 * density);
        gutterSeparatorPaint.setColor(config.getGutterSeparatorColor());
    }

    public void setTextAlign(boolean rtl) {
        lineNumbersPaint.setTextAlign(rtl ? Paint.Align.LEFT : Paint.Align.RIGHT);
    }

    public void setTextSize(float sizePx) {
        lineNumbersPaint.setTextSize(sizePx);
    }

    public void setTypeface(Typeface typeface) {
        lineNumbersPaint.setTypeface(typeface);
    }

    public Paint getLineNumberPaint() {
        return lineNumbersPaint;
    }

    public Paint getGutterPaint() {
        return gutterPaint;
    }

    public Paint getGutterSeparatorPaint() {
        return gutterSeparatorPaint;
    }

    public void drawCurrentLineHighlightInGutter(Canvas canvas, float top, float bottom, Paint paint) {
        if (!state.isShowLineNumbers() || !state.isHighlightCurrentLineInGutter() || state.getLineNumbersGutterWidth() <= 0f) return;
        float left = view.getGutterStartX();
        float right = left + state.getLineNumbersGutterWidth();
        float sep = config.getGutterSeparatorWidth();
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

    public float computeGutterWidth(
            int maxLines,
            boolean codeFoldingEnabled,
            float foldMarkerWidth) {
        if (!state.isShowLineNumbers()) return 0f;
        String maxLineNum = String.valueOf(Math.max(0, maxLines));
        float baseWidth =
                lineNumbersPaint.measureText(maxLineNum) + (LineNumberConfig.GUTTER_TEXT_PADDING * 2f);
        float foldWidth = codeFoldingEnabled ? foldMarkerWidth : 0f;
        return baseWidth + foldWidth + config.getGutterSeparatorWidth();
    }

    public float getTextStartX(float paddingLeft, boolean rtl) {
        return rtl ? paddingLeft : paddingLeft + state.getLineNumbersGutterWidth();
    }

    public float getTextAvailableWidth(float viewWidth, float paddingLeft) {
        return Math.max(0f, viewWidth - state.getLineNumbersGutterWidth() - paddingLeft);
    }

    public float getLineNumberViewLeft(float viewWidth, boolean rtl) {
        return rtl ? viewWidth - state.getLineNumbersGutterWidth() : 0f;
    }

    public boolean isInLineNumberGutter(float x, float startX) {
        if (!state.isShowLineNumbers() || state.getLineNumbersGutterWidth() <= 0f) return false;
        return x >= startX && x <= startX + state.getLineNumbersGutterWidth();
    }

    public float getContentClipLeft(boolean rtl) {
        return rtl ? 0f : state.getLineNumbersGutterWidth();
    }

    public float getContentClipRight(float viewWidth, boolean rtl) {
        return rtl ? viewWidth - state.getLineNumbersGutterWidth() : viewWidth;
    }

    public float getContentViewLeft(boolean rtl) {
        return rtl ? 0f : state.getLineNumbersGutterWidth();
    }

    public float getContentViewRight(float viewWidth, boolean rtl) {
        return rtl ? (viewWidth - state.getLineNumbersGutterWidth()) : viewWidth;
    }

    public float getSeparatorLeft(float gutterStartX) {
        return gutterStartX + state.getLineNumbersGutterWidth() - config.getGutterSeparatorWidth();
    }

    public float getGutterRight(float gutterStartX) {
        return gutterStartX + state.getLineNumbersGutterWidth();
    }

    public boolean shouldUseLineNumberCache() {
        return state.isShowLineNumbers() && state.getLineNumbersGutterWidth() > 0f && view.getHeight() > 0;
    }

    private void ensureLineNumberCacheBitmap(int width, int height) {
        if (state.lineNumberCacheBitmap != null
                && state.lineNumberCacheWidth == width
                && state.lineNumberCacheHeight == height) {
            return;
        }
        state.lineNumberCacheBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        state.lineNumberCacheCanvas = new Canvas(state.lineNumberCacheBitmap);
        state.lineNumberCacheWidth = width;
        state.lineNumberCacheHeight = height;
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
        if (view.foldState.isCodeFoldingEnabled()) {
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

        int gutterWidth = Math.max(1, Math.round(state.getLineNumbersGutterWidth()));
        float lineHeight = view.lineHeight;
        float padPx = lineHeight;
        int height = view.getHeight() + Math.round(padPx * 2f);
        float baseScrollY =
                (float) Math.floor(view.scrollManager.scrollY / lineHeight) * lineHeight - padPx;

        boolean needsRebuild = state.shouldRebuildCache(
                gutterWidth, height, firstVisibleIndex, drawLastIndex, baseScrollY,
                lineNumbersPaint.getTextSize(), lineNumbersPaint.getTypeface(),
                view.isRtl, false, view.foldState.isCodeFoldingEnabled(),
                view.foldRenderer.getFoldMarkerGutterWidth(), lineHeight, lineNumbersPaint.getColor());

        if (needsRebuild) {
            ensureLineNumberCacheBitmap(gutterWidth, height);
            state.lineNumberCacheBitmap.eraseColor(0);

            float lineNumX =
                    view.isRtl
                            ? view.getGutterStartX()
                            + LineNumberConfig.GUTTER_TEXT_PADDING
                            + (view.foldState.isCodeFoldingEnabled() ? view.foldRenderer.getFoldMarkerGutterWidth() : 0f)
                            : view.getGutterStartX()
                            + state.getLineNumbersGutterWidth()
                            - (view.foldState.isCodeFoldingEnabled() ? view.foldRenderer.getFoldMarkerGutterWidth() : 0f)
                            - LineNumberConfig.GUTTER_TEXT_PADDING;
            float lineNumXLocal = lineNumX - view.getGutterStartX();

            if (view.foldState.isCodeFoldingEnabled()) {
                for (int v = firstVisibleIndex; v <= drawLastIndex; v++) {
                    int i = view.mapVisibleIndexToGlobal(v);
                    int start = writeIntToChars(i + 1, lineNumberChars);
                    int count = lineNumberChars.length - start;
                    float y =
                            Math.round(
                                    v * lineHeight - baseScrollY + lineHeight - view.paint.descent());
                    state.lineNumberCacheCanvas.drawText(
                            lineNumberChars, start, count, lineNumXLocal, y, lineNumbersPaint);
                }
            } else {
                for (int i = firstVisibleLine; i <= drawLastLine; i++) {
                    int start = writeIntToChars(i + 1, lineNumberChars);
                    int count = lineNumberChars.length - start;
                    float y =
                            Math.round(
                                    i * lineHeight - baseScrollY + lineHeight - view.paint.descent());
                    state.lineNumberCacheCanvas.drawText(
                            lineNumberChars, start, count, lineNumXLocal, y, lineNumbersPaint);
                }
            }

            state.updateCacheState(
                    firstVisibleIndex, drawLastIndex, baseScrollY,
                    lineNumbersPaint.getTextSize(), lineNumbersPaint.getTypeface(),
                    view.isRtl, false, view.foldState.isCodeFoldingEnabled(),
                    state.getLineNumbersGutterWidth(), view.foldRenderer.getFoldMarkerGutterWidth(),
                    lineHeight, lineNumbersPaint.getColor());
        }

        float offsetY = state.lineNumberCacheBaseScrollY - view.scrollManager.scrollY;
        canvas.drawBitmap(state.lineNumberCacheBitmap, view.getGutterStartX(), offsetY, null);
        drawCurrentLineNumberUnwrapped(canvas, firstVisibleIndex, lastVisibleIndex);
    }

    public void drawLineNumbersCachedWrapped(Canvas canvas, int firstVisualIndex, int lastVisualIndex) {
        if (!shouldUseLineNumberCache()) {
            drawLineNumbersDirectWrapped(canvas, firstVisualIndex, lastVisualIndex);
            return;
        }

        int drawLastIndex = lastVisualIndex;
        int totalVisual = view.wrapWordMapper.getTotalVisualLineCount(view, view.getVisibleLineCount());
        if (totalVisual > 0) {
            drawLastIndex = Math.min(lastVisualIndex + 1, totalVisual - 1);
        }

        int gutterWidth = Math.max(1, Math.round(state.getLineNumbersGutterWidth()));
        float lineHeight = view.lineHeight;
        float padPx = lineHeight;
        int height = view.getHeight() + Math.round(padPx * 2f);
        float baseScrollY =
                (float) Math.floor(view.scrollManager.scrollY / lineHeight) * lineHeight - padPx;

        boolean needsRebuild = state.shouldRebuildCache(
                gutterWidth, height, firstVisualIndex, drawLastIndex, baseScrollY,
                lineNumbersPaint.getTextSize(), lineNumbersPaint.getTypeface(),
                view.isRtl, true, view.foldState.isCodeFoldingEnabled(),
                view.foldRenderer.getFoldMarkerGutterWidth(), lineHeight, lineNumbersPaint.getColor());

        if (needsRebuild) {
            ensureLineNumberCacheBitmap(gutterWidth, height);
            state.lineNumberCacheBitmap.eraseColor(0);

            float lineNumX =
                    view.isRtl
                            ? view.getGutterStartX() + LineNumberConfig.GUTTER_TEXT_PADDING
                            : view.getGutterStartX()
                            + state.getLineNumbersGutterWidth()
                            - LineNumberConfig.GUTTER_TEXT_PADDING;
            float lineNumXLocal = lineNumX - view.getGutterStartX();

            for (int v = firstVisualIndex; v <= drawLastIndex; v++) {
                SodiumEditorView.VisualLinePosition pos =
                        view.wrapWordMapper.getVisualPositionForIndex(view, v, Math.max(1, Math.round(view.getWidth() - view.getTextStartX())));
                if (pos.segment != 0) continue;
                int start = writeIntToChars(pos.line + 1, lineNumberChars);
                int count = lineNumberChars.length - start;
                float y =
                        Math.round(
                                v * lineHeight - baseScrollY + lineHeight - view.paint.descent());
                state.lineNumberCacheCanvas.drawText(
                        lineNumberChars, start, count, lineNumXLocal, y, lineNumbersPaint);
            }

            state.updateCacheState(
                    firstVisualIndex, drawLastIndex, baseScrollY,
                    lineNumbersPaint.getTextSize(), lineNumbersPaint.getTypeface(),
                    view.isRtl, true, view.foldState.isCodeFoldingEnabled(),
                    state.getLineNumbersGutterWidth(), view.foldRenderer.getFoldMarkerGutterWidth(),
                    lineHeight, lineNumbersPaint.getColor());
        }

        float offsetY = state.lineNumberCacheBaseScrollY - view.scrollManager.scrollY;
        canvas.drawBitmap(state.lineNumberCacheBitmap, view.getGutterStartX(), offsetY, null);
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
        if (view.foldState.isCodeFoldingEnabled()) {
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
                        + LineNumberConfig.GUTTER_TEXT_PADDING
                        + (view.foldState.isCodeFoldingEnabled() ? view.foldRenderer.getFoldMarkerGutterWidth() : 0f)
                        : view.getGutterStartX()
                        + state.getLineNumbersGutterWidth()
                        - (view.foldState.isCodeFoldingEnabled() ? view.foldRenderer.getFoldMarkerGutterWidth() : 0f)
                        - LineNumberConfig.GUTTER_TEXT_PADDING;

        if (view.foldState.isCodeFoldingEnabled()) {
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
                if (i == view.cursorState.getCursorLine()) {
                    int originalColor = lineNumbersPaint.getColor();
                    lineNumbersPaint.setColor(config.getCurrentLineNumberColor());
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
                if (i == view.cursorState.getCursorLine()) {
                    int originalColor = lineNumbersPaint.getColor();
                    lineNumbersPaint.setColor(config.getCurrentLineNumberColor());
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
                        ? view.getGutterStartX() + LineNumberConfig.GUTTER_TEXT_PADDING
                        : view.getGutterStartX()
                        + state.getLineNumbersGutterWidth()
                        - LineNumberConfig.GUTTER_TEXT_PADDING;

        int drawLastIndex = lastVisualIndex;
        int totalVisual = view.wrapWordMapper.getTotalVisualLineCount(view, view.getVisibleLineCount());
        if (totalVisual > 0) drawLastIndex = Math.min(lastVisualIndex + 1, totalVisual - 1);

        for (int v = firstVisualIndex; v <= drawLastIndex; v++) {
            SodiumEditorView.VisualLinePosition pos =
                    view.wrapWordMapper.getVisualPositionForIndex(view, v, Math.max(1, Math.round(view.getWidth() - view.getTextStartX())));
            if (pos.segment != 0) continue;
            int start = writeIntToChars(pos.line + 1, lineNumberChars);
            int count = lineNumberChars.length - start;
            float y =
                    Math.round(
                            v * lineHeight
                                    - view.scrollManager.scrollY
                                    + lineHeight
                                    - view.paint.descent());
            if (pos.line == view.cursorState.getCursorLine()) {
                int originalColor = lineNumbersPaint.getColor();
                lineNumbersPaint.setColor(config.getCurrentLineNumberColor());
                canvas.drawText(lineNumberChars, start, count, lineNumX, y, lineNumbersPaint);
                lineNumbersPaint.setColor(originalColor);
            } else {
                canvas.drawText(lineNumberChars, start, count, lineNumX, y, lineNumbersPaint);
            }
        }
    }

    void drawCurrentLineNumberUnwrapped(Canvas canvas, int firstVisibleIndex, int lastVisibleIndex) {
        if (!state.isShowLineNumbers()) return;
        if (view.foldState.isCodeFoldingEnabled()
                && view.foldState.isLineHiddenByFold(view.cursorState.getCursorLine())) return;

        int cursorLine = view.cursorState.getCursorLine();
        int visibleIndex =
                view.foldState.isCodeFoldingEnabled()
                        ? view.getVisibleIndexForGlobalLine(cursorLine)
                        : cursorLine;
        if (visibleIndex < firstVisibleIndex || visibleIndex > lastVisibleIndex) return;

        float lineHeight = view.lineHeight;
        float lineNumX =
                view.isRtl
                        ? view.getGutterStartX()
                        + LineNumberConfig.GUTTER_TEXT_PADDING
                        + (view.foldState.isCodeFoldingEnabled() ? view.foldRenderer.getFoldMarkerGutterWidth() : 0f)
                        : view.getGutterStartX()
                        + state.getLineNumbersGutterWidth()
                        - (view.foldState.isCodeFoldingEnabled() ? view.foldRenderer.getFoldMarkerGutterWidth() : 0f)
                        - LineNumberConfig.GUTTER_TEXT_PADDING;
        int start = writeIntToChars(cursorLine + 1, lineNumberChars);
        int count = lineNumberChars.length - start;
        float y =
                Math.round(
                        visibleIndex * lineHeight
                                - view.scrollManager.scrollY
                                + lineHeight
                                - view.paint.descent());
        int originalColor = lineNumbersPaint.getColor();
        lineNumbersPaint.setColor(config.getCurrentLineNumberColor());
        canvas.drawText(lineNumberChars, start, count, lineNumX, y, lineNumbersPaint);
        lineNumbersPaint.setColor(originalColor);
    }

    void drawCurrentLineNumberWrapped(Canvas canvas, int firstVisualIndex, int lastVisualIndex) {
        if (!state.isShowLineNumbers()) return;
        int visualIndex =
                view.getVisualIndexForLineAndChar(view.cursorState.getCursorLine(), 0);
        if (visualIndex < firstVisualIndex || visualIndex > lastVisualIndex) return;

        float lineHeight = view.lineHeight;
        float lineNumX =
                view.isRtl
                        ? view.getGutterStartX() + LineNumberConfig.GUTTER_TEXT_PADDING
                        : view.getGutterStartX()
                        + state.getLineNumbersGutterWidth()
                        - LineNumberConfig.GUTTER_TEXT_PADDING;
        int start = writeIntToChars(view.cursorState.getCursorLine() + 1, lineNumberChars);
        int count = lineNumberChars.length - start;
        float y =
                Math.round(
                        visualIndex * lineHeight
                                - view.scrollManager.scrollY
                                + lineHeight
                                - view.paint.descent());
        int originalColor = lineNumbersPaint.getColor();
        lineNumbersPaint.setColor(config.getCurrentLineNumberColor());
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
        state.resetCache();
    }
}
