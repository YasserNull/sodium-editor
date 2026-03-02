package com.yn.sodiumeditor.renderer;

import android.graphics.Canvas;
import android.graphics.Paint;
import androidx.annotation.Nullable;
import com.yn.sodiumeditor.SodiumEditor;
import com.yn.sodiumeditor.renderer.animation.CharAnimator;

public final class CharAnimationRenderer {
    private final SodiumEditor view;
    private final CharAnimator animator;

    public CharAnimationRenderer(SodiumEditor view, CharAnimator animator) {
        this.view = view;
        this.animator = animator;
    }

    public void drawDeleteAnimation(
            Canvas canvas, int globalLine, float x, float y, int baseAlpha) {
        if (!animator.getDelAnimTextIsForLine(globalLine)) return;

        String delText = animator.getDelAnimText();
        if (delText == null || delText.isEmpty()) return;
        if (animator.getDelAnimAlpha() <= 0f) return;

        Paint ghostPaint = animator.getDelAnimPaint();
        if (ghostPaint == null) ghostPaint = view.editorConfig.paint;

        Paint tempPaint = animator.getTempPaint();
        tempPaint.set(ghostPaint);
        tempPaint.setUnderlineText(false);
        tempPaint.setAlpha(
                (int) (baseAlpha * Math.max(0f, Math.min(1f, animator.getDelAnimAlpha()))));

        canvas.drawText(delText, x, y, tempPaint);
    }

    public void drawDeleteAnimationForSegment(
            Canvas canvas, String line, int globalLine, int segStart, int segEnd, float y) {
        if (!view.charAnimationConfig.isEnabled()) return;
        if (globalLine != animator.getDelAnimLine()
                || animator.getDelAnimText() == null
                || animator.getDelAnimText().isEmpty()
                || animator.getDelAnimAlpha() <= 0f) return;
        if (line == null) line = "";
        int at = Math.max(0, Math.min(animator.getDelAnimAtChar(), line.length()));
        if (at < segStart || at > segEnd) return;
        float x = view.whitespaceGuideRenderer.measureTextWithVisualSpaces(view, line, segStart, at, view.editorConfig.paint);
        Paint ghostPaint = (animator.getDelAnimPaint() != null) ? animator.getDelAnimPaint() : view.editorConfig.paint;
        Paint tempPaint = animator.getTempPaint();
        tempPaint.set(ghostPaint);
        tempPaint.setUnderlineText(false);
        int baseAlpha = ghostPaint.getAlpha();
        tempPaint.setAlpha((int) (baseAlpha * Math.max(0f, Math.min(1f, animator.getDelAnimAlpha()))));
        canvas.drawText(animator.getDelAnimText(), x, y, tempPaint);
    }

    public void drawCharAnimationHighlight(
            Canvas canvas,
            String line,
            int globalLine,
            float currentX,
            float y,
            Paint segmentPaint,
            int fadeSegStart,
            int fadeSegEnd,
            float fadeAlpha) {
        if (!animator.getCharAnimTextIsForLine(globalLine)) return;
        if (animator.getCharAnimEndChar() <= animator.getCharAnimStartChar()) return;
        if (animator.getCharAnimAlpha() >= 1f) return;

        Paint tempPaint = animator.getTempPaint();
        tempPaint.set(segmentPaint);
        tempPaint.setAlpha((int) (segmentPaint.getAlpha() * fadeAlpha));
        canvas.drawText(
                line,
                fadeSegStart,
                fadeSegEnd,
                currentX,
                y,
                tempPaint);
    }
}
