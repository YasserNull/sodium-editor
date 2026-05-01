package com.yn.sodiumeditor.renderer.draw;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import com.yn.sodiumeditor.SodiumEditor;
import com.yn.sodiumeditor.core.binary.BinaryTokenConverter;
import com.yn.sodiumeditor.utils.FunctionLog;

/**
 * BinaryLineDrawer handles fast binary line drawing.
 * This includes:
 * - Drawing binary lines with token boxes
 * - Drawing individual token boxes
 * - Calculating X positions for binary tokens
 * - Cursor snapping for binary tokens
 */
public class BinaryLineDrawer {

    private final SodiumEditor editor;
    private final BinaryTokenConverter tokenConverter;

    // Paint objects
    private final Paint binaryTokenFillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint binaryTokenStrokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint binaryTokenTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    // Configuration
    public boolean binaryTokenBoxEnabled = true;
    public int binaryTokenFillColor = 0xFFFF0000;
    public int binaryTokenStrokeColor = 0xFF000000;
    public int binaryTokenTextColor = 0xFFFF3B30;
    public float binaryTokenStrokeWidth = 1f;
    public float binaryTokenPaddingX = 2f;
    public float binaryTokenPaddingY = 2f;
    public float binaryTokenCornerRadius = 10f;
    public boolean binaryCaretNotationEnabled = false;

    // Cached character width for fast binary rendering
    private float cachedCharWidth = 0f;

    // ThreadLocal temporary arrays to reduce allocations
    private static final ThreadLocal<int[]> TL_SPAN_TMP_2 = ThreadLocal.withInitial(() -> new int[2]);
    private static final ThreadLocal<int[]> TL_SPAN_TMP_4 = ThreadLocal.withInitial(() -> new int[4]);
    private static final ThreadLocal<float[]> TL_FLOAT_TMP_1 = ThreadLocal.withInitial(() -> new float[1]);
    private static final ThreadLocal<Paint.FontMetrics> TL_FONT_METRICS = ThreadLocal.withInitial(Paint.FontMetrics::new);

    public BinaryLineDrawer(SodiumEditor editor, BinaryTokenConverter tokenConverter) {
        FunctionLog.f("BinaryLineDrawer", "BinaryLineDrawer", editor, tokenConverter);
        this.editor = editor;
        this.tokenConverter = tokenConverter;

        binaryTokenFillPaint.setStyle(Paint.Style.FILL);
        binaryTokenFillPaint.setColor(binaryTokenFillColor);
        binaryTokenStrokePaint.setStyle(Paint.Style.STROKE);
        binaryTokenStrokePaint.setColor(binaryTokenStrokeColor);
        binaryTokenStrokePaint.setStrokeWidth(binaryTokenStrokeWidth);
        binaryTokenTextPaint.setColor(binaryTokenTextColor);
        binaryTokenTextPaint.setAntiAlias(true);
    }

    // ── Cached Character Width Management ──────────────────────────────────────
    /**
     * Update cached character width when text size or typeface changes.
     * Call this whenever the main paint changes.
     */
    public void updateCachedCharWidth(Paint paint) {
        FunctionLog.f("BinaryLineDrawer", "updateCachedCharWidth", paint);
        if (paint != null) {
            cachedCharWidth = paint.measureText("M");
        }
    }

    public float getCachedCharWidth() {
        FunctionLog.f("BinaryLineDrawer", "getCachedCharWidth");
        return cachedCharWidth > 0f ? cachedCharWidth : editor.textRender.paint.measureText("M");
    }

    public void setCachedCharWidth(float width) {
        FunctionLog.f("BinaryLineDrawer", "setCachedCharWidth", width);
        this.cachedCharWidth = Math.max(1f, width);
    }

    // ── Configuration setters ──────────────────────────────────────────────────
    public void setBinaryTokenBoxEnabled(boolean enabled) {
        FunctionLog.f("BinaryLineDrawer", "setBinaryTokenBoxEnabled", enabled);
        binaryTokenBoxEnabled = enabled;
    }

    public void setBinaryTokenFillColor(int color) {
        FunctionLog.f("BinaryLineDrawer", "setBinaryTokenFillColor", color);
        binaryTokenFillColor = color;
        binaryTokenFillPaint.setColor(color);
    }

    public void setBinaryTokenStrokeColor(int color) {
        FunctionLog.f("BinaryLineDrawer", "setBinaryTokenStrokeColor", color);
        binaryTokenStrokeColor = color;
        binaryTokenStrokePaint.setColor(color);
    }

    public void setBinaryTokenStrokeWidth(float widthPx) {
        FunctionLog.f("BinaryLineDrawer", "setBinaryTokenStrokeWidth", widthPx);
        binaryTokenStrokeWidth = Math.max(0.5f, widthPx);
        binaryTokenStrokePaint.setStrokeWidth(binaryTokenStrokeWidth);
    }

