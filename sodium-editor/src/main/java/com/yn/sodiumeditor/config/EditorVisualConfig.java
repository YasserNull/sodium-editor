package com.yn.sodiumeditor.config;

import android.graphics.Bitmap;
import android.graphics.Rect;

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
}
