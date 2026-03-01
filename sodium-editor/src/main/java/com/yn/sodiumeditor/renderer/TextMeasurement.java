package com.yn.sodiumeditor.renderer;

import android.graphics.Paint;
import androidx.annotation.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.yn.sodiumeditor.SodiumEditorView;
import com.yn.sodiumeditor.core.HighlightParser;

/**
 * Handles text measurement and calculation operations for the text editor.
 */
public final class TextMeasurement {
    private final SodiumEditorView view;

    public TextMeasurement(SodiumEditorView view) {
        this.view = view;
    }

    /**
     * Computes and caches the width for a specific line.
     */
    public void computeWidthForLine(int globalIndex, String line) {
        String safe = (line == null) ? "" : line;
        float w;
        int logicalLen = view.getLogicalLineLength(globalIndex, safe);
        if (logicalLen > view.highlightState.maxSyntaxLineLength) {
            w = view.highlightRenderer.getAverageCharWidthForLine(safe, globalIndex) * logicalLen;
        } else {
            w = view.whitespaceGuideRenderer.measureTextWithVisualSpaces(view, safe, 0, safe.length(), view.paint);
        }
        synchronized (view.lineWidthCache) {
            view.lineWidthCache.put(globalIndex, w);
        }
    }

    /**
     * Gets the cached width for a line, computing it if not cached.
     */
    public float getWidthForLine(int globalIndex, String line) {
        synchronized (view.lineWidthCache) {
            Float v = view.lineWidthCache.get(globalIndex);
            if (v != null) return v;
        }
        String safe = (line == null) ? "" : line;
        float w;
        int logicalLen = view.getLogicalLineLength(globalIndex, safe);
        if (logicalLen > view.highlightState.maxSyntaxLineLength) {
            w = view.highlightRenderer.getAverageCharWidthForLine(safe, globalIndex) * logicalLen;
        } else {
            w = view.whitespaceGuideRenderer.measureTextWithVisualSpaces(view, safe, 0, safe.length(), view.paint);
        }
        synchronized (view.lineWidthCache) {
            view.lineWidthCache.put(globalIndex, w);
        }
        return w;
    }

    /**
     * Gets the character index at a given X position on a line.
     */
    public int getCharIndexForX(String text, float x, int globalLine) {
        if (text == null || text.isEmpty()) return 0;
        if (view.isRtl) {
            float baseX = getRtlLineBaseX(text, globalLine);
            x -= baseX;
            float w = view.highlightRenderer.measureHighlightedSegmentWidth(
                text, globalLine, 0, view.getLogicalLineLength(globalLine, text));
            x = w - x;
        }
        if (x <= 0f) return 0;

        int len = view.getLogicalLineLength(globalLine, text);
        if (len > view.highlightState.maxSyntaxLineLength) {
            float avg = view.highlightRenderer.getAverageCharWidthForLine(text, globalLine);
            if (avg <= 0f) return 0;
            int idx = (int) Math.round(x / avg);
            return Math.max(0, Math.min(idx, len));
        }
        int textLen = text.length();
        if (view.getVisualSpaceScale() == 1) {
            int count = view.paint.breakText(text, true, x, null);
            if (count <= 0) return 0;
            if (count >= textLen) return textLen;

            float wPrev = (count > 1) ? view.paint.measureText(text, 0, count - 1) : 0f;
            float wCount = view.paint.measureText(text, 0, count);
            float mid = wPrev + (wCount - wPrev) * 0.5f;
            return (x < mid) ? (count - 1) : count;
        }

        float[] widths = view.whitespaceGuideState.ensureMeasureWidthBuffer(textLen);
        view.paint.getTextWidths(text, 0, textLen, widths);
        float current = 0f;
        for (int i = 0; i < textLen; i++) {
            float adv = view.whitespaceGuideRenderer.getCharAdvanceWidth(text.charAt(i), widths[i], view.paint, com.yn.sodiumeditor.core.WrapWordEngine.DEFAULT_TAB_SIZE_SPACES);
            float mid = current + adv * 0.5f;
            if (x < mid) return i;
            if (x < current + adv) return i + 1;
            current += adv;
        }
        return textLen;
    }

