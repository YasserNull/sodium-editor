package com.yn.sodiumeditor;

import android.animation.ValueAnimator;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;

public final class LoadingCircleManager {
  private static final float DEFAULT_RADIUS = 40f;
  private static final int DEFAULT_COLOR = 0xFF3F51B5;
  private static final float DEFAULT_STROKE_WIDTH = 8f;

  private final SodiumEditorView view;
  private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final RectF rect = new RectF();

  private boolean show = false;
  private float radius = DEFAULT_RADIUS;
  private int color = DEFAULT_COLOR;
  private float rotation = 0f;
  private ValueAnimator rotationAnimator;

  LoadingCircleManager(SodiumEditorView view) {
    this.view = view;
    paint.setStyle(Paint.Style.STROKE);
    paint.setStrokeCap(Paint.Cap.ROUND);
  }

  public void show(boolean enabled) {
    show = enabled;
    if (enabled) {
      if (rotationAnimator == null) {
        rotationAnimator = ValueAnimator.ofFloat(0f, 360f);
        rotationAnimator.setDuration(1000);
        rotationAnimator.setRepeatCount(ValueAnimator.INFINITE);
        rotationAnimator.addUpdateListener(
            animation -> {
              rotation = (float) animation.getAnimatedValue();
              view.invalidate();
            });
      }
      if (!rotationAnimator.isRunning()) rotationAnimator.start();
    } else {
      if (rotationAnimator != null && rotationAnimator.isRunning()) rotationAnimator.cancel();
      rotation = 0f;
    }
    view.invalidate();
  }

  boolean isVisible() {
    return show;
  }

  void setColor(int color) {
    this.color = color;
    view.invalidate();
  }

  void setRadius(float radius) {
    this.radius = radius;
    view.invalidate();
  }

  public void draw(Canvas canvas) {
    if (!show) return;
    paint.setColor(color);
    paint.setStrokeWidth(DEFAULT_STROKE_WIDTH);
    float centerX = view.getWidth() / 2f;
    float centerY = view.getHeight() / 2f;
    canvas.save();
    canvas.rotate(rotation, centerX, centerY);
    rect.set(
        centerX - radius,
        centerY - radius,
        centerX + radius,
        centerY + radius);
    canvas.drawArc(rect, 0, 270, false, paint);
    canvas.restore();
  }
}
