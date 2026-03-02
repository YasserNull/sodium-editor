package com.yn.sodiumeditor.renderer;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import com.yn.sodiumeditor.SodiumEditor;
import com.yn.sodiumeditor.state.HighlightState;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Renderer for error underlines (squiggles).
 */
public class ErrorUnderlineRenderer {

    private final SodiumEditor view;
    private final HighlightState state;

    public ErrorUnderlineRenderer(SodiumEditor view, HighlightState state) {
        this.view = view;
        this.state = state;
    }

    public void setErrorUnderline(int line, int col, int length) {
        if (line < 0) return;
        if (length <= 0) {
            state.errorUnderlineMap.remove(line);
            view.invalidate();
            return;
        }
        int start = Math.max(0, col);
        int end = Math.max(start, start + length);
        List<ErrorUnderlineSpan> list = state.errorUnderlineMap.get(line);
        if (list == null) {
            list = new ArrayList<>();
            state.errorUnderlineMap.put(line, list);
        }
        list.add(new ErrorUnderlineSpan(start, end));
        view.invalidate();
    }

    public void drawErrorUnderlinesForLine(
            Canvas canvas, String line, int globalLine, float baselineY, float lineTop, float lineBottom) {
        if (!state.errorUnderlineEnabled) return;
        List<ErrorUnderlineSpan> spans = state.errorUnderlineMap.get(globalLine);
        if (spans == null || spans.isEmpty()) return;
        List<ErrorUnderlineSpan> snapshot = new ArrayList<>(spans);
        int len = line.length();
        for (ErrorUnderlineSpan span : snapshot) {
            int start = Math.max(0, Math.min(span.start, len));
            int end = Math.max(start, Math.min(span.end, len));
            if (start >= end) continue;
            float xStart = measureText(line, start, globalLine);
            float xEnd = measureText(line, end, globalLine);
            drawErrorSquiggle(canvas, xStart, xEnd, baselineY, lineTop, lineBottom);
        }
    }

