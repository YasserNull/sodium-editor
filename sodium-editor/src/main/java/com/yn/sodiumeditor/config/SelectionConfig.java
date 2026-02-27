package com.yn.sodiumeditor.config;

import android.graphics.Paint;

/**
 * Configuration class for selection functionality.
 * Manages selection colors and paint settings.
 */
public final class SelectionConfig {
    private int selectionHighlightColor = 0x8033B5E5;
    private final Paint selectionPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    public SelectionConfig() {
        selectionPaint.setStyle(Paint.Style.FILL);
    }

    public int getSelectionHighlightColor() {
        return selectionHighlightColor;
    }

    public void setSelectionHighlightColor(int selectionHighlightColor) {
        this.selectionHighlightColor = selectionHighlightColor;
    }

    public Paint getSelectionPaint() {
        selectionPaint.setColor(selectionHighlightColor);
        return selectionPaint;
    }

    public void initPaints() {
        selectionPaint.setStyle(Paint.Style.FILL);
    }
}
