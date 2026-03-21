package com.yn.sodiumeditor.core.highlite;
import com.yn.sodiumeditor.SodiumEditor;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Error underlining functionality for SodiumEditor.
 * Handles drawing squiggly underlines for errors, warnings, and lint issues.
 */
public class ErrorUnderline {

    private final SodiumEditor editor;

    // Error underline configuration
    public int errorUnderlineColor = 0xFFE53935;
    public boolean errorUnderlineEnabled = false;
    public final Paint errorUnderlinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    public final Path errorUnderlinePath = new Path();
    public float errorUnderlineHeightScale = 0.18f;
    public float errorUnderlineWaveLengthScale = 0.70f;
    public float errorUnderlineStrokeScale = 0.08f;
    public float errorUnderlineSmoothness = 3f;

    // Error underline cache
    public final LinkedHashMap<Integer, List<ErrorUnderlineSpan>> errorUnderlineMap =
            new LinkedHashMap<Integer, List<ErrorUnderlineSpan>>(256, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<Integer, List<ErrorUnderlineSpan>> eldest) {
                    return size() > 2000;
                }
            };

    // Error underline span class
    public static class ErrorUnderlineSpan {
        public final int start;
        public final int end;

        public ErrorUnderlineSpan(int start, int end) {
            this.start = start;
            this.end = end;
        }
    }
    public ErrorUnderline(SodiumEditor editor) {
        this.editor = editor;
    }

    /**
     * Clear all error underlines.
     */
    public void clearErrorUnderlines() {
        errorUnderlineMap.clear();
    }

    /**
     * Clear error underlines for a specific line.
     */
    public void clearErrorUnderlinesForLine(int line) {
        errorUnderlineMap.remove(line);
    }

    /**
     * Get error underline spans for a line.
     */
    public List<ErrorUnderlineSpan> getErrorUnderlineSpansForLine(int globalLine) {
        return errorUnderlineMap.get(globalLine);
    }

    /**
     * Set error underline for a specific range.
     */
    public void setErrorUnderline(int line, int col, int length) {
        if (line < 0) return;
        if (length <= 0) {
            errorUnderlineMap.remove(line);
            editor.invalidate();
            return;
        }
        int start = Math.max(0, col);
        int end = Math.max(start, start + length);
        List<ErrorUnderlineSpan> list = errorUnderlineMap.get(line);
        if (list == null) {
            list = new ArrayList<>();
            errorUnderlineMap.put(line, list);
        }
        list.add(new ErrorUnderlineSpan(start, end));
        editor.invalidate();
    }

    /**
     * Set error underline color.
     */
    public void setErrorUnderlineColor(int color) {
        if (errorUnderlineColor == color) return;
        errorUnderlineColor = color;
        editor.invalidate();
    }

    /**
     * Get error underline color.
     */
    public int getErrorUnderlineColor() {
        return errorUnderlineColor;
    }

    /**
     * Set error underline enabled state.
     */
    public void setErrorUnderlineEnabled(boolean enabled) {
        if (errorUnderlineEnabled == enabled) return;
        errorUnderlineEnabled = enabled;
        editor.invalidate();
    }

    /**
     * Get error underline enabled state.
     */
    public boolean isErrorUnderlineEnabled() {
        return errorUnderlineEnabled;
    }

    /**
     * Set error underline height scale.
     */
    public void setErrorUnderlineHeightScale(float scale) {
        float safe = Math.max(0f, scale);
        if (errorUnderlineHeightScale == safe) return;
        errorUnderlineHeightScale = safe;
        editor.invalidate();
    }

    /**
     * Get error underline height scale.
     */
    public float getErrorUnderlineHeightScale() {
        return errorUnderlineHeightScale;
    }

    /**
     * Set error underline wavelength scale.
     */
    public void setErrorUnderlineWaveLengthScale(float scale) {
        float safe = Math.max(0.1f, scale);
        if (errorUnderlineWaveLengthScale == safe) return;
        errorUnderlineWaveLengthScale = safe;
        editor.invalidate();
    }

    /**
     * Get error underline wavelength scale.
     */
    public float getErrorUnderlineWaveLengthScale() {
        return errorUnderlineWaveLengthScale;
    }

    /**
     * Set error underline stroke scale.
     */
    public void setErrorUnderlineStrokeScale(float scale) {
        float safe = Math.max(0f, scale);
        if (errorUnderlineStrokeScale == safe) return;
        errorUnderlineStrokeScale = safe;
        editor.invalidate();
    }

    /**
     * Get error underline stroke scale.
     */
    public float getErrorUnderlineStrokeScale() {
        return errorUnderlineStrokeScale;
    }

    /**
     * Set error underline smoothness.
     */
    public void setErrorUnderlineSmoothness(float smoothness) {
        float safe = Math.max(0f, smoothness);
        if (errorUnderlineSmoothness == safe) return;
        errorUnderlineSmoothness = safe;
        editor.invalidate();
    }

    /**
     * Get error underline smoothness.
     */
    public float getErrorUnderlineSmoothness() {
        return errorUnderlineSmoothness;
    }

    /**
     * Draw error underlines for a line.
     */
    public void drawErrorUnderlinesForLine(
            Canvas canvas, String line, int globalLine, float baselineY, float lineTop, float lineBottom) {
        if (!errorUnderlineEnabled) return;
        List<ErrorUnderlineSpan> spans = errorUnderlineMap.get(globalLine);
        if (spans == null || spans.isEmpty()) return;
        List<ErrorUnderlineSpan> snapshot = new ArrayList<>(spans);
        int len = line.length();
        for (ErrorUnderlineSpan span : snapshot) {
            int start = Math.max(0, Math.min(span.start, len));
            int end = Math.max(start, Math.min(span.end, len));
            if (start >= end) continue;
            float xStart = editor.measureText(line, start, globalLine);
            float xEnd = editor.measureText(line, end, globalLine);
            drawErrorSquiggle(canvas, xStart, xEnd, baselineY, lineTop, lineBottom);
        }
    }

    /**
     * Draw error underlines for a line range.
     */
    public void drawErrorUnderlinesForLineRange(
            Canvas canvas, String line, int globalLine, int start, int end,
            float baselineY, float lineTop, float lineBottom) {
        if (!errorUnderlineEnabled) return;
        List<ErrorUnderlineSpan> spans = errorUnderlineMap.get(globalLine);
        if (spans == null || spans.isEmpty()) return;
        List<ErrorUnderlineSpan> snapshot = new ArrayList<>(spans);
        int len = line.length();
        start = Math.max(0, Math.min(start, len));
        end = Math.max(start, Math.min(end, len));
        if (start >= end) return;
        for (ErrorUnderlineSpan span : snapshot) {
            int s = Math.max(start, Math.max(0, Math.min(span.start, len)));
            int e = Math.min(end, Math.max(s, Math.min(span.end, len)));
            if (s >= e) continue;
            float xStart = editor.measureText(line, s, globalLine);
            float xEnd = editor.measureText(line, e, globalLine);
            drawErrorSquiggle(canvas, xStart, xEnd, baselineY, lineTop, lineBottom);
        }
    }

    /**
     * Draw error underlines for a segment.
     */
    public void drawErrorUnderlinesForSegment(
            Canvas canvas, String line, int globalLine, int start, int end,
            float baselineY, float lineTop, float lineBottom) {
        if (!errorUnderlineEnabled) return;
        List<ErrorUnderlineSpan> spans = errorUnderlineMap.get(globalLine);
        if (spans == null || spans.isEmpty()) return;
        List<ErrorUnderlineSpan> snapshot = new ArrayList<>(spans);
        int len = line.length();
        start = Math.max(0, Math.min(start, len));
        end = Math.max(start, Math.min(end, len));
        if (start >= end) return;
        for (ErrorUnderlineSpan span : snapshot) {
            int s = Math.max(start, Math.max(0, Math.min(span.start, len)));
            int e = Math.min(end, Math.max(s, Math.min(span.end, len)));
            if (s >= e) continue;
            float xStart = editor.measureTextWithVisualSpaces(line, s, e, editor.textRender.paint);
            float xEnd = editor.measureTextWithVisualSpaces(line, s, e, editor.textRender.paint);
            drawErrorSquiggle(canvas, xStart, xEnd, baselineY, lineTop, lineBottom);
        }
    }

    /**
     * Draw error squiggle.
     */
    public void drawErrorSquiggle(Canvas canvas, float xStart, float xEnd, float baselineY, float lineTop, float lineBottom) {
        if (xEnd <= xStart) return;

        errorUnderlinePaint.setColor(errorUnderlineColor);
        errorUnderlinePaint.setStyle(Paint.Style.STROKE);
        errorUnderlinePaint.setStrokeWidth(Math.max(1f, editor.textRender.paint.getTextSize() * errorUnderlineStrokeScale));
        errorUnderlinePaint.setAntiAlias(true);

        float underlineY = baselineY + (editor.textRender.paint.getFontMetrics().descent * 0.5f);
        underlineY = Math.max(lineTop + 1f, Math.min(underlineY, lineBottom - 2f));

        float waveHeight = (lineBottom - lineTop) * errorUnderlineHeightScale;
        float wavelength = (xEnd - xStart) * errorUnderlineWaveLengthScale;
        int waves = Math.max(1, (int) ((xEnd - xStart) / wavelength));

        errorUnderlinePath.reset();
        errorUnderlinePath.moveTo(xStart, underlineY);

        for (int i = 0; i <= waves; i++) {
            float x = xStart + (i * wavelength);
            float y = underlineY + ((i % 2 == 0) ? waveHeight : -waveHeight);
            errorUnderlinePath.quadTo(x - (wavelength / 2), underlineY, x, y);
        }

        canvas.drawPath(errorUnderlinePath, errorUnderlinePaint);
    }
}