    public void drawErrorUnderlinesForLineRange(
            Canvas canvas, String line, int globalLine, int start, int end,
            float baselineY, float lineTop, float lineBottom) {
        if (!state.errorUnderlineEnabled) return;
        List<ErrorUnderlineSpan> spans = state.errorUnderlineMap.get(globalLine);
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
            float xStart = measureText(line, s, globalLine);
            float xEnd = measureText(line, e, globalLine);
            drawErrorSquiggle(canvas, xStart, xEnd, baselineY, lineTop, lineBottom);
        }
    }

    private void drawErrorSquiggle(
            Canvas canvas, float xStart, float xEnd, float baselineY, float lineTop, float lineBottom) {
        if (xEnd <= xStart) return;
        float lineH = Math.max(1f, lineBottom - lineTop);
        float textSize = view.editorConfig.paint.getTextSize();
        float y = baselineY + (view.editorConfig.paint.getFontMetrics().descent * 0.55f);
        float maxY = lineBottom - 2f;
        float minY = lineTop + 1f;
        y = Math.max(minY, Math.min(y, maxY));
        float amplitude = Math.max(1f, Math.min(lineH * 0.22f, textSize * state.errorUnderlineHeightScale));
        float roomTop = y - minY;
        float roomBottom = maxY - y;
        float room = Math.max(0f, Math.min(roomTop, roomBottom));
        amplitude = Math.min(amplitude, Math.max(1f, room));
        float waveLen = Math.max(textSize * state.errorUnderlineWaveLengthScale, amplitude * 2f);
        float thickness = Math.max(1f, textSize * state.errorUnderlineStrokeScale);

        state.errorUnderlinePaint.setColor(state.errorUnderlineColor);
        state.errorUnderlinePaint.setStyle(Paint.Style.STROKE);
        state.errorUnderlinePaint.setStrokeWidth(thickness);
        state.errorUnderlinePaint.setUnderlineText(false);
        state.errorUnderlinePaint.setStrokeCap(Paint.Cap.ROUND);
        state.errorUnderlinePaint.setStrokeJoin(Paint.Join.ROUND);
        if (state.errorUnderlineSmoothness > 0f) {
            state.errorUnderlinePaint.setPathEffect(new android.graphics.CornerPathEffect(state.errorUnderlineSmoothness));
        } else {
            state.errorUnderlinePaint.setPathEffect(null);
        }

        state.errorUnderlinePath.reset();
        state.errorUnderlinePath.moveTo(xStart, y);
        float x = xStart;
        boolean up = true;
        while (x < xEnd) {
            float midX = Math.min(xEnd, x + waveLen * 0.5f);
            float endX = Math.min(xEnd, x + waveLen);
            float ctrlY = up ? (y - amplitude) : (y + amplitude);
            state.errorUnderlinePath.quadTo(midX, ctrlY, endX, y);
            up = !up;
            x = endX;
        }
        canvas.drawPath(state.errorUnderlinePath, state.errorUnderlinePaint);
    }

    public void drawErrorSquigglePublic(Canvas canvas, float xStart, float xEnd, float baselineY, float lineTop, float lineBottom) {
        if (xEnd <= xStart) return;
        float lineH = Math.max(1f, lineBottom - lineTop);
        float textSize = view.editorConfig.paint.getTextSize();
        float y = baselineY + (view.editorConfig.paint.getFontMetrics().descent * 0.55f);
        float maxY = lineBottom - 2f;
        float minY = lineTop + 1f;
        y = Math.max(minY, Math.min(y, maxY));
        float amplitude = Math.max(1f, Math.min(lineH * 0.22f, textSize * state.errorUnderlineHeightScale));
        float roomTop = y - minY;
        float roomBottom = maxY - y;
        float room = Math.max(0f, Math.min(roomTop, roomBottom));
        amplitude = Math.min(amplitude, Math.max(1f, room));
        float waveLen = Math.max(textSize * state.errorUnderlineWaveLengthScale, amplitude * 2f);
        float thickness = Math.max(1f, textSize * state.errorUnderlineStrokeScale);

        state.errorUnderlinePaint.setColor(state.errorUnderlineColor);
        state.errorUnderlinePaint.setStyle(Paint.Style.STROKE);
        state.errorUnderlinePaint.setStrokeWidth(thickness);
        state.errorUnderlinePaint.setUnderlineText(false);
        state.errorUnderlinePaint.setStrokeCap(Paint.Cap.ROUND);
        state.errorUnderlinePaint.setStrokeJoin(Paint.Join.ROUND);
        if (state.errorUnderlineSmoothness > 0f) {
            state.errorUnderlinePaint.setPathEffect(new android.graphics.CornerPathEffect(state.errorUnderlineSmoothness));
        } else {
            state.errorUnderlinePaint.setPathEffect(null);
        }

        state.errorUnderlinePath.reset();
        state.errorUnderlinePath.moveTo(xStart, y);
        float x = xStart;
        boolean up = true;
        while (x < xEnd) {
            float midX = Math.min(xEnd, x + waveLen * 0.5f);
            float endX = Math.min(xEnd, x + waveLen);
            float ctrlY = up ? (y - amplitude) : (y + amplitude);
            state.errorUnderlinePath.quadTo(midX, ctrlY, endX, y);
            up = !up;
            x = endX;
        }
        canvas.drawPath(state.errorUnderlinePath, state.errorUnderlinePaint);
    }

    private float measureText(String line, int length, int globalLine) {
        int logicalLen = view.getLogicalLineLength(globalLine, line);
        int safeLen = Math.max(0, Math.min(length, logicalLen));
        if (logicalLen > state.maxSyntaxLineLength) {
            float avg = getAverageCharWidthForLine(line, globalLine);
            return avg * safeLen;
        }
        return view.whitespaceGuideRenderer.measureTextWithVisualSpaces(view, line, 0, safeLen, view.editorConfig.paint);
    }

    private float getAverageCharWidthForLine(String line, int lineIndex) {
        if (line == null || line.isEmpty()) return view.editorConfig.paint.measureText(" ");
        if (lineIndex >= 0) {
            synchronized (view.avgCharWidthCache) {
                Float cached = view.avgCharWidthCache.get(lineIndex);
                if (cached != null) return cached;
            }
        }
        int sampleLen = Math.min(line.length(), 256);
        float w = (sampleLen > 0) ? view.editorConfig.paint.measureText(line, 0, sampleLen) : view.editorConfig.paint.measureText(" ");
        float avg = (sampleLen > 0) ? (w / sampleLen) : w;
        if (lineIndex >= 0) {
            synchronized (view.avgCharWidthCache) {
                view.avgCharWidthCache.put(lineIndex, avg);
            }
        }
        return avg;
    }

    public void setErrorUnderlineColor(int color) {
        state.errorUnderlineColor = color;
        view.invalidate();
    }

    public void setErrorUnderlineEnabled(boolean enabled) {
        state.errorUnderlineEnabled = enabled;
        view.invalidate();
    }

    public void setErrorUnderlineHeightScale(float scale) {
        state.errorUnderlineHeightScale = Math.max(0f, scale);
        view.invalidate();
    }

    public void setErrorUnderlineWaveLengthScale(float scale) {
        state.errorUnderlineWaveLengthScale = Math.max(0.1f, scale);
        view.invalidate();
    }

    public void setErrorUnderlineStrokeScale(float scale) {
        state.errorUnderlineStrokeScale = Math.max(0f, scale);
        view.invalidate();
    }

    public void setErrorUnderlineSmoothness(float smoothness) {
        state.errorUnderlineSmoothness = Math.max(0f, smoothness);
        view.invalidate();
    }

    public static class ErrorUnderlineSpan {
        public final int start;
        public final int end;

        public ErrorUnderlineSpan(int start, int end) {
            this.start = start;
            this.end = end;
        }
    }
}
