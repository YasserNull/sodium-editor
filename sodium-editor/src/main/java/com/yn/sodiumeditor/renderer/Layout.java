package com.yn.sodiumeditor.renderer;

import android.graphics.Paint;
import com.yn.sodiumeditor.SodiumEditor;

/**
 * Layout handles all layout-related logic for SodiumEditor.
 * This includes:
 * - RTL/LTR text direction management
 * - Padding and margin calculations
 * - Text area width/height measurements
 * - Line base X position calculations for RTL
 * - Segment base X position calculations for RTL
 * - Layout direction changes
 */
public class Layout {

    private final SodiumEditor editor;

    // Text direction state
    public boolean isRtl = false;

    // Padding constants
    public float paddingLeft = 0f;
    public float paddingRight = 0f;
    public float paddingTop = 0f;
    public float paddingBottom = 0f;

    // Text area dimensions
    public float textAreaWidth = 0f;
    public float textAreaHeight = 0f;

    public Layout(SodiumEditor editor) {
        this.editor = editor;
    }

    // ============================================================================
    // RTL/LTR Direction Management
    // ============================================================================

    /**
     * Set the layout direction (RTL or LTR).
     * @param isRtl true for RTL, false for LTR
     */
    public void setLayoutDirection(boolean Direction) {
    if (Direction == isRtl) return;
    isRtl = Direction;
    editor.lineNumber.lineNumbersPaint.setTextAlign(editor.textRender.isRtl ? Paint.Align.LEFT : Paint.Align.RIGHT);
    editor.codeFold.animation.foldMarkerPaint.setTextAlign(editor.textRender.isRtl ? Paint.Align.LEFT : Paint.Align.RIGHT);
    editor.lineNumber.invalidateLineNumberCache();
    editor.requestLayout();
    if (editor.wordWrap.isWordWrapEnabled) editor.wordWrap.invalidateWrapMetrics(true);
    editor.scroll.maxScrollXForScroll = 0f;
    editor.scroll.maxTextStartXForScroll = 0f;
    editor.scroll.scrollX =0f;
    editor.scroll.keepCursorVisibleHorizontally();
    editor.invalidate();
  }

    /**
     * Check if the current layout direction is RTL.
     */
    public boolean isRtl() {
        return isRtl;
    }

    /**
     * Check if the current layout direction is LTR.
     */
    public boolean isLtr() {
        return !isRtl;
    }

    // ============================================================================
    // Text Area Calculations
    // ============================================================================

    /**
     * Calculate the text area width (excluding gutter).
     */
    public float calculateTextAreaWidth() {
        float gutterWidth = editor.lineNumber.showLineNumbers ? editor.lineNumber.lineNumbersGutterWidth : 0f;
        textAreaWidth = editor.getWidth() - gutterWidth;
        return textAreaWidth;
    }

    /**
     * Calculate the text area height (excluding any top/bottom padding).
     */
    public float calculateTextAreaHeight() {
        textAreaHeight = editor.getHeight() - paddingTop - paddingBottom;
        return textAreaHeight;
    }

    /**
     * Get the text area width.
     */
    public float getTextAreaWidth() {
        if (textAreaWidth <= 0f) {
            return calculateTextAreaWidth();
        }
        return textAreaWidth;
    }

    /**
     * Get the text area height.
     */
    public float getTextAreaHeight() {
        if (textAreaHeight <= 0f) {
            return calculateTextAreaHeight();
        }
        return textAreaHeight;
    }

    // ============================================================================
    // RTL Base X Calculations
    // ============================================================================

    /**
     * Get the RTL line base X position.
     * This is used to align text to the right in RTL mode.
     * @param line the line text
     * @param globalLine the global line index
     * @return the base X position for RTL text
     */
    public float getRtlLineBaseX(String line, int globalLine) {
        if (!isRtl || line == null) return 0f;
        float totalWidth = editor.textRender.globalMaxLineWidth;
        float lineWidth = editor.highlite.measureHighlightedSegmentWidth(line, globalLine, 0, editor.getLogicalLineLength(globalLine, line));
        return Math.max(0f, totalWidth - lineWidth);
    }

