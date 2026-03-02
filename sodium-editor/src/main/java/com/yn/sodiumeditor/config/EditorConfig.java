package com.yn.sodiumeditor.config;

import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;

/**
 * Main configuration class for SodiumEditor.
 * Aggregates all editor configuration settings.
 */
public class EditorConfig {

    public static final int STYLE_NORMAL = 0;
    public static final int STYLE_BOLD = 1;
    public static final int STYLE_ITALIC = 2;
    public static final int STYLE_BOLD_ITALIC = 3;

    // Paint & metrics
    public final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    public Typeface baseTypeface = Typeface.DEFAULT;
    public float lineHeight;
    public float paddingLeft = 10f;
    public final Rect textBounds = new Rect();

    // Sub-configs
    public final EditorVisualConfig visualConfig = new EditorVisualConfig();
    public final EditorPerformanceConfig performanceConfig = new EditorPerformanceConfig();
    public final EditorBehaviorConfig behaviorConfig = new EditorBehaviorConfig();

    public EditorConfig() {
    }

    public Paint getPaint() {
        return paint;
    }

    public Typeface getBaseTypeface() {
        return baseTypeface;
    }

    public void setBaseTypeface(Typeface typeface) {
        this.baseTypeface = typeface;
    }

    public void initPaint(float textSize, int color) {
        paint.setTextSize(textSize);
        paint.setColor(color);
        paint.setAntiAlias(true);
        paint.setSubpixelText(true);
        paint.setHinting(Paint.HINTING_ON);
        paint.setUnderlineText(false);
    }

    public void updateLineHeight() {
        lineHeight = paint.getFontSpacing();
    }

    // Delegate methods for easy access
    public boolean isRtl() {
        return visualConfig.isRtl;
    }

    public void setRtl(boolean rtl) {
        visualConfig.isRtl = rtl;
    }

    public boolean isAutoPairingEnabled() {
        return behaviorConfig.isAutoPairingEnabled;
    }

    public boolean isIndentationBlocksEnabled() {
        return behaviorConfig.isIndentationBlocksEnabled;
    }

    public int getPrefetchLines() {
        return performanceConfig.prefetchLines;
    }

    public int getWindowSize() {
        return performanceConfig.windowSize;
    }

    public int getLineWidthCacheSize() {
        return performanceConfig.lineWidthCacheSize;
    }

    public int getColsWidthCacheSize() {
        return performanceConfig.colsWidthCacheSize;
    }

    public long getFlingStopAnimDurationMs() {
        return performanceConfig.flingStopAnimDurationMs;
    }

    public int getLargeEditLines() {
        return performanceConfig.largeEditLines;
    }

    public int getHideCopyCutLines() {
        return performanceConfig.hideCopyCutLines;
    }
}
