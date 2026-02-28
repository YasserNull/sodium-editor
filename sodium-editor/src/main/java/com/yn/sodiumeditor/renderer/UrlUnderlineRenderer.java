package com.yn.sodiumeditor.renderer;

import android.graphics.Canvas;
import android.graphics.Paint;
import com.yn.sodiumeditor.SodiumEditorView;
import com.yn.sodiumeditor.core.UnderlineSpan;
import com.yn.sodiumeditor.state.HighlightState;
import java.util.ArrayList;
import java.util.List;

/**
 * Renderer for URL underlines.
 */
public class UrlUnderlineRenderer {

    private final SodiumEditorView view;
    private final HighlightState state;

    public UrlUnderlineRenderer(SodiumEditorView view, HighlightState state) {
        this.view = view;
        this.state = state;
    }

    public List<UnderlineSpan> getUrlUnderlineSpansForLine(String line, int globalLine) {
        if (!state.isUrlUnderliningEnabled || state.urlUnderlinePattern == null) return null;
        List<UnderlineSpan> cached = state.urlUnderlineCache.get(globalLine);
        if (cached != null) return cached;

        ArrayList<UnderlineSpan> spans = new ArrayList<>();
        java.util.regex.Matcher matcher = state.urlUnderlinePattern.matcher(line);
        while (matcher.find()) {
            int start = matcher.start();
            int end = matcher.end();
            end = trimUrlUnderlineEnd(line, start, end);
            if (end > start) {
                spans.add(new UnderlineSpan(start, end, false));
            }
        }
        state.urlUnderlineCache.put(globalLine, spans);
        return spans;
    }

    public void drawUnderlineSegment(
            Canvas canvas, String line, int start, int end, float x, float y,
            float lineTop, float lineBottom, Paint textPaint) {
        drawUnderlineSegmentWithFade(canvas, line, start, end, x, y, lineTop, lineBottom, textPaint, -1, -1, 1f);
    }

    public void drawUnderlineSegmentWithFade(
            Canvas canvas, String line, int start, int end, float x, float y,
            float lineTop, float lineBottom, Paint textPaint,
            int fadeStart, int fadeEnd, float fadeAlpha) {
        if (start >= end) return;

        Paint.FontMetrics fm = textPaint.getFontMetrics();
        float underlineY = y + (fm.descent * 0.5f);
        underlineY = Math.max(lineTop + 1f, Math.min(underlineY, lineBottom - 2f));

        float thickness = Math.max(1f, textPaint.getTextSize() / 18f);
        thickness = Math.min(thickness, Math.max(1f, (lineBottom - lineTop) / 8f));

        state.urlUnderlineTmpPaint.set(textPaint);
        state.urlUnderlineTmpPaint.setStyle(Paint.Style.STROKE);
        state.urlUnderlineTmpPaint.setStrokeWidth(thickness);
        state.urlUnderlineTmpPaint.setUnderlineText(false);

        boolean hasFade = fadeStart >= 0 && fadeEnd > fadeStart && fadeAlpha < 1f;
        if (!hasFade || end <= fadeStart || start >= fadeEnd) {
            float w = view.whitespaceGuideManager.measureTextWithVisualSpaces(view, line, start, end, textPaint);
            if (w > 0f) canvas.drawLine(x, underlineY, x + w, underlineY, state.urlUnderlineTmpPaint);
            return;
        }

        float currentX = x;
        int baseAlpha = textPaint.getAlpha();

        int beforeEnd = Math.min(end, fadeStart);
        if (start < beforeEnd) {
            state.urlUnderlineTmpPaint.setAlpha(baseAlpha);
            float w = view.whitespaceGuideManager.measureTextWithVisualSpaces(view, line, start, beforeEnd, textPaint);
            if (w > 0f) canvas.drawLine(currentX, underlineY, currentX + w, underlineY, state.urlUnderlineTmpPaint);
            currentX += w;
        }

        int fadeSegStart = Math.max(start, fadeStart);
        int fadeSegEnd = Math.min(end, fadeEnd);
        if (fadeSegStart < fadeSegEnd) {
            state.urlUnderlineTmpPaint.setAlpha((int) (baseAlpha * Math.max(0f, Math.min(1f, fadeAlpha))));
            float w = view.whitespaceGuideManager.measureTextWithVisualSpaces(view, line, fadeSegStart, fadeSegEnd, textPaint);
            if (w > 0f) canvas.drawLine(currentX, underlineY, currentX + w, underlineY, state.urlUnderlineTmpPaint);
            currentX += w;
        }

        int afterStart = Math.max(start, fadeEnd);
        if (afterStart < end) {
            state.urlUnderlineTmpPaint.setAlpha(baseAlpha);
            float w = view.whitespaceGuideManager.measureTextWithVisualSpaces(view, line, afterStart, end, textPaint);
            if (w > 0f) canvas.drawLine(currentX, underlineY, currentX + w, underlineY, state.urlUnderlineTmpPaint);
        }
    }

    private static int trimUrlUnderlineEnd(String line, int start, int end) {
        int e = Math.min(end, line.length());
        while (e > start) {
            char c = line.charAt(e - 1);
            if (c == '.' || c == ',' || c == ';' || c == ':' || c == '!' || c == '?' || c == ')'
                    || c == ']' || c == '}' || c == '>' || c == '"' || c == '\'') {
                e--;
                continue;
            }
            break;
        }
        return e;
    }

    public void setUrlUnderliningEnabled(boolean enabled) {
        state.isUrlUnderliningEnabled = enabled;
        state.urlUnderlineCache.clear();
        view.invalidate();
    }

    public void setUrlUnderlineRegex(String regex) {
        if (regex == null || regex.trim().isEmpty()) {
            state.urlUnderlinePattern = null;
        } else {
            state.urlUnderlinePattern = java.util.regex.Pattern.compile(regex);
        }
        state.urlUnderlineCache.clear();
        view.invalidate();
    }
}
