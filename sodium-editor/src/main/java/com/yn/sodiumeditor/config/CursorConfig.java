package com.yn.sodiumeditor.config;

import android.graphics.Paint;

/**
 * Configuration class for cursor functionality.
 * Manages cursor appearance settings.
 */
public final class CursorConfig {
    private final Paint caretPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    public CursorConfig() {
        caretPaint.setStyle(Paint.Style.STROKE);
        caretPaint.setStrokeCap(Paint.Cap.BUTT);
    }

    public Paint getCaretPaint() {
        return caretPaint;
    }
}
