package com.yn.sodiumeditor.config;

/**
 * Configuration class for line numbers.
 * Stores line number settings such as colors, widths, and padding.
 */
public class LineNumberConfig {

    public static final float GUTTER_TEXT_PADDING = 20f;

    private int currentLineNumberColor = 0xFF2196F3;
    private int lineNumberColor = 0xFF888888;
    private int gutterBackgroundColor = 0xFFFAFAFA;
    private int gutterSeparatorColor = 0xFF555555;
    private float gutterSeparatorWidth = 0f;

    public LineNumberConfig() {
    }

    public int getCurrentLineNumberColor() {
        return currentLineNumberColor;
    }

    public void setCurrentLineNumberColor(int color) {
        this.currentLineNumberColor = color;
    }

    public int getLineNumberColor() {
        return lineNumberColor;
    }

    public void setLineNumberColor(int color) {
        this.lineNumberColor = color;
    }

    public int getGutterBackgroundColor() {
        return gutterBackgroundColor;
    }

    public void setGutterBackgroundColor(int color) {
        this.gutterBackgroundColor = color;
    }

    public int getGutterSeparatorColor() {
        return gutterSeparatorColor;
    }

    public void setGutterSeparatorColor(int color) {
        this.gutterSeparatorColor = color;
    }

    public float getGutterSeparatorWidth() {
        return gutterSeparatorWidth;
    }

    public void setGutterSeparatorWidth(float width) {
        this.gutterSeparatorWidth = Math.max(0f, width);
    }

    public float getGutterTextPadding() {
        return GUTTER_TEXT_PADDING;
    }
}
