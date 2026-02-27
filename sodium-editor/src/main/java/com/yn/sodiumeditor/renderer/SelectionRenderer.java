package com.yn.sodiumeditor.renderer;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import com.yn.sodiumeditor.config.SelectionConfig;

/**
 * Renderer class for selection functionality.
 * Handles drawing selection highlights on the canvas.
 */
public final class SelectionRenderer {

    private final SelectionConfig config;
    private final RectF selectionRectTmp = new RectF();
    private final Path selectionPathTmp = new Path();
    private final float[] selectionRadiiTmp = new float[8];

    public SelectionRenderer(SelectionConfig config) {
        this.config = config;
    }

    public void drawSelectionSegment(
            Canvas canvas,
            float left,
            float top,
            float right,
            float bottom,
            boolean roundTopLeft,
            boolean roundTopRight,
            boolean roundBottomRight,
            boolean roundBottomLeft,
            float lineHeight,
            Paint paint) {
        if (right <= left || bottom <= top) return;

        float radius = Math.min(12f, Math.max(2f, lineHeight * 0.22f));
        float insetX = 0.5f;
        selectionRectTmp.set(left + insetX, top, right - insetX, bottom);

        if (!roundTopLeft && !roundTopRight && !roundBottomRight && !roundBottomLeft) {
            canvas.drawRect(selectionRectTmp, paint);
            return;
        }

        float tl = roundTopLeft ? radius : 0f;
        float tr = roundTopRight ? radius : 0f;
        float br = roundBottomRight ? radius : 0f;
        float bl = roundBottomLeft ? radius : 0f;

        selectionRadiiTmp[0] = tl;
        selectionRadiiTmp[1] = tl;
        selectionRadiiTmp[2] = tr;
        selectionRadiiTmp[3] = tr;
        selectionRadiiTmp[4] = br;
        selectionRadiiTmp[5] = br;
        selectionRadiiTmp[6] = bl;
        selectionRadiiTmp[7] = bl;

        selectionPathTmp.reset();
        selectionPathTmp.addRoundRect(selectionRectTmp, selectionRadiiTmp, Path.Direction.CW);
        canvas.drawPath(selectionPathTmp, paint);
    }

    public Paint getSelectionPaint() {
        return config.getSelectionPaint();
    }

    public void initPaints() {
        config.initPaints();
    }
}
