package com.yn.sodiumeditor;

import android.animation.ValueAnimator;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import androidx.annotation.Nullable;

/**
 * LoadingCircle handles the loading circle animation for SodiumEditor.
 * This includes:
 * - Loading circle visibility
 * - Rotation animation
 * - Loading circle rendering
 */
public class LoadingCircle {

  // Loading circle state
  public boolean showLoadingCircle = false;
  public float loadingCircleRotation = 0f;
  
  // Loading circle appearance
  public float loadingCircleRadius = 40f;
  public int loadingCircleColor = 0xFF3F51B5;
  public float loadingCircleStrokeWidth = 8f;
  
  // Animation
  @Nullable public ValueAnimator rotationAnimator;
  public static final long ROTATION_ANIM_DURATION_MS = 1000;
  
  // Paint and rects
  public final Paint loadingCirclePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  public final RectF loadingCircleRect = new RectF();
  
  private final SodiumEditor sodiumeditor;

  public LoadingCircle(SodiumEditor sodiumeditor) {
    this.sodiumeditor = sodiumeditor;
    
    // Initialize paint
    loadingCirclePaint.setStyle(Paint.Style.STROKE);
    loadingCirclePaint.setStrokeCap(Paint.Cap.ROUND);
  }

  /**
   * Show or hide loading circle
   */
  public void setShow(boolean show) {
    showLoadingCircle = show;
    if (show) {
      startRotation();
    } else {
      stopRotation();
    }
    sodiumeditor.invalidate();
  }

  /**
   * Start rotation animation
   */
  private void startRotation() {
    if (rotationAnimator == null) {
      rotationAnimator = ValueAnimator.ofFloat(0f, 360f);
      rotationAnimator.setDuration(ROTATION_ANIM_DURATION_MS);
      rotationAnimator.setRepeatCount(ValueAnimator.INFINITE);
      rotationAnimator.addUpdateListener(
          animation -> {
            loadingCircleRotation = (float) animation.getAnimatedValue();
            sodiumeditor.invalidate();
          });
    }
    if (!rotationAnimator.isRunning()) {
      rotationAnimator.start();
    }
  }

  /**
   * Stop rotation animation
   */
  private void stopRotation() {
    if (rotationAnimator != null && rotationAnimator.isRunning()) {
      rotationAnimator.cancel();
    }
    loadingCircleRotation = 0f;
  }

  /**
   * Draw loading circle on canvas
   */
  public void drawLoadingCircle(Canvas canvas) {
    if (!showLoadingCircle) {
      return;
    }
    
    float centerX = canvas.getWidth() * 0.5f;
    float centerY = canvas.getHeight() * 0.5f;
    
    loadingCirclePaint.setColor(loadingCircleColor);
    loadingCirclePaint.setStrokeWidth(loadingCircleStrokeWidth);
    
    canvas.save();
    canvas.rotate(loadingCircleRotation, centerX, centerY);
    
    loadingCircleRect.set(
        centerX - loadingCircleRadius,
        centerY - loadingCircleRadius,
        centerX + loadingCircleRadius,
        centerY + loadingCircleRadius);
    
    canvas.drawArc(loadingCircleRect, 0, 270, false, loadingCirclePaint);
    canvas.restore();
  }

  /**
   * Check if loading circle is visible
   */
  public boolean isVisible() {
    return showLoadingCircle;
  }

  /**
   * Cancel animation and cleanup
   */
  public void cancel() {
    stopRotation();
    if (rotationAnimator != null) {
      rotationAnimator = null;
    }
  }

  // Getters and Setters

  public void setLoadingCircleRadius(float radius) {
    if (radius <= 0f) return;
    loadingCircleRadius = radius;
  }

  public void setLoadingCircleColor(int color) {
    loadingCircleColor = color;
  }

  public void setLoadingCircleStrokeWidth(float width) {
    if (width <= 0f) return;
    loadingCircleStrokeWidth = width;
  }

  public boolean isAnimating() {
    return rotationAnimator != null && rotationAnimator.isRunning();
  }

  public float getRotation() {
    return loadingCircleRotation;
  }
}
