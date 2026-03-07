package com.yn.sodiumeditor.config;

import android.graphics.Bitmap;
import android.graphics.Rect;
import androidx.annotation.Nullable;

/**
 * Configuration class for editor visual appearance.
 */
public class EditorVisualConfig {

    // Whitespace guide characters
    public final String WHITESPACE_GUIDE_SPACE = "\u00B7";
    public final String WHITESPACE_GUIDE_TAB = "\u2192";

    // Fold placeholder
    public final String FOLD_PLACEHOLDER_TEXT = "<—>";

    // Indent block unit
    public final String INDENT_BLOCK_UNIT = "  ";

    // Paint settings
    public int paintTextSize = 36;
    public int paintColor = 0xFF000000;
    public float paddingLeft = 10f;

    // Text direction
    public boolean isRtl = false;

    // Visual padding
    public float bottomScrollOffset = 100f;
    public float minBottomVisibleSpace = 50f;

    // Editor background settings
    public boolean hasEditorBackgroundColor = false;
    public int editorBackgroundColor = 0x00000000;
    public Bitmap editorBackgroundBitmap = null;
    public final Rect editorBackgroundDst = new Rect();

    // Cursor settings
    public float baseCursorTextSizePx = 36f;

    // Visible char padding
    public int visibleCharPadding = 2;

    public EditorVisualConfig() {
    }

    public int getPaintTextSize() {
        return paintTextSize;
    }

    public void setPaintTextSize(int size) {
        this.paintTextSize = size;
    }

    public int getPaintColor() {
        return paintColor;
    }

    public void setPaintColor(int color) {
        this.paintColor = color;
    }

    public float getPaddingLeft() {
        return paddingLeft;
    }

    public void setPaddingLeft(float padding) {
        this.paddingLeft = padding;
    }

    public boolean isRtl() {
        return isRtl;
    }

    public void setRtl(boolean rtl) {
        isRtl = rtl;
    }

    public String getWhitespaceGuideSpace() {
        return WHITESPACE_GUIDE_SPACE;
    }

    public String getWhitespaceGuideTab() {
        return WHITESPACE_GUIDE_TAB;
    }

    public String getFoldPlaceholderText() {
        return FOLD_PLACEHOLDER_TEXT;
    }

    public String getIndentBlockUnit() {
        return INDENT_BLOCK_UNIT;
    }

    // Editor background getters/setters
    public boolean hasEditorBackgroundColor() {
        return hasEditorBackgroundColor;
    }

    public void setHasEditorBackgroundColor(boolean has) {
        hasEditorBackgroundColor = has;
    }

    public int getEditorBackgroundColor() {
        return editorBackgroundColor;
    }

    public void setEditorBackgroundColor(int color) {
        editorBackgroundColor = color;
    }

    public Bitmap getEditorBackgroundBitmap() {
        return editorBackgroundBitmap;
    }

    public void setEditorBackgroundBitmap(Bitmap bitmap) {
        editorBackgroundBitmap = bitmap;
    }

    public Rect getEditorBackgroundDst() {
        return editorBackgroundDst;
    }

    /**
     * Sets visible char padding and triggers invalidation.
     * @param paddingChars number of padding characters
     * @param editor the SodiumEditor instance to invalidate
     */
    public void setVisibleCharPadding(int paddingChars, com.yn.sodiumeditor.SodiumEditor editor) {
        int safe = Math.max(0, paddingChars);
        if (this.visibleCharPadding == safe) return;
        this.visibleCharPadding = safe;
        editor.invalidate();
    }

    /**
     * Sets editor background color and triggers invalidation.
     * @param color the background color
     * @param editor the SodiumEditor instance to invalidate
     */
    public void setEditorBackgroundColor(int color, com.yn.sodiumeditor.SodiumEditor editor) {
        this.hasEditorBackgroundColor = true;
        this.editorBackgroundColor = color;
        editor.invalidate();
    }

    /**
     * Clears editor background color and triggers invalidation.
     * @param editor the SodiumEditor instance to invalidate
     */
    public void clearEditorBackgroundColor(com.yn.sodiumeditor.SodiumEditor editor) {
        if (!this.hasEditorBackgroundColor) return;
        this.hasEditorBackgroundColor = false;
        editor.invalidate();
    }