    public void setBinaryTokenBoxPadding(float paddingX, float paddingY) {
        FunctionLog.f("BinaryLineDrawer", "setBinaryTokenBoxPadding", paddingX, paddingY);
        binaryTokenPaddingX = Math.max(0f, paddingX);
        binaryTokenPaddingY = Math.max(0f, paddingY);
    }

    public void setBinaryTokenCornerRadius(float radiusPx) {
        FunctionLog.f("BinaryLineDrawer", "setBinaryTokenCornerRadius", radiusPx);
        binaryTokenCornerRadius = Math.max(0f, radiusPx);
    }

    public void setBinaryTokenTextColor(int color) {
        FunctionLog.f("BinaryLineDrawer", "setBinaryTokenTextColor", color);
        binaryTokenTextColor = color;
        binaryTokenTextPaint.setColor(color);
    }

    public void setBinaryCaretNotationEnabled(boolean enabled) {
        FunctionLog.f("BinaryLineDrawer", "setBinaryCaretNotationEnabled", enabled);
        binaryCaretNotationEnabled = enabled;
    }

    public Paint getBinaryTokenFillPaint() { 
        FunctionLog.f("BinaryLineDrawer", "getBinaryTokenFillPaint");
        return binaryTokenFillPaint; 
    }
    public Paint getBinaryTokenStrokePaint() { 
        FunctionLog.f("BinaryLineDrawer", "getBinaryTokenStrokePaint");
        return binaryTokenStrokePaint; 
    }

    // ── Cursor helpers ─────────────────────────────────────────────────────────
    public int snapBinaryCursor(String line, int index, int lineIndex, android.util.SparseArray<int[]> binaryTokenSpans) {
        FunctionLog.f("BinaryLineDrawer", "snapBinaryCursor", line, index, lineIndex, binaryTokenSpans);
        int[] spans = binaryTokenSpans.get(lineIndex);
        int[] span = new int[2];
        if (spans != null && tokenConverter.findBinaryTokenSpanInSpans(spans, index, span)) {
            int start = span[0];
            int end = span[1];
            if (index <= start) return start;
            if (index >= end) return end;
            int leftDist = index - start;
            int rightDist = end - index;
            return (leftDist <= rightDist) ? start : end;
        }
        if (!tokenConverter.findBinaryTokenSpan(line, index, span)) return index;
        int start = span[0];
        int end = span[1];
        if (index <= start) return start;
        if (index >= end) return end;
        int leftDist = index - start;
        int rightDist = end - index;
        return (leftDist <= rightDist) ? start : end;
    }

    public int getCharIndexForXBinary(
        String line, int start, int end, float x, Paint paint, int[] spans, float padX) {
        FunctionLog.f("BinaryLineDrawer", "getCharIndexForXBinary", line, start, end, x, paint, spans, padX);
        if (line == null) return start;
        int len = line.length();
        start = Math.max(0, Math.min(start, len));
        end = Math.max(start, Math.min(end, len));
        if (x <= 0f) return start;
        if (start >= end) return start;

        // Use cached character width instead of measuring
        float charWidth = getCachedCharWidth();

        if (spans == null || spans.length == 0) {
            int idx = start + (int) Math.round(x / charWidth);
            return Math.min(idx, end);
        }

        float current = 0f;
        int idx = start;

        for (int i = 0; i + 1 < spans.length; i += 2) {
            int s = spans[i];
            int e = spans[i + 1];
            if (e <= start) continue;
            if (s >= end) break;
            int safeS = Math.max(start, s);
            int safeE = Math.min(end, e);

            if (safeS > idx) {
                float w = (safeS - idx) * charWidth;
                if (x < current + w) {
                    int count = (int) Math.round((x - current) / charWidth);
                    return idx + count;
                }
                current += w;
                idx = safeS;
            }

            if (safeS < safeE) {
                float tokenWidth = (safeE - safeS) * charWidth + (padX * 2f);
                float mid = current + tokenWidth * 0.5f;
                if (x < current + tokenWidth) {
                    return (x < mid) ? safeS : safeE;
                }
                current += tokenWidth;
                idx = safeE;
            }
        }

        if (idx < end) {
            float w = (end - idx) * charWidth;
            if (x < current + w) {
                int count = (int) Math.round((x - current) / charWidth);
                return idx + count;
            }
        }
        return end;
    }

