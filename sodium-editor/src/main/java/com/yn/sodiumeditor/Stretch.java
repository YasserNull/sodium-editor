package com.yn.sodiumeditor;

import android.animation.ValueAnimator;
import androidx.annotation.Nullable;

/**
 * Stretch handles all stretch/overscroll effects for SodiumEditor.
 * This includes:
 * - Stretch overscroll effect (elastic band effect)
 * - Stretch release animation
 * - Stretch drawing
 */
public class Stretch {

  // Stretch configuration
  public boolean stretchOverscrollEnabled = true;
  public float stretchOverscrollStrength = 1f;

  // Stretch state
  public float stretchX = 0f;
  public float stretchY = 0f;
  public int stretchDirX = 0; // -1 = left, 0 = none, 1 = right
  public int stretchDirY = 0; // -1 = top, 0 = none, 1 = bottom
  @Nullable public ValueAnimator stretchReleaseAnimator;

  private final SodiumEditor sodiumeditor;

  public Stretch(SodiumEditor sodiumeditor) {
    this.sodiumeditor = sodiumeditor;
  }

  /**
   * Apply stretch effect for X axis
   */
  public void pullStretchX(float deltaPx, boolean toRight) {
    if (!stretchOverscrollEnabled || sodiumeditor.isWordWrapEnabled) return;
    if (sodiumeditor.getWidth() <= 0) return;
    float norm = Math.abs(deltaPx) / (float) sodiumeditor.getWidth();
    float gain = norm * 0.6f * stretchOverscrollStrength;
    stretchDirX = toRight ? 1 : -1;
    stretchX = Math.min(1f, stretchX + gain);
  }

  /**
   * Apply stretch effect for Y axis
   */
  public void pullStretchY(float deltaPx, boolean toBottom) {
    if (!stretchOverscrollEnabled) return;
    if (sodiumeditor.getHeight() <= 0) return;
    float norm = Math.abs(deltaPx) / (float) sodiumeditor.getHeight();
    float gain = norm * 0.6f * stretchOverscrollStrength;
    stretchDirY = toBottom ? 1 : -1;
    stretchY = Math.min(1f, stretchY + gain);
  }

  /**
   * Absorb stretch from fling velocity for X axis
   */
  public void absorbStretchX(float velocityPxPerSec, boolean toRight) {
    if (!stretchOverscrollEnabled || sodiumeditor.isWordWrapEnabled) return;
    float v = Math.min(1f, Math.abs(velocityPxPerSec) / 6000f);
    stretchDirX = toRight ? 1 : -1;
    stretchX = Math.min(1f, stretchX + v * 0.8f * stretchOverscrollStrength);
  }

  /**
   * Absorb stretch from fling velocity for Y axis
   */
  public void absorbStretchY(float velocityPxPerSec, boolean toBottom) {
    if (!stretchOverscrollEnabled) return;
    float v = Math.min(1f, Math.abs(velocityPxPerSec) / 6000f);
    stretchDirY = toBottom ? 1 : -1;
    stretchY = Math.min(1f, stretchY + v * 0.8f * stretchOverscrollStrength);
  }

  /**
   * Release stretch with animation
   */
  public void releaseStretch() {
    if (stretchReleaseAnimator != null) {
      stretchReleaseAnimator.cancel();
    }
    if (stretchX <= 0f && stretchY <= 0f) return;

    stretchReleaseAnimator = ValueAnimator.ofFloat(1f, 0f);
    stretchReleaseAnimator.setDuration(180);
    stretchReleaseAnimator.addUpdateListener(
        animation -> {
          float t = (float) animation.getAnimatedValue();
          stretchX = stretchX * t;
          stretchY = stretchY * t;
          sodiumeditor.invalidate();
        });
    stretchReleaseAnimator.addListener(
        new android.animation.AnimatorListenerAdapter() {
          @Override
          public void onAnimationEnd(android.animation.Animator animation) {
            stretchX = 0f;
            stretchY = 0f;
            stretchDirX = 0;
            stretchDirY = 0;
            stretchReleaseAnimator = null;
            sodiumeditor.invalidate();
          }

          @Override
          public void onAnimationCancel(android.animation.Animator animation) {
            stretchReleaseAnimator = null;
          }
        });
    stretchReleaseAnimator.start();
  }

  /**
   * Cancel stretch release animation
   */
  public void cancelStretchRelease() {
    if (stretchReleaseAnimator != null) {
      stretchReleaseAnimator.cancel();
      stretchReleaseAnimator = null;
    }
  }

  /**
   * Draw stretch effect
   */
  public void drawStretch(android.graphics.Canvas canvas) {
    if (!stretchOverscrollEnabled) return;
    if (stretchX <= 0f && stretchY <= 0f) return;

    int w = sodiumeditor.getWidth();
    int h = sodiumeditor.getHeight();

    // Draw horizontal stretch
    if (stretchX > 0f && !sodiumeditor.isWordWrapEnabled) {
      int alpha = (int) (stretchX * 60);
      android.graphics.Paint paint = new android.graphics.Paint();
      paint.setAlpha(alpha);
      paint.setColor(0x80808080);

      if (stretchDirX < 0) {
        // Stretching from left edge
        float width = w * stretchX * 0.5f;
        canvas.drawRect(0, 0, width, h, paint);
      } else if (stretchDirX > 0) {
        // Stretching from right edge
        float width = w * stretchX * 0.5f;
        canvas.drawRect(w - width, 0, w, h, paint);
      }
    }

    // Draw vertical stretch
    if (stretchY > 0f) {
      int alpha = (int) (stretchY * 60);
      android.graphics.Paint paint = new android.graphics.Paint();
      paint.setAlpha(alpha);
      paint.setColor(0x80808080);

      if (stretchDirY < 0) {
        // Stretching from top edge
        float height = h * stretchY * 0.5f;
        canvas.drawRect(0, 0, w, height, paint);
      } else if (stretchDirY > 0) {
        // Stretching from bottom edge
        float height = h * stretchY * 0.5f;
        canvas.drawRect(0, h - height, w, h, paint);
      }
    }
  }

  /**
   * Reset stretch state
   */
  public void reset() {
    stretchX = 0f;
    stretchY = 0f;
    stretchDirX = 0;
    stretchDirY = 0;
    cancelStretchRelease();
  }

  // Getters and Setters

  public void setStretchOverscrollEnabled(boolean enabled) {
    if (stretchOverscrollEnabled == enabled) return;
    stretchOverscrollEnabled = enabled;
    if (!enabled) {
      reset();
      sodiumeditor.invalidate();
    }
  }

  public void setStretchOverscrollStrength(float strength) {
    if (strength <= 0f) return;
    stretchOverscrollStrength = strength;
  }

  public boolean isStretchActive() {
    return stretchX > 0f || stretchY > 0f;
  }

  public float getStretchX() {
    return stretchX;
  }

  public float getStretchY() {
    return stretchY;
  }

  public int getStretchDirX() {
    return stretchDirX;
  }

  public int getStretchDirY() {
    return stretchDirY;
  }
}
