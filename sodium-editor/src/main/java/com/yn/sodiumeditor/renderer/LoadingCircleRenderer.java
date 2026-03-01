package com.yn.sodiumeditor.renderer;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import com.yn.sodiumeditor.SodiumEditorView;
import com.yn.sodiumeditor.state.LoadingCircleState;

/**
 * Renderer class for loading circle.
 * Handles drawing the loading spinner on the canvas.
 */
public class LoadingCircleRenderer {

    private static final float DEFAULT_STROKE_WIDTH = 8f;

    private final SodiumEditorView view;
    private final LoadingCircleState state;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();

    public LoadingCircleRenderer(SodiumEditorView view, LoadingCircleState state) {
        this.view = view;
        this.state = state;
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
    }

    public void draw(Canvas canvas) {
        if (!state.isShow()) return;

        paint.setColor(state.getColor());
        paint.setStrokeWidth(DEFAULT_STROKE_WIDTH);

        float centerX = view.getWidth() / 2f;
        float centerY = view.getHeight() / 2f;

        canvas.save();
        canvas.rotate(state.getRotation(), centerX, centerY);

        rect.set(
                centerX - state.getRadius(),
                centerY - state.getRadius(),
                centerX + state.getRadius(),
                centerY + state.getRadius());

        canvas.drawArc(rect, 0, 270, false, paint);
        canvas.restore();
    }

    public void setStrokeWidth(float width) {
        paint.setStrokeWidth(width);
    }

    public float getStrokeWidth() {
        return paint.getStrokeWidth();
    }
}
