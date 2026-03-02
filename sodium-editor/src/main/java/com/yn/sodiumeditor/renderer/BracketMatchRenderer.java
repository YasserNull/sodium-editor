package com.yn.sodiumeditor.renderer;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import com.yn.sodiumeditor.SodiumEditor;
import com.yn.sodiumeditor.state.BracketMatch;
import com.yn.sodiumeditor.state.BracketMatchState;

/**
 * Renderer class for bracket matches.
 * Handles drawing boxes around matching brackets.
 */
public class BracketMatchRenderer {

    private final SodiumEditor view;
    private final BracketMatchState state;

    public final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

    public BracketMatchRenderer(SodiumEditor view, BracketMatchState state) {
        this.view = view;
        this.state = state;
        paint.setStyle(Paint.Style.STROKE);
    }

    public void setColor(int color) {
        paint.setColor(color);
    }

    public void setStrokeWidth(float width) {
        state.setBaseStrokeWidth(width);
        state.setBaseTextSizePx(view.getPaintTextSizeForMatch());
    }

    public void setBaseTextSizePx(float sizePx) {
        state.setBaseTextSizePx(sizePx);
    }

    public float getBaseStrokeWidth() {
        return state.getBaseStrokeWidth();
    }

    public float getBaseTextSizePx() {
        return state.getBaseTextSizePx();
    }

    public void applyScaledStrokeWidth(float scaledWidth) {
        state.setStrokeWidth(scaledWidth);
        paint.setStrokeWidth(state.getStrokeWidth());
    }

    public void drawMatchForLine(Canvas canvas, String line, int globalLine, BracketMatch match) {
        if (match == null) return;
        if (globalLine != match.openLine && globalLine != match.closeLine) return;
        if (line == null || line.isEmpty()) return;

        if (match.openLine == match.closeLine) {
            if (match.openChar == match.closeChar) {
                drawBracketBox(canvas, line, globalLine, match.openChar);
                return;
            }

            if (Math.abs(match.openChar - match.closeChar) == 1) {
                int leftIndex = Math.min(match.openChar, match.closeChar);
                int rightIndex = Math.max(match.openChar, match.closeChar);
                drawBracketBoxRange(canvas, line, globalLine, leftIndex, rightIndex);
            } else {
                drawBracketBox(canvas, line, globalLine, match.openChar);
                drawBracketBox(canvas, line, globalLine, match.closeChar);
            }
            return;
        }

        int index = (globalLine == match.openLine) ? match.openChar : match.closeChar;
        drawBracketBox(canvas, line, globalLine, index);
    }

    private void drawBracketBox(Canvas canvas, String line, int globalLine, int index) {
        if (index < 0 || index >= line.length()) return;

        float left = view.highlightRenderer.measureText(line, index, globalLine);
        float right = view.highlightRenderer.measureText(line, index + 1, globalLine);
        if (right <= left)
            right = left + view.whitespaceGuideRenderer.measureTextWithVisualSpaces(view, line, index, index + 1, paint);

        drawBracketBoxRect(canvas, globalLine, left, right);
    }

    private void drawBracketBoxRange(
            Canvas canvas, String line, int globalLine, int startIndex, int endIndex) {
        if (startIndex < 0 || endIndex < 0) return;
        if (startIndex >= line.length()) return;
        if (endIndex >= line.length()) endIndex = line.length() - 1;
        if (endIndex < startIndex) return;

        float left = view.highlightRenderer.measureText(line, startIndex, globalLine);
        float right = view.highlightRenderer.measureText(line, endIndex + 1, globalLine);
        if (right <= left)
            right = left + view.whitespaceGuideRenderer.measureTextWithVisualSpaces(view, line, startIndex, endIndex + 1, paint);
        drawBracketBoxRect(canvas, globalLine, left, right);
    }

    private void drawBracketBoxRect(Canvas canvas, int globalLine, float left, float right) {
        final float padding = 1f;
        final float top = view.getDrawLineTopForMatch(globalLine) + padding;
        final float bottom = top + view.getLineHeightForMatch() - (padding * 2f);

        float l = left - padding;
        float r = right + padding;
        if (r <= l) return;

        state.getRect().set(l, top, r, bottom);
        float radius = Math.max(2f, state.getStrokeWidth() + 1f);
        canvas.drawRoundRect(state.getRect(), radius, radius, paint);
    }
}
