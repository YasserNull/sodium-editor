package com.yn.sodiumeditor.renderer;

import android.graphics.Paint;
import androidx.annotation.Nullable;
import com.yn.sodiumeditor.SodiumEditor;
import com.yn.sodiumeditor.core.HitAdvance;
import com.yn.sodiumeditor.renderer.HighliteRender;
import java.util.List;

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

    public final com.yn.sodiumeditor.core.HitAdvance lastHitAdvance =
        new com.yn.sodiumeditor.core.HitAdvance();

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
    // Guide and Whitespace Calculations
    // ============================================================================

    public float getGuideXForColumn(String line, int column, int globalLine) {
        if (line == null) line = "";
        if (column <= line.length()) {
            return editor.textRender.measureText(line, column, globalLine);
        }
        float base = editor.textRender.measureText(line, line.length(), globalLine);
        float spaceWidth = editor.textRender.getVisualSpaceWidth(editor.textRender.paint);
        return base + spaceWidth * (column - line.length());
    }

    public boolean isWhitespaceAtX(String line, int globalLine, float x) {
        if (line == null || line.isEmpty()) return true;
        if (x <= 0f) return Character.isWhitespace(line.charAt(0));

        List<HighliteRender.HighlightSpan> spans = editor.highlite.highlightCache.get(globalLine);
        if (spans == null) {
            spans = editor.highlite.calculateSpansForLine(line, globalLine);
            editor.highlite.highlightCache.put(globalLine, spans);
        }

        final int len = line.length();
        float currentX = 0f;
        boolean prevWhitespace = false;
        final float eps = 0.25f;

        int pos = 0;
        if (spans != null && !spans.isEmpty()) {
            for (HighliteRender.HighlightSpan span : spans) {
                if (pos >= len) break;
                if (span.end <= pos) continue;
                if (span.start > pos) {
                    if (hitTestWhitespaceSegment(line, pos, Math.min(span.start, len), globalLine, x, editor.textRender.paint, eps, currentX, prevWhitespace)) {
                        if (isGuideHitOnWhitespaceBoundary(line, x)) return false;
                        return true;
                    }
                    HitAdvance a = editor.layout.lastHitAdvance;
                    if (a.hit) return a.isWhitespace;
                    currentX = a.x;
                    prevWhitespace = a.prevWhitespace;
                    pos = a.pos;
                }
                int segStart = Math.max(pos, span.start);
                int segEnd = Math.min(len, span.end);
                if (segEnd > segStart) {
                    if (hitTestWhitespaceSegment(line, segStart, segEnd, globalLine, x, span.paint, eps, currentX, prevWhitespace)) {
                        if (isGuideHitOnWhitespaceBoundary(line, x)) return false;
                        return true;
                    }
                    HitAdvance a = editor.layout.lastHitAdvance;
                    if (a.hit) return a.isWhitespace;
                    currentX = a.x;
                    prevWhitespace = a.prevWhitespace;
                    pos = a.pos;
                }
            }
        }

        if (pos < len) {
            if (hitTestWhitespaceSegment(line, pos, len, globalLine, x, editor.textRender.paint, eps, currentX, prevWhitespace)) {
                if (isGuideHitOnWhitespaceBoundary(line, x)) return false;
                return true;
            }
        }

        return true;
    }

    public boolean isGuideHitOnWhitespaceBoundary(String line, float x) {
        if (!editor.layout.lastHitAdvance.hit || !editor.layout.lastHitAdvance.isWhitespace) return false;
        final float boundaryEps = 0.6f;
        if (editor.layout.lastHitAdvance.hitCharEndX - x > boundaryEps) return false;
        int next = editor.layout.lastHitAdvance.pos + 1;
        if (next >= line.length()) return false;
        return !Character.isWhitespace(line.charAt(next));
    }

    public boolean hitTestWhitespaceSegment(String line, int start, int end, int globalLine, float x, Paint p, float eps, float startX, boolean prevWhitespace) {
        editor.layout.lastHitAdvance.hit = false;
        editor.layout.lastHitAdvance.isWhitespace = false;
        editor.layout.lastHitAdvance.x = startX;
        editor.layout.lastHitAdvance.pos = start;
        editor.layout.lastHitAdvance.prevWhitespace = prevWhitespace;

        if (start >= end) return false;

        int segLen = end - start;
        if (editor.view.measureWidthBuffer == null || editor.view.measureWidthBuffer.length < segLen) {
            editor.view.measureWidthBuffer = new float[Math.max(segLen, 64)];
        }
        p.getTextWidths(line, start, end, editor.view.measureWidthBuffer);

        float currentX = startX;
        boolean prevWs = prevWhitespace;
        for (int i = 0; i < segLen; i++) {
            int idx = start + i;
            char c = line.charAt(idx);
            float adv = editor.textRender.getCharAdvanceWidth(c, editor.view.measureWidthBuffer[i], p);
            float nextX = currentX + adv;

            if (x <= nextX + eps) {
                boolean ws = Character.isWhitespace(c);
                editor.layout.lastHitAdvance.hit = true;
                editor.layout.lastHitAdvance.isWhitespace = ws;
                editor.layout.lastHitAdvance.x = currentX;
                editor.layout.lastHitAdvance.hitCharEndX = nextX;
                editor.layout.lastHitAdvance.pos = idx;
                editor.layout.lastHitAdvance.prevWhitespace = prevWs;
                return ws;
            }
            currentX = nextX;
            prevWs = Character.isWhitespace(c);
        }

        editor.layout.lastHitAdvance.x = currentX;
        editor.layout.lastHitAdvance.pos = end;
        editor.layout.lastHitAdvance.prevWhitespace = prevWs;
        return false;
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
    public float getRtlLineBaseX(@Nullable String line, int globalLine) {
        if (!isRtl || line == null) return 0f;
        int logicalLen = editor.view.getLogicalLineLength(globalLine, line);
        float w = editor.highlite.measureHighlightedSegmentWidth(line, globalLine, 0, logicalLen);
        float area = getTextAreaWidth();
        return area - w;
    }

    public float getRtlSegmentBaseX(@Nullable String line, int globalLine, int segStart, int segEnd) {
        if (!isRtl || line == null) return 0f;
        float w = editor.highlite.measureHighlightedSegmentWidth(line, globalLine, segStart, segEnd);
        float area = getTextAreaWidth();
        return area - w;
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
        float lineWidth = editor.highlite.measureHighlightedSegmentWidth(line, globalLine, 0, editor.view.getLogicalLineLength(globalLine, line));
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
        float lineWidth = editor.highlite.measureHighlightedSegmentWidth(line, globalLine, 0, editor.view.getLogicalLineLength(globalLine, line));
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
        editor.windowRender.currentMaxWindowLineWidth = 0f;
        editor.windowRender.globalMaxLineWidth = 0f;
        editor.scroll.maxLineWidthForScroll = 0f;
        editor.scroll.maxTextStartXForScroll = 0f;
        editor.scroll.maxScrollXForScroll = 0f;
        editor.windowRender.recalculateMaxLineWidth();
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

    public float getViewXForLineChar(String line, int globalLine, int ch) {
        if (line == null) line = "";
        int safeChar = Math.max(0, Math.min(ch, editor.view.getLogicalLineLength(globalLine, line)));
        if (!editor.wordWrap.isWordWrapEnabled) {
            return editor.layout.getTextStartX()
                    + editor.textRender.measureText(line, safeChar, globalLine)
                    - editor.scroll.getEffectiveScrollX();
        }
        int[] starts = editor.wordWrap.getWrapStartsForLine(globalLine, line);
        int seg = editor.wordWrap.getWrapSegmentIndexForChar(starts, safeChar);
        int segStart = editor.wordWrap.getWrapSegmentStart(starts, seg);
        float x = editor.textRender.measureTextWithVisualSpaces(line, segStart, safeChar, editor.textRender.paint);
        return editor.layout.getTextStartX() + x - editor.scroll.getEffectiveScrollX();
    }

    public float getViewYTopForLineChar(int globalLine, int ch) {
        int v = editor.wordWrap.getVisualIndexForLineAndChar(globalLine, ch);
        return v * editor.textRender.lineHeight - editor.scroll.scrollY;
    }
}
