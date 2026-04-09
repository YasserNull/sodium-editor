package com.yn.sodiumeditor.renderer;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import com.yn.sodiumeditor.SodiumEditor;
import com.yn.sodiumeditor.core.WordWrap;

/**
 * LineNumberRender handles all line number rendering logic for SodiumEditor.
 * This includes:
 * - Line number cache management
 * - Drawing line numbers (cached and direct)
 * - Drawing current line number highlight
 * - Writing integers to char buffers
 */
public class LineNumberRender {

    private final SodiumEditor editor;

    // Line number cache state
    public boolean showLineNumbers = true;
    public float lineNumbersGutterWidth = 0f;
    public float gutterSeparatorWidth = 1f;
    public int gutterSeparatorColor = 0xFFCCCCCC;
    public int lineNumbersColor = 0xFF888888;
    public int currentLineNumberColor = 0xFF000000;
    public float GUTTER_TEXT_PADDING = 8f;

    // Cache bitmap
    public Bitmap lineNumberCacheBitmap = null;
    public Canvas lineNumberCacheCanvas = null;
    public int lineNumberCacheWidth = 0;
    public int lineNumberCacheHeight = 0;
    public int lineNumberCacheFirstIndex = -1;
    public int lineNumberCacheLastIndex = -1;
    public float lineNumberCacheBaseScrollY = 0f;
    public float lineNumberCacheTextSize = 0f;
    public android.graphics.Typeface lineNumberCacheTypeface = null;
    public boolean lineNumberCacheRtl = false;
    public boolean lineNumberCacheWrapped = false;
    public boolean lineNumberCacheCodeFolding = false;
    public float lineNumberCacheGutterWidth = 0f;
    public float lineNumberCacheFoldMarkerWidth = 0f;
    public float lineNumberCacheLineHeight = 0f;
    public int lineNumberCacheColor = 0;

    // Reusable char array for number conversion
    public final char[] lineNumberChars = new char[12];

    public LineNumberRender(SodiumEditor editor) {
        this.editor = editor;
    }

    // ========================================================================
    // Cache Management
    // ========================================================================

    /**
     * Check if line number cache should be used
     */
    public boolean shouldUseLineNumberCache() {
        return showLineNumbers
                && lineNumbersGutterWidth > 0f
                && editor.getHeight() > 0;
    }

    /**
     * Ensure line number cache bitmap exists
     */
    public void ensureLineNumberCacheBitmap(int width, int height) {
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

    /**
     * Write integer to chars buffer (delegated)
     */
    public int writeIntToChars(int value, char[] chars) {
        return editor.lineNumber.writeIntToChars(value, chars);
    }

    // ========================================================================
    // Gutter Calculations
    // ========================================================================

    /**
     * Get the gutter start X position.
     */
    public float getGutterStartX() {
        if (editor.textRender.isRtl) {
            return editor.getWidth() - lineNumbersGutterWidth;
        } else {
            return 0f;
        }
    }

    /**
     * Recalculate gutter width based on line count.
     */
    public void recalculateGutterWidth() {
        if (!showLineNumbers) {
            lineNumbersGutterWidth = 0f;
            return;
        }

        int lineCount = editor.getLinesCount();
        if (lineCount <= 0) lineCount = 1;
        int digits = String.valueOf(lineCount).length();
        float textWidth = editor.textRender.paint.measureText("8") * (digits + 1);
        lineNumbersGutterWidth = Math.max(40f, textWidth + GUTTER_TEXT_PADDING * 2f);

        if (editor.codeFold.isCodeFoldingEnabled) {
            lineNumbersGutterWidth += editor.codeFold.animation.foldMarkerGutterWidth;
        }

        editor.lineNumber.invalidateLineNumberCache();
    }

    // ========================================================================
    // Drawing Methods (delegated to TextRender for canvas operations)
    // ========================================================================

    /**
     * Draw line numbers cached (unwrapped)
     */
    public void drawLineNumbersCachedUnwrapped(
            Canvas canvas, int firstVisibleIndex, int lastVisibleIndex,
            int firstVisibleLine, int lastVisibleLine) {
        // Delegated back to TextRender which has the actual implementation
        editor.textRender.drawLineNumbersCachedUnwrappedImpl(canvas, firstVisibleIndex, lastVisibleIndex, firstVisibleLine, lastVisibleLine);
    }

    /**
     * Draw line numbers cached (wrapped)
     */
    public void drawLineNumbersCachedWrapped(Canvas canvas, int firstVisualIndex, int lastVisualIndex) {
        editor.textRender.drawLineNumbersCachedWrappedImpl(canvas, firstVisualIndex, lastVisualIndex);
    }

    /**
     * Draw line numbers direct (unwrapped)
     */
    public void drawLineNumbersDirectUnwrapped(
            Canvas canvas, int firstVisibleIndex, int lastVisibleIndex,
            int firstVisibleLine, int lastVisibleLine) {
        editor.textRender.drawLineNumbersDirectUnwrappedImpl(canvas, firstVisibleIndex, lastVisibleIndex, firstVisibleLine, lastVisibleLine);
    }

    /**
     * Draw line numbers direct (wrapped)
     */
    public void drawLineNumbersDirectWrapped(Canvas canvas, int firstVisualIndex, int lastVisualIndex) {
        editor.textRender.drawLineNumbersDirectWrappedImpl(canvas, firstVisualIndex, lastVisualIndex);
    }
}
