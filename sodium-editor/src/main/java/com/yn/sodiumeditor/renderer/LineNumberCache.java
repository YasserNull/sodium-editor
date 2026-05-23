package com.yn.sodiumeditor.renderer;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import com.yn.sodiumeditor.SodiumEditor;
import com.yn.sodiumeditor.core.linenumber.LineNumber;

/**
 * Manages bitmap caching for line numbers to improve rendering performance.
 */
public class LineNumberCache {
    private final SodiumEditor editor;
    private final LineNumber lineNumber;

    public LineNumberCache(SodiumEditor editor, LineNumber lineNumber) {
        this.editor = editor;
        this.lineNumber = lineNumber;
    }

    public void invalidate() {
        lineNumber.lineNumberCacheBitmap = null;
        lineNumber.lineNumberCacheCanvas = null;
    }

    public boolean shouldUseCache() {
        return lineNumber.showLineNumbers && lineNumber.lineNumbersGutterWidth > 0f && editor.getHeight() > 0;
    }

    public void ensureBitmap(int width, int height) {
        if (lineNumber.lineNumberCacheBitmap != null && lineNumber.lineNumberCacheWidth == width && lineNumber.lineNumberCacheHeight == height) return;
        lineNumber.lineNumberCacheBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        lineNumber.lineNumberCacheCanvas = new Canvas(lineNumber.lineNumberCacheBitmap);
        lineNumber.lineNumberCacheWidth = width;
        lineNumber.lineNumberCacheHeight = height;
    }

    public boolean needsRebuild(int width, int height, int first, int last, float baseY, boolean wrapped) {
        return lineNumber.lineNumberCacheBitmap == null
                || lineNumber.lineNumberCacheWidth != width
                || lineNumber.lineNumberCacheHeight != height
                || lineNumber.lineNumberCacheFirstIndex != first
                || lineNumber.lineNumberCacheLastIndex != last
                || Math.abs(lineNumber.lineNumberCacheBaseScrollY - baseY) > 0.1f
                || lineNumber.lineNumberCacheTextSize != lineNumber.lineNumbersPaint.getTextSize()
                || lineNumber.lineNumberCacheTypeface != lineNumber.lineNumbersPaint.getTypeface()
                || lineNumber.lineNumberCacheRtl != editor.textRender.isRtl
                || lineNumber.lineNumberCacheWrapped != wrapped
                || Math.abs(lineNumber.lineNumberCacheGutterWidth - lineNumber.lineNumbersGutterWidth) > 0.1f
                || Math.abs(lineNumber.lineNumberCacheLineHeight - editor.textRender.lineHeight) > 0.1f
                || lineNumber.lineNumberCacheColor != lineNumber.lineNumbersPaint.getColor();
    }

    public void updateMetadata(int first, int last, float baseY, boolean wrapped) {
        lineNumber.lineNumberCacheFirstIndex = first;
        lineNumber.lineNumberCacheLastIndex = last;
        lineNumber.lineNumberCacheBaseScrollY = baseY;
        lineNumber.lineNumberCacheTextSize = lineNumber.lineNumbersPaint.getTextSize();
        lineNumber.lineNumberCacheTypeface = lineNumber.lineNumbersPaint.getTypeface();
        lineNumber.lineNumberCacheRtl = editor.textRender.isRtl;
        lineNumber.lineNumberCacheWrapped = wrapped;
        lineNumber.lineNumberCacheGutterWidth = lineNumber.lineNumbersGutterWidth;
        lineNumber.lineNumberCacheLineHeight = editor.textRender.lineHeight;
        lineNumber.lineNumberCacheColor = lineNumber.lineNumbersPaint.getColor();
    }
}
