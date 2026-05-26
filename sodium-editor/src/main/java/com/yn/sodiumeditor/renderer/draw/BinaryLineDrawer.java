package com.yn.sodiumeditor.renderer.draw;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import com.yn.sodiumeditor.SodiumEditor;
import com.yn.sodiumeditor.core.binary.BinaryTokenConverter;

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
        if (paint != null) {
            cachedCharWidth = paint.measureText("M");
        }
    }

    public float getCachedCharWidth() {
        return cachedCharWidth > 0f ? cachedCharWidth : editor.textRender.paint.measureText("M");
    }

    public void setCachedCharWidth(float width) {
        this.cachedCharWidth = Math.max(1f, width);
    }

    // ── Configuration setters ──────────────────────────────────────────────────
    public void setBinaryTokenBoxEnabled(boolean enabled) {
        binaryTokenBoxEnabled = enabled;
    }

    public void setBinaryTokenFillColor(int color) {
        binaryTokenFillColor = color;
        binaryTokenFillPaint.setColor(color);
    }

    public void setBinaryTokenStrokeColor(int color) {
        binaryTokenStrokeColor = color;
        binaryTokenStrokePaint.setColor(color);
    }

    public void setBinaryTokenStrokeWidth(float widthPx) {
        binaryTokenStrokeWidth = Math.max(0.5f, widthPx);
        binaryTokenStrokePaint.setStrokeWidth(binaryTokenStrokeWidth);
    }

    public void setBinaryTokenBoxPadding(float paddingX, float paddingY) {
        binaryTokenPaddingX = Math.max(0f, paddingX);
        binaryTokenPaddingY = Math.max(0f, paddingY);
    }

    public void setBinaryTokenCornerRadius(float radiusPx) {
        binaryTokenCornerRadius = Math.max(0f, radiusPx);
    }

    public void setBinaryTokenTextColor(int color) {
        binaryTokenTextColor = color;
        binaryTokenTextPaint.setColor(color);
    }

    public void setBinaryCaretNotationEnabled(boolean enabled) {
        binaryCaretNotationEnabled = enabled;
    }

    public Paint getBinaryTokenFillPaint() { 
        return binaryTokenFillPaint; 
    }
    public Paint getBinaryTokenStrokePaint() { 
        return binaryTokenStrokePaint; 
    }

    // ── Cursor helpers ─────────────────────────────────────────────────────────
    public int snapBinaryCursor(String line, int index, int lineIndex, android.util.SparseArray<int[]> binaryTokenSpans) {
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
        if (line == null) return 0f;
        int len = line.length();
        int idx = Math.max(0, Math.min(charIndex, len));

        // Use cached character width instead of measuring
        float charWidth = getCachedCharWidth();

        if (spans == null || spans.length == 0 || idx == 0) {
            return paint.measureText(line, 0, idx);
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
                    return current + paint.measureText(line, pos, idx);
                }
                current += paint.measureText(line, pos, s);
                pos = s;
            }
            if (idx <= e) {
                float tokenWidth = paint.measureText(line, s, e) + (padX * 2f);
                return (idx <= s) ? current : (idx >= e ? current + tokenWidth : current);
            }
            float tokenWidth = paint.measureText(line, s, e) + (padX * 2f);
            current += tokenWidth;
            pos = e;
        }
        if (idx > pos) {
            current += paint.measureText(line, pos, idx);
        }
        return current;
    }

    // ── Fast Binary Line Drawing ───────────────────────────────────────────────
    /**
     * Optimized binary line drawing method with fast path.
     * Uses cached character width and avoids all unnecessary measurements.
     */
    public void drawBinaryLine(Canvas canvas, String line, int globalLine, float y, Paint defaultPaint, android.util.SparseArray<int[]> binaryTokenSpans) {
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
        drawBinaryLineSlice(canvas, line, globalLine, relStart, relEnd, sliceStart, y, defaultPaint, binaryTokenSpans, -1, -1, 1f);
    }

    public void drawBinaryLineSlice(
            Canvas canvas,
            String line,
            int globalLine,
            int relStart,
            int relEnd,
            int sliceStart,
            float y,
            Paint defaultPaint,
            android.util.SparseArray<int[]> binaryTokenSpans,
            int fadeStart,
            int fadeEnd,
            float fadeAlpha) {
        if (line == null || relStart >= relEnd) return;

        int len = line.length();
        int[] spans = binaryTokenSpans.get(globalLine);
        boolean hasFade = fadeStart >= 0 && fadeEnd > fadeStart && fadeAlpha < 1f;

        // Use cached character width
        float charWidth = getCachedCharWidth();
        float padX = binaryCaretNotationEnabled ? 0f : binaryTokenPaddingX;

        // Fast path: no spans, draw entire slice at once
        if (spans == null || spans.length == 0) {
            drawBinaryTextRunWithFade(canvas, line, relStart, relEnd, 0f, y, defaultPaint, fadeStart, fadeEnd, fadeAlpha);
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
                drawBinaryTextRunWithFade(canvas, line, idx, safeS, x, y, defaultPaint, fadeStart, fadeEnd, fadeAlpha);
                x += editor.textRender.measureTextWithVisualSpaces(line, idx, safeS, defaultPaint);
            }

            // Draw token box if enabled
            float tokenTextWidth = editor.textRender.measureTextWithVisualSpaces(line, safeS, safeE, defaultPaint);
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
            if (hasFade && safeS < fadeEnd && safeE > fadeStart) {
                drawBinaryTextRunWithFade(canvas, line, safeS, safeE, x + padX, y, binaryTokenTextPaint, fadeStart, fadeEnd, fadeAlpha);
            } else {
                canvas.drawText(tokenText, x + padX, y, binaryTokenTextPaint);
            }

            x += tokenTotalWidth;
            idx = safeE;
        }

        // Draw remaining normal text
        if (idx < relEnd) {
            drawBinaryTextRunWithFade(canvas, line, idx, relEnd, x, y, defaultPaint, fadeStart, fadeEnd, fadeAlpha);
        }
    }

    private float drawBinaryTextRunWithFade(
            Canvas canvas,
            String line,
            int start,
            int end,
            float x,
            float y,
            Paint paint,
            int fadeStart,
            int fadeEnd,
            float fadeAlpha) {
        if (start >= end) return 0f;
        boolean hasFade = fadeStart >= 0 && fadeEnd > fadeStart && fadeAlpha < 1f;
        if (!hasFade || end <= fadeStart || start >= fadeEnd) {
            if (line.indexOf('\t', start) < end) {
                return editor.textLineDraw.drawTextSegmentWithVisualSpaces(canvas, line, start, end, x, y, paint, 1f);
            }
            canvas.drawText(line, start, end, x, y, paint);
            return paint.measureText(line, start, end);
        }

        float currentX = x;
        int beforeEnd = Math.min(end, fadeStart);
        if (start < beforeEnd) {
            if (line.indexOf('\t', start) < beforeEnd) {
                currentX += editor.textLineDraw.drawTextSegmentWithVisualSpaces(canvas, line, start, beforeEnd, currentX, y, paint, 1f);
            } else {
                canvas.drawText(line, start, beforeEnd, currentX, y, paint);
                currentX += paint.measureText(line, start, beforeEnd);
            }
        }

        int fadeSegStart = Math.max(start, fadeStart);
        int fadeSegEnd = Math.min(end, fadeEnd);
        if (fadeSegStart < fadeSegEnd) {
            editor.charAnimation.charAnimTmpPaint.set(paint);
            int baseAlpha = paint.getAlpha();
            float alpha = Math.max(0f, Math.min(1f, fadeAlpha));
            editor.charAnimation.charAnimTmpPaint.setAlpha((int) (baseAlpha * alpha));
            if (line.indexOf('\t', fadeSegStart) < fadeSegEnd) {
                currentX += editor.textLineDraw.drawTextSegmentWithVisualSpaces(canvas, line, fadeSegStart, fadeSegEnd, currentX, y, editor.charAnimation.charAnimTmpPaint, fadeAlpha);
            } else {
                canvas.drawText(
                        line,
                        fadeSegStart,
                        fadeSegEnd,
                        currentX,
                        y + getCharAnimOffsetY(alpha, paint),
                        editor.charAnimation.charAnimTmpPaint);
                currentX += paint.measureText(line, fadeSegStart, fadeSegEnd);
            }
        }

        int afterStart = Math.max(start, fadeEnd);
        if (afterStart < end) {
            if (line.indexOf('\t', afterStart) < end) {
                currentX += editor.textLineDraw.drawTextSegmentWithVisualSpaces(canvas, line, afterStart, end, currentX, y, paint, 1f);
            } else {
                canvas.drawText(line, afterStart, end, currentX, y, paint);
                currentX += paint.measureText(line, afterStart, end);
            }
        }

        return currentX - x;
    }

    private float getCharAnimOffsetY(float alpha, Paint paint) {
        return 0f;
    }
}