    /**
     * Gets the character index at a given X position within a specific range.
     */
    public int getCharIndexForXInRange(String text, int globalLine, int start, int end, float x) {
        if (text == null || text.isEmpty()) return 0;
        start = Math.max(0, Math.min(start, text.length()));
        end = Math.max(start, Math.min(end, text.length()));
        if (view.isRtl) {
            float baseX = getRtlSegmentBaseX(text, globalLine, start, end);
            x -= baseX;
            float w = view.highlightRenderer.measureHighlightedSegmentWidth(text, globalLine, start, end);
            x = w - x;
        }
        if (x <= 0f) return start;
        int len = end - start;
        if (len <= 0) return start;
        if (view.getVisualSpaceScale() == 1) {
            int count = view.paint.breakText(text, start, end, true, x, null);
            int idx = start + Math.max(0, count);
            return Math.min(idx, end);
        }
        float[] widths = view.whitespaceGuideState.ensureMeasureWidthBuffer(len);
        view.paint.getTextWidths(text, start, end, widths);
        float current = 0f;
        for (int i = 0; i < len; i++) {
            float adv = view.whitespaceGuideRenderer.getCharAdvanceWidth(text.charAt(start + i), widths[i], view.paint, com.yn.sodiumeditor.core.WrapWordEngine.DEFAULT_TAB_SIZE_SPACES);
            float mid = current + adv * 0.5f;
            if (x < mid) return start + i;
            if (x < current + adv) return start + i + 1;
            current += adv;
        }
        return end;
    }

    /**
     * Gets the X position of the caret for a given line and character index.
     */
    public float getCaretXForLine(String line, int globalLine, int charIndex) {
        float x = view.highlightRenderer.measureText(line, charIndex, globalLine);
        if (!view.isRtl) return x;
        int logicalLen = view.getLogicalLineLength(globalLine, line);
        float w = view.highlightRenderer.measureHighlightedSegmentWidth(line, globalLine, 0, logicalLen);
        float baseX = getRtlLineBaseX(line, globalLine);
        return baseX + (w - x);
    }

    /**
     * Gets the X position of the caret for a segment within a line.
     */
    public float getCaretXForSegment(String line, int globalLine, int segStart, int segEnd, int charIndex) {
        float xRel = view.whitespaceGuideRenderer.measureTextWithVisualSpaces(view, line, segStart, charIndex, view.paint);
        if (!view.isRtl) return xRel;
        float w = view.highlightRenderer.measureHighlightedSegmentWidth(line, globalLine, segStart, segEnd);
        float baseX = getRtlSegmentBaseX(line, globalLine, segStart, segEnd);
        return baseX + (w - xRel);
    }

    /**
     * Gets the base X position for RTL line rendering.
     */
    public float getRtlLineBaseX(@Nullable String line, int globalLine) {
        if (!view.isRtl || line == null) return 0f;
        int logicalLen = view.getLogicalLineLength(globalLine, line);
        float w = view.highlightRenderer.measureHighlightedSegmentWidth(line, globalLine, 0, logicalLen);
        float area = view.getTextAreaWidth();
        return area - w;
    }

    /**
     * Gets the base X position for RTL segment rendering.
     */
    public float getRtlSegmentBaseX(@Nullable String line, int globalLine, int segStart, int segEnd) {
        if (!view.isRtl || line == null) return 0f;
        float w = view.highlightRenderer.measureHighlightedSegmentWidth(line, globalLine, segStart, segEnd);
        float area = view.getTextAreaWidth();
        return area - w;
    }

    /**
     * Computes the visible character range for a line (optimized version).
     */
    public void getVisibleCharRangeForLine(String line, int globalLine, int[] out) {
        if (line == null || out == null || out.length < 2) return;
        int len = view.getLogicalLineLength(globalLine, line);
        if (len <= 0) {
            out[0] = 0;
            out[1] = 0;
            return;
        }
        if (len > view.highlightState.maxSyntaxLineLength) {
            getVisibleCharRangeForLineFast(line, globalLine, len, out);
            return;
        }
        if (view.isStableGlyphPositionsEnabled) {
            out[0] = 0;
            out[1] = len;
            return;
        }
        float viewLeft = view.lineNumberRenderer.getContentViewLeft(view.isRtl);
        float viewRight = view.lineNumberRenderer.getContentViewRight(view.getWidth(), view.isRtl);
        float leftX = viewLeft + view.getEffectiveScrollX() - view.getTextStartX();
        float rightX = viewRight + view.getEffectiveScrollX() - view.getTextStartX();

        int start = getCharIndexForX(line, leftX, globalLine);
        int end = getCharIndexForX(line, rightX, globalLine);
        if (end < start) {
            int t = start;
            start = end;
            end = t;
        }

        int pad = view.visibleCharPadding;
        start = Math.max(0, start - pad);
        end = Math.min(len, end + pad);
        out[0] = start;
        out[1] = end;
    }

