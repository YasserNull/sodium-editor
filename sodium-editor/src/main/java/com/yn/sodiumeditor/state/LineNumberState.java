package com.yn.sodiumeditor.state;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Typeface;
import androidx.annotation.Nullable;

/**
 * State class for line number functionality.
 * Stores line number state including visibility, gutter width, and cache fields.
 */
public class LineNumberState {

    private boolean showLineNumbers = false;
    private boolean highlightCurrentLineInGutter = true;
    private boolean lineNumberSelectionEnabled = true;
    private float lineNumbersGutterWidth = 0f;

    // Cache fields
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

    public LineNumberState() {
    }

    public boolean isShowLineNumbers() {
        return showLineNumbers;
    }

    public void setShowLineNumbers(boolean show) {
        this.showLineNumbers = show;
    }

    public boolean isHighlightCurrentLineInGutter() {
        return highlightCurrentLineInGutter;
    }

    public void setHighlightCurrentLineInGutter(boolean enabled) {
        this.highlightCurrentLineInGutter = enabled;
    }

    public boolean isLineNumberSelectionEnabled() {
        return lineNumberSelectionEnabled;
    }

    public void setLineNumberSelectionEnabled(boolean enabled) {
        this.lineNumberSelectionEnabled = enabled;
    }

    public float getLineNumbersGutterWidth() {
        return lineNumbersGutterWidth;
    }

    public void setLineNumbersGutterWidth(float width) {
        this.lineNumbersGutterWidth = width;
    }

    public void resetCache() {
        lineNumberCacheBitmap = null;
        lineNumberCacheCanvas = null;
        lineNumberCacheWidth = 0;
        lineNumberCacheHeight = 0;
        lineNumberCacheFirstIndex = -1;
        lineNumberCacheLastIndex = -1;
        lineNumberCacheBaseScrollY = 0f;
        lineNumberCacheTextSize = -1f;
        lineNumberCacheTypeface = null;
        lineNumberCacheRtl = false;
        lineNumberCacheWrapped = false;
        lineNumberCacheCodeFolding = false;
        lineNumberCacheGutterWidth = 0f;
        lineNumberCacheFoldMarkerWidth = 0f;
        lineNumberCacheLineHeight = 0f;
        lineNumberCacheColor = 0;
    }

    public boolean shouldRebuildCache(
            int gutterWidth,
            int height,
            int firstVisibleIndex,
            int drawLastIndex,
            float baseScrollY,
            float textSize,
            Typeface typeface,
            boolean isRtl,
            boolean isWrapped,
            boolean codeFoldingEnabled,
            float foldMarkerWidth,
            float lineHeight,
            int color) {
        return lineNumberCacheBitmap == null
                || lineNumberCacheWidth != gutterWidth
                || lineNumberCacheHeight != height
                || lineNumberCacheFirstIndex != firstVisibleIndex
                || lineNumberCacheLastIndex != drawLastIndex
                || Math.abs(lineNumberCacheBaseScrollY - baseScrollY) > 0.1f
                || lineNumberCacheTextSize != textSize
                || lineNumberCacheTypeface != typeface
                || lineNumberCacheRtl != isRtl
                || lineNumberCacheWrapped != isWrapped
                || lineNumberCacheCodeFolding != codeFoldingEnabled
                || Math.abs(lineNumberCacheGutterWidth - gutterWidth) > 0.1f
                || Math.abs(lineNumberCacheFoldMarkerWidth - foldMarkerWidth) > 0.1f
                || Math.abs(lineNumberCacheLineHeight - lineHeight) > 0.1f
                || lineNumberCacheColor != color;
    }

    public void updateCacheState(
            int firstVisibleIndex,
            int drawLastIndex,
            float baseScrollY,
            float textSize,
            Typeface typeface,
            boolean isRtl,
            boolean isWrapped,
            boolean codeFoldingEnabled,
            float gutterWidth,
            float foldMarkerWidth,
            float lineHeight,
            int color) {
        lineNumberCacheFirstIndex = firstVisibleIndex;
        lineNumberCacheLastIndex = drawLastIndex;
        lineNumberCacheBaseScrollY = baseScrollY;
        lineNumberCacheTextSize = textSize;
        lineNumberCacheTypeface = typeface;
        lineNumberCacheRtl = isRtl;
        lineNumberCacheWrapped = isWrapped;
        lineNumberCacheCodeFolding = codeFoldingEnabled;
        lineNumberCacheGutterWidth = gutterWidth;
        lineNumberCacheFoldMarkerWidth = foldMarkerWidth;
        lineNumberCacheLineHeight = lineHeight;
        lineNumberCacheColor = color;
    }
}