    /**
     * Sets editor background bitmap and triggers invalidation.
     * @param bitmap the background bitmap
     * @param editor the SodiumEditor instance to invalidate
     */
    public void setEditorBackgroundBitmap(Bitmap bitmap, com.yn.sodiumeditor.SodiumEditor editor) {
        if (this.editorBackgroundBitmap != null && !this.editorBackgroundBitmap.isRecycled()) {
            this.editorBackgroundBitmap.recycle();
        }
        this.editorBackgroundBitmap = bitmap;
        editor.invalidate();
    }

    /**
     * Clears editor background bitmap and triggers invalidation.
     * @param editor the SodiumEditor instance to invalidate
     */
    public void clearEditorBackgroundBitmap(com.yn.sodiumeditor.SodiumEditor editor) {
        if (this.editorBackgroundBitmap != null && !this.editorBackgroundBitmap.isRecycled()) {
            this.editorBackgroundBitmap.recycle();
        }
        this.editorBackgroundBitmap = null;
        editor.invalidate();
    }

    /**
     * Sets layout direction (RTL/LTR) and triggers necessary updates.
     * @param isRtl whether RTL direction
     * @param editor the SodiumEditor instance to update
     */
    public void setLayoutDirection(boolean isRtl, com.yn.sodiumeditor.SodiumEditor editor) {
        if (this.isRtl == isRtl) return;
        this.isRtl = isRtl;
        editor.lineNumberRenderer.setTextAlign(isRtl);
        editor.foldRenderer.foldMarkerPaint.setTextAlign(isRtl ? android.graphics.Paint.Align.LEFT : android.graphics.Paint.Align.RIGHT);
        editor.lineNumberRenderer.invalidateCache();
        editor.requestLayout();
        if (editor.wrapWordState.isWordWrapEnabled) editor.wrapWordBuilder.invalidate(true, true);
        editor.scrollManager.maxScrollXForScroll = 0f;
        editor.scrollManager.maxTextStartXForScroll = 0f;
        editor.scrollManager.scrollX = 0f;
        editor.scrollManager.keepCursorVisibleHorizontally();
        editor.invalidate();
    }

    /**
     * Sets typeface and applies to paint.
     * @param typeface the Typeface to apply
     * @param style the style (NORMAL, BOLD, ITALIC, BOLD_ITALIC)
     * @param editor the SodiumEditor instance to update
     */
    public void setTypeface(@Nullable android.graphics.Typeface typeface, int style, com.yn.sodiumeditor.SodiumEditor editor) {
        editor.applyTypeface(typeface, style);
    }

    /**
     * Sets text size in pixels and triggers necessary updates.
     * @param sizePx text size in pixels
     * @param editor the SodiumEditor instance to update
     * @param deferWrapRebuild whether to defer word wrap rebuild
     */
    public void setTextSizePx(float sizePx, com.yn.sodiumeditor.SodiumEditor editor, boolean deferWrapRebuild) {
        float oldSize = editor.editorConfig.paint.getTextSize();
        if (Math.abs(sizePx - oldSize) < 0.1f) return;

        editor.editorConfig.paint.setTextSize(sizePx);
        editor.inlinePredictionRenderer.onTextSizeChanged(sizePx);
        editor.lineNumberRenderer.setTextSize(sizePx);
        editor.foldRenderer.foldMarkerPaint.setTextSize(sizePx * editor.foldRenderer.foldMarkerTextScale);
        editor.wrapWordIndicatorRender.updatePaintForTextSize(sizePx, editor.editorConfig.paint);
        editor.editorConfig.lineHeight = editor.editorConfig.paint.getFontSpacing();
        editor.zoomEngine.updateTextSizeDependentMetrics(editor);
        editor.whitespaceGuideRenderer.updateMetrics(editor.editorConfig.paint, editor.editorConfig.visualConfig.WHITESPACE_GUIDE_SPACE, editor.editorConfig.visualConfig.WHITESPACE_GUIDE_TAB);
        editor.lineNumberRenderer.invalidateCache();

        for (com.yn.sodiumeditor.core.HighlightRule rule : editor.highlightState.highlightRules) {
            rule.updateTextSize(sizePx);
        }
        editor.whitespaceGuideRenderer.updateRuleTextSize(sizePx, editor.highlightState.stringHighlightRule, editor.highlightState.blockCommentHighlightRule);
        if (editor.highlightState.lineCommentHighlightRule != null) editor.highlightState.lineCommentHighlightRule.updateTextSize(sizePx);
        editor.highlightState.clearHighlightCaches();

        synchronized (editor.editorState.lineWidthCache) {
            editor.editorState.lineWidthCache.clear();
        }
        float scale = sizePx / oldSize;
        editor.editorState.currentMaxWindowLineWidth *= scale;
        editor.editorState.globalMaxLineWidth *= scale;
        editor.scrollManager.maxLineWidthForScroll *= scale;
        editor.scrollManager.maxScrollXForScroll *= scale;
        editor.scrollManager.maxTextStartXForScroll = 0f;
        if (scale < 1f) {
            editor.scrollManager.maxLineWidthForScroll = 0f;
            editor.scrollManager.maxScrollXForScroll = 0f;
        }

        editor.requestLayout();
        if (editor.wrapWordState.isWordWrapEnabled) editor.wrapWordBuilder.invalidate(true, !deferWrapRebuild);
        editor.wrapWordBuilder.requestPrefixRebuild(editor);
        editor.invalidate();
    }