    /**
     * Computes the visible character range for a line (fast version for long lines).
     */
    public void getVisibleCharRangeForLineFast(String line, int globalLine, int lineLength, int[] out) {
        int len = Math.max(0, lineLength);
        if (len <= 0) {
            out[0] = 0;
            out[1] = 0;
            return;
        }
        float avg = view.highlightRenderer.getAverageCharWidthForLine(line, globalLine);
        if (avg <= 0f) {
            out[0] = 0;
            out[1] = Math.min(len, Math.max(0, view.prefetchCols));
            return;
        }
        float viewLeft = view.lineNumberRenderer.getContentViewLeft(view.isRtl);
        float viewRight = view.lineNumberRenderer.getContentViewRight(view.getWidth(), view.isRtl);
        float leftX = viewLeft + view.getEffectiveScrollX() - view.getTextStartX();
        float rightX = viewRight + view.getEffectiveScrollX() - view.getTextStartX();
        if (view.isRtl) {
            float w = avg * len;
            float baseX = view.getTextAreaWidth() - w;
            float l = leftX - baseX;
            float r = rightX - baseX;
            leftX = w - l;
            rightX = w - r;
        }
        int start = (int) Math.floor(leftX / avg);
        int end = (int) Math.ceil(rightX / avg);
        if (end < start) {
            int t = start;
            start = end;
            end = t;
        }
        int pad = view.visibleCharPadding + Math.max(0, view.prefetchCols);
        start = Math.max(0, start - pad);
        end = Math.min(len, end + pad);
        out[0] = start;
        out[1] = end;
    }

    /**
     * Checks if the character at a given X position is whitespace.
     */
    public boolean isWhitespaceAtX(String line, int globalLine, float x) {
        if (line == null || line.isEmpty()) return true;
        if (x <= 0f) return Character.isWhitespace(line.charAt(0));

        List<com.yn.sodiumeditor.state.HighlightSpan> spans = view.highlightState.highlightCache.get(globalLine);
        if (spans == null) {
            spans = view.highlightRenderer.calculateSpansForLine(line, globalLine);
            view.highlightState.highlightCache.put(globalLine, spans);
        }

        final int len = line.length();
        float currentX = 0f;
        final float eps = 0.25f;

        int pos = 0;
        if (spans != null && !spans.isEmpty()) {
            for (com.yn.sodiumeditor.state.HighlightSpan span : spans) {
                if (pos >= len) break;
                if (span.end <= pos) continue;
                if (span.start > pos) {
                    for (int i = pos; i < Math.min(span.start, len); i++) {
                        float adv = view.whitespaceGuideRenderer.measureTextWithVisualSpaces(view, line, i, i + 1, view.paint);
                        if (x >= currentX - eps && x <= currentX + adv + eps) {
                            return Character.isWhitespace(line.charAt(i));
                        }
                        currentX += adv;
                    }
                }
                int start = Math.max(pos, span.start);
                int end = Math.min(len, span.end);
                for (int i = start; i < end; i++) {
                    float adv = view.whitespaceGuideRenderer.measureTextWithVisualSpaces(view, line, i, i + 1, view.paint);
                    if (x >= currentX - eps && x <= currentX + adv + eps) {
                        return Character.isWhitespace(line.charAt(i));
                    }
                    currentX += adv;
                }
                pos = Math.max(pos, end);
            }
        }

        if (pos < len) {
            for (int i = pos; i < len; i++) {
                float adv = view.whitespaceGuideRenderer.measureTextWithVisualSpaces(view, line, i, i + 1, view.paint);
                if (x >= currentX - eps && x <= currentX + adv + eps) {
                    return Character.isWhitespace(line.charAt(i));
                }
                currentX += adv;
            }
        }

        return true;
    }
}