    /**
     * Get the RTL segment base X position.
     * This is used to align a segment of text to the right in RTL mode.
     * @param line the line text
     * @param globalLine the global line index
     * @param segStart the start character index of the segment
     * @param segEnd the end character index of the segment
     * @return the base X position for RTL segment
     */
    public float getRtlSegmentBaseX(String line, int globalLine, int segStart, int segEnd) {
        if (!isRtl || line == null) return 0f;
        float segWidth = editor.highlite.measureHighlightedSegmentWidth(line, globalLine, segStart, segEnd);
        float lineBaseX = getRtlLineBaseX(line, globalLine);
        return lineBaseX + (editor.highlite.measureHighlightedSegmentWidth(line, globalLine, 0, segStart));
    }

    /**
     * Convert an X position from LTR to RTL coordinate space.
     * @param x the X position in LTR space
     * @param line the line text
     * @param globalLine the global line index
     * @return the X position in RTL space
     */
    public float convertXToRtl(float x, String line, int globalLine) {
        if (!isRtl) return x;
        float baseX = getRtlLineBaseX(line, globalLine);
        float lineWidth = editor.highlite.measureHighlightedSegmentWidth(line, globalLine, 0, editor.getLogicalLineLength(globalLine, line));
        return baseX + (lineWidth - x);
    }

    /**
     * Convert an X position from RTL to LTR coordinate space.
     * @param x the X position in RTL space
     * @param line the line text
     * @param globalLine the global line index
     * @return the X position in LTR space
     */
    public float convertXToLtr(float x, String line, int globalLine) {
        if (!isRtl) return x;
        float baseX = getRtlLineBaseX(line, globalLine);
        float lineWidth = editor.highlite.measureHighlightedSegmentWidth(line, globalLine, 0, editor.getLogicalLineLength(globalLine, line));
        return lineWidth - (x - baseX);
    }

    // ============================================================================
    // Padding and Margins
    // ============================================================================

    /**
     * Set all padding values.
     */
    public void setPadding(float left, float top, float right, float bottom) {
        this.paddingLeft = left;
        this.paddingTop = top;
        this.paddingRight = right;
        this.paddingBottom = bottom;
        editor.requestLayout();
        editor.invalidate();
    }

    /**
     * Set the left padding.
     */
    public void setPaddingLeft(float padding) {
        this.paddingLeft = padding;
        editor.requestLayout();
        editor.invalidate();
    }

    /**
     * Set the right padding.
     */
    public void setPaddingRight(float padding) {
        this.paddingRight = padding;
        editor.requestLayout();
        editor.invalidate();
    }

    /**
     * Set the top padding.
     */
    public void setPaddingTop(float padding) {
        this.paddingTop = padding;
        editor.requestLayout();
        editor.invalidate();
    }

    /**
     * Set the bottom padding.
     */
    public void setPaddingBottom(float padding) {
        this.paddingBottom = padding;
        editor.requestLayout();
        editor.invalidate();
    }

    /**
     * Get the effective left padding (includes gutter in LTR mode).
     */
    public float getEffectivePaddingLeft() {
        if (isRtl) {
            return paddingLeft;
        } else {
            return paddingLeft + editor.lineNumber.lineNumbersGutterWidth;
        }
    }

    /**
     * Get the effective right padding (includes gutter in RTL mode).
     */
    public float getEffectivePaddingRight() {
        if (isRtl) {
            return paddingRight + editor.lineNumber.lineNumbersGutterWidth;
        } else {
            return paddingRight;
        }
    }

    // ============================================================================
    // Text Start X Calculation
    // ============================================================================

    /**
     * Get the text start X position (where text drawing begins).
     * This accounts for gutter width and padding based on the current layout direction.
     */
    public float getTextStartX() {
        if (isRtl) {
            return paddingLeft;
        } else {
            return paddingLeft + editor.lineNumber.lineNumbersGutterWidth;
        }
    }

    // ============================================================================
    // Layout Invalidation
    // ============================================================================

    /**
     * Invalidate all layout caches and trigger a full re-layout.
     */
    public void invalidateLayout() {
        textAreaWidth = 0f;
        textAreaHeight = 0f;
        editor.textRender.currentMaxWindowLineWidth = 0f;
        editor.textRender.globalMaxLineWidth = 0f;
        editor.scroll.maxLineWidthForScroll = 0f;
        editor.scroll.maxTextStartXForScroll = 0f;
        editor.scroll.maxScrollXForScroll = 0f;
        editor.recalculateMaxLineWidth();
        editor.requestLayout();
        editor.invalidate();
    }

    /**
     * Update text area dimensions after a layout change.
     */
    public void updateTextAreaDimensions() {
        calculateTextAreaWidth();
        calculateTextAreaHeight();
    }
}
