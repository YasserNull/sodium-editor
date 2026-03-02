package com.yn.sodiumeditor.renderer;

import android.graphics.Canvas;
import android.graphics.Paint;
import com.yn.sodiumeditor.SodiumEditor;
import com.yn.sodiumeditor.renderer.animation.CursorAnimator;

public final class CursorAnimationRenderer {
    private final SodiumEditor view;
    private final CursorAnimator animator;

    public CursorAnimationRenderer(SodiumEditor view, CursorAnimator animator) {
        this.view = view;
        this.animator = animator;
    }

    public void drawCursor(Canvas canvas, Paint cursorPaint) {
        if (!animator.isCursorVisible()) return;

        float drawX = animator.getCursorDrawX();
        float drawY = animator.getCursorDrawY();
        float cursorHeight = view.lineHeight;

        canvas.drawLine(drawX, drawY, drawX, drawY + cursorHeight, cursorPaint);
    }
}
