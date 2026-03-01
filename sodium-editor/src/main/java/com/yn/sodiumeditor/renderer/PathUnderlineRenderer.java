package com.yn.sodiumeditor.renderer;

import android.graphics.Canvas;
import android.graphics.Paint;
import com.yn.sodiumeditor.SodiumEditorView;
import com.yn.sodiumeditor.core.UnderlineSpan;
import com.yn.sodiumeditor.state.HighlightState;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Renderer for path underlines.
 */
public class PathUnderlineRenderer {

    private final SodiumEditorView view;
    private final HighlightState state;

    public PathUnderlineRenderer(SodiumEditorView view, HighlightState state) {
        this.view = view;
        this.state = state;
    }

    public List<UnderlineSpan> getPathUnderlineSpansForLine(String line, int globalLine) {
        if (!state.isPathUnderliningEnabled || state.pathUnderlinePattern == null) return null;
        List<UnderlineSpan> cached = state.pathUnderlineCache.get(globalLine);
        if (cached != null) return cached;

        ArrayList<UnderlineSpan> spans = new ArrayList<>();
        java.util.regex.Matcher matcher = state.pathUnderlinePattern.matcher(line);
        while (matcher.find()) {
            String potentialPath = matcher.group();
            if (potentialPath != null && !potentialPath.isEmpty()) {
                Boolean exists = state.pathValidationCache.get(potentialPath);
                if (Boolean.TRUE.equals(exists)) {
                    spans.add(new UnderlineSpan(matcher.start(), matcher.end(), true));
                } else if (exists == null) {
                    validatePathInBackground(potentialPath, globalLine);
                }
            }
        }
        state.pathUnderlineCache.put(globalLine, spans);
        return spans;
    }

    private void validatePathInBackground(final String path, final int lineToInvalidate) {
        if (state.pendingPathValidations.contains(path)) return;
        state.pendingPathValidations.add(path);

        view.ioHandler.post(() -> {
            boolean exists = false;
            try {
                java.io.File file = new java.io.File(path);
                exists = file.exists();
            } catch (Exception e) {
                // Ignore errors
            } finally {
                state.pathValidationCache.put(path, exists);
                state.pendingPathValidations.remove(path);

                if (exists) {
                    view.mainHandler.post(() -> {
                        state.pathUnderlineCache.remove(lineToInvalidate);
                        view.invalidate();
                    });
                }
            }
        });
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

        state.pathUnderlineTmpPaint.set(textPaint);
        state.pathUnderlineTmpPaint.setStyle(Paint.Style.STROKE);
        state.pathUnderlineTmpPaint.setStrokeWidth(thickness);
        state.pathUnderlineTmpPaint.setUnderlineText(false);

        boolean hasFade = fadeStart >= 0 && fadeEnd > fadeStart && fadeAlpha < 1f;
        if (!hasFade || end <= fadeStart || start >= fadeEnd) {
            float w = view.whitespaceGuideRenderer.measureTextWithVisualSpaces(view, line, start, end, textPaint);
            if (w > 0f) canvas.drawLine(x, underlineY, x + w, underlineY, state.pathUnderlineTmpPaint);
            return;
        }

        float currentX = x;
        int baseAlpha = textPaint.getAlpha();

        int beforeEnd = Math.min(end, fadeStart);
        if (start < beforeEnd) {
            state.pathUnderlineTmpPaint.setAlpha(baseAlpha);
            float w = view.whitespaceGuideRenderer.measureTextWithVisualSpaces(view, line, start, beforeEnd, textPaint);
            if (w > 0f) canvas.drawLine(currentX, underlineY, currentX + w, underlineY, state.pathUnderlineTmpPaint);
            currentX += w;
        }

        int fadeSegStart = Math.max(start, fadeStart);
        int fadeSegEnd = Math.min(end, fadeEnd);
        if (fadeSegStart < fadeSegEnd) {
            state.pathUnderlineTmpPaint.setAlpha((int) (baseAlpha * Math.max(0f, Math.min(1f, fadeAlpha))));
            float w = view.whitespaceGuideRenderer.measureTextWithVisualSpaces(view, line, fadeSegStart, fadeSegEnd, textPaint);
            if (w > 0f) canvas.drawLine(currentX, underlineY, currentX + w, underlineY, state.pathUnderlineTmpPaint);
            currentX += w;
        }

        int afterStart = Math.max(start, fadeEnd);
        if (afterStart < end) {
            state.pathUnderlineTmpPaint.setAlpha(baseAlpha);
            float w = view.whitespaceGuideRenderer.measureTextWithVisualSpaces(view, line, afterStart, end, textPaint);
            if (w > 0f) canvas.drawLine(currentX, underlineY, currentX + w, underlineY, state.pathUnderlineTmpPaint);
        }
    }

    public void setPathUnderliningEnabled(boolean enabled) {
        state.isPathUnderliningEnabled = enabled;
        state.pathUnderlineCache.clear();
        state.pathValidationCache.clear();
        state.pendingPathValidations.clear();
        view.invalidate();
    }

    public void setPathUnderlineRegex(String regex) {
        if (regex == null || regex.trim().isEmpty()) {
            state.pathUnderlinePattern = null;
        } else {
            state.pathUnderlinePattern = java.util.regex.Pattern.compile(regex);
        }
        state.pathUnderlineCache.clear();
        view.invalidate();
    }

    public void clearPathCache() {
        state.pathUnderlineCache.clear();
        state.pathValidationCache.clear();
        state.pendingPathValidations.clear();
    }
}