    /**
     * Sets text size in pixels and triggers necessary updates (immediate rebuild).
     * @param sizePx text size in pixels
     * @param editor the SodiumEditor instance to update
     */
    public void setTextSizePx(float sizePx, com.yn.sodiumeditor.SodiumEditor editor) {
        setTextSizePx(sizePx, editor, false);
    }

    //================================================================================
    // Editor Background Image
    //================================================================================

    /**
     * Sets editor background image from assets.
     * @param assetPath the path to the asset
     * @param editor the SodiumEditor instance
     */
    public void setEditorBackgroundImageFromAssets(String assetPath, com.yn.sodiumeditor.SodiumEditor editor) {
        if (assetPath == null) return;
        try (android.content.res.AssetManager assets = editor.getContext().getAssets();
             java.io.InputStream input = assets.open(assetPath)) {
            android.graphics.Bitmap bmp = android.graphics.BitmapFactory.decodeStream(input);
            if (bmp != null) {
                setEditorBackgroundBitmap(bmp, editor);
            }
        } catch (Exception e) {
            android.util.Log.e("EditorVisualConfig", "setEditorBackgroundImageFromAssets failed: " + assetPath, e);
        }
    }

    /**
     * Sets editor background image from file.
     * @param filePath the path to the image file
     * @param editor the SodiumEditor instance
     */
    public void setEditorBackgroundImageFromFile(String filePath, com.yn.sodiumeditor.SodiumEditor editor) {
        if (filePath == null) return;
        try {
            android.graphics.Bitmap bmp = android.graphics.BitmapFactory.decodeFile(filePath);
            if (bmp != null) {
                setEditorBackgroundBitmap(bmp, editor);
            }
        } catch (Exception e) {
            android.util.Log.e("EditorVisualConfig", "setEditorBackgroundImageFromFile failed: " + filePath, e);
        }
    }

    //================================================================================
    // Font Loading
    //================================================================================

    /**
     * Sets font from assets.
     * @param assetPath the path to the font asset
     * @param style the style (NORMAL, BOLD, ITALIC, BOLD_ITALIC)
     * @param editor the SodiumEditor instance
     */
    public void setFontFromAssets(String assetPath, int style, com.yn.sodiumeditor.SodiumEditor editor) {
        try {
            android.graphics.Typeface tf = android.graphics.Typeface.createFromAsset(editor.getContext().getAssets(), assetPath);
            setTypeface(tf, style, editor);
        } catch (Exception e) {
            android.util.Log.e("EditorVisualConfig", "setFontFromAssets failed: " + assetPath, e);
        }
    }

    /**
     * Sets font from file.
     * @param filePath the path to the font file
     * @param style the style (NORMAL, BOLD, ITALIC, BOLD_ITALIC)
     * @param editor the SodiumEditor instance
     */
    public void setFontFromFile(String filePath, int style, com.yn.sodiumeditor.SodiumEditor editor) {
        try {
            android.graphics.Typeface tf = android.graphics.Typeface.createFromFile(filePath);
            setTypeface(tf, style, editor);
        } catch (Exception e) {
            android.util.Log.e("EditorVisualConfig", "setFontFromFile failed: " + filePath, e);
        }
    }
}