    public float getXForCharBinary(String line, int charIndex, Paint paint, int[] spans, float padX) {
        FunctionLog.f("BinaryLineDrawer", "getXForCharBinary", line, charIndex, paint, spans, padX);
        if (line == null) return 0f;
        int len = line.length();
        int idx = Math.max(0, Math.min(charIndex, len));

        // Use cached character width instead of measuring
        float charWidth = getCachedCharWidth();

        if (spans == null || spans.length == 0 || idx == 0) {
            return idx * charWidth;
        }
        float current = 0f;
        int pos = 0;
        for (int i = 0; i + 1 < spans.length; i += 2) {
            int s = spans[i];
            int e = spans[i + 1];
            if (e <= pos) continue;
            if (s > len) break;
            if (s > pos) {
                if (idx <= s) {
                    return current + (idx - pos) * charWidth;
                }
                current += (s - pos) * charWidth;
                pos = s;
            }
            if (idx <= e) {
                float tokenWidth = (e - s) * charWidth + (padX * 2f);
                return (idx <= s) ? current : (idx >= e ? current + tokenWidth : current);
            }
            float tokenWidth = (e - s) * charWidth + (padX * 2f);
            current += tokenWidth;
            pos = e;
        }
        if (idx > pos) {
            current += (idx - pos) * charWidth;
        }
        return current;
    }

    // ── Fast Binary Line Drawing ───────────────────────────────────────────────
    /**
     * Optimized binary line drawing method with fast path.
     * Uses cached character width and avoids all unnecessary measurements.
     */
    public void drawBinaryLine(Canvas canvas, String line, int globalLine, float y, Paint defaultPaint, android.util.SparseArray<int[]> binaryTokenSpans) {
        FunctionLog.f("BinaryLineDrawer", "drawBinaryLine", canvas, line, globalLine, y, defaultPaint, binaryTokenSpans);
        drawBinaryLineSlice(canvas, line, globalLine, 0, line.length(), 0, y, defaultPaint, binaryTokenSpans);
    }

    /**
     * Draw a slice of a binary line.
     * @param canvas the canvas to draw on
     * @param line the line text
     * @param globalLine the global line index
     * @param relStart the start character index relative to the slice
     * @param relEnd the end character index relative to the slice
     * @param sliceStart the absolute start character index of the slice
     * @param y the Y position
     * @param defaultPaint the default paint to use
     * @param binaryTokenSpans the token spans array
     */
    public void drawBinaryLineSlice(Canvas canvas, String line, int globalLine, int relStart, int relEnd, int sliceStart, float y, Paint defaultPaint, android.util.SparseArray<int[]> binaryTokenSpans) {
        FunctionLog.f("BinaryLineDrawer", "drawBinaryLineSlice", canvas, line, globalLine, relStart, relEnd, sliceStart, y, defaultPaint, binaryTokenSpans);
        if (line == null || relStart >= relEnd) return;

        int len = line.length();
        int[] spans = binaryTokenSpans.get(globalLine);

        // Use cached character width
        float charWidth = getCachedCharWidth();
        float padX = binaryCaretNotationEnabled ? 0f : binaryTokenPaddingX;

        // Fast path: no spans, draw entire slice at once
        if (spans == null || spans.length == 0) {
            canvas.drawText(line, relStart, relEnd, 0f, y, defaultPaint);
            return;
        }

        // Reuse temporary rectangle from editor
        RectF rect = editor.highliteRender.binaryTokenRect;
        Paint.FontMetrics fm = TL_FONT_METRICS.get();
        defaultPaint.getFontMetrics(fm);
        float boxTop = y + fm.ascent - binaryTokenPaddingY;
        float boxBottom = y + fm.descent + binaryTokenPaddingY;

        float x = 0f;
        int idx = relStart;

        for (int i = 0; i + 1 < spans.length; i += 2) {
            int s = spans[i] - sliceStart;
            int e = spans[i + 1] - sliceStart;

            // Skip spans outside the visible range
            if (e <= relStart || s >= relEnd) continue;

            int safeS = Math.max(relStart, s);
            int safeE = Math.min(relEnd, e);

            // Draw normal text before this span
            if (safeS > idx) {
                canvas.drawText(line, idx, safeS, x, y, defaultPaint);
                x += (safeS - idx) * charWidth;
            }

            // Draw token box if enabled
            float tokenTextWidth = (safeE - safeS) * charWidth;
            float tokenTotalWidth = tokenTextWidth + (padX * 2f);

            if (binaryTokenBoxEnabled) {
                rect.set(x, boxTop, x + tokenTotalWidth, boxBottom);
                canvas.drawRoundRect(rect, binaryTokenCornerRadius, binaryTokenCornerRadius, binaryTokenFillPaint);
                canvas.drawRoundRect(rect, binaryTokenCornerRadius, binaryTokenCornerRadius, binaryTokenStrokePaint);
            }

            // Draw token text
            String tokenText = line.substring(safeS, safeE);
            binaryTokenTextPaint.setTextSize(defaultPaint.getTextSize());
            binaryTokenTextPaint.setTypeface(defaultPaint.getTypeface());
            canvas.drawText(tokenText, x + padX, y, binaryTokenTextPaint);

            x += tokenTotalWidth;
            idx = safeE;
        }

        // Draw remaining normal text
        if (idx < relEnd) {
            canvas.drawText(line, idx, relEnd, x, y, defaultPaint);
        }
    }
}
