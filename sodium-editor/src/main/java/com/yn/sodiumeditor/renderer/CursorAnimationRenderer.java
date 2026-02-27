package com.yn.sodiumeditor.renderer;

import android.graphics.Canvas;
import android.graphics.Paint;
import com.yn.sodiumeditor.SodiumEditorView;
import com.yn.sodiumeditor.renderer.animation.CursorAnimator;

public final class CursorAnimationRenderer {
    private final SodiumEditorView view;
    private final CursorAnimator animator;

    public CursorAnimationRenderer(SodiumEditorView view, CursorAnimator animator) {
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
