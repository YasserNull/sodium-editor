package com.yn.sodiumeditor.core.scroll;

import android.animation.ValueAnimator;
import android.view.animation.DecelerateInterpolator;
import androidx.annotation.Nullable;
import com.yn.sodiumeditor.SodiumEditor;

/** Stretch handles all stretch/overscroll effects for SodiumEditor. */
public class Stretch {

  public boolean stretchOverscrollEnabled = true;
  public float stretchOverscrollStrength = 1f;
  public boolean stretchOverlayEnabled = false;

  public float stretchX = 0f;
  public float stretchY = 0f;
  public int stretchDirX = 0; // -1 = left, 1 = right
  public int stretchDirY = 0; // -1 = top, 1 = bottom

  @Nullable public ValueAnimator stretchReleaseAnimator;

  private final SodiumEditor editor;

  public Stretch(SodiumEditor editor) {
    this.editor = editor;
  }

  public void pullStretchX(float deltaPx, boolean toRight) {
    if (!stretchOverscrollEnabled) return;
    if (editor.selection.hasSelection) {
      reset();
      return;
    }
    cancelStretchRelease();
    float norm = Math.abs(deltaPx) / (float) editor.getWidth();
    float gain = norm * 0.4f * stretchOverscrollStrength;
    stretchDirX = toRight ? 1 : -1;
    stretchX = Math.min(1f, stretchX + gain);
    editor.invalidate();
  }

  public void pullStretchY(float deltaPx, boolean toBottom) {
    if (!stretchOverscrollEnabled) return;
    if (editor.selection.hasSelection) {
      reset();
      return;
    }
    cancelStretchRelease();
    float norm = Math.abs(deltaPx) / (float) editor.getHeight();
    float gain = norm * 0.4f * stretchOverscrollStrength;
    stretchDirY = toBottom ? 1 : -1;
    stretchY = Math.min(1f, stretchY + gain);
    editor.invalidate();
  }

  public void absorbStretchX(float velocity, boolean toRight) {
    if (!stretchOverscrollEnabled) return;
    if (editor.selection.hasSelection) {
      reset();
      return;
    }
    stretchDirX = toRight ? 1 : -1;
    stretchX = Math.min(0.5f, Math.abs(velocity) / 10000f);
    releaseStretch();
  }

  public void absorbStretchY(float velocity, boolean toBottom) {
    if (!stretchOverscrollEnabled) return;
    if (editor.selection.hasSelection) {
      reset();
      return;
    }
    stretchDirY = toBottom ? 1 : -1;
    stretchY = Math.min(0.5f, Math.abs(velocity) / 10000f);
    releaseStretch();
  }

  public void releaseStretch() {
    if (stretchReleaseAnimator != null) {
      stretchReleaseAnimator.cancel();
    }
    if (editor.selection.hasSelection) {
      stretchX = 0f;
      stretchY = 0f;
      stretchDirX = 0;
      stretchDirY = 0;
      editor.invalidate();
      return;
    }
    if (stretchX <= 0f && stretchY <= 0f) return;

    stretchReleaseAnimator = ValueAnimator.ofFloat(1f, 0f);
    stretchReleaseAnimator.setDuration(300); // مدة الارتداد السلس
    stretchReleaseAnimator.setInterpolator(new DecelerateInterpolator());
    stretchReleaseAnimator.addUpdateListener(
        animation -> {
          float t = (float) animation.getAnimatedValue();
          stretchX *= t;
          stretchY *= t;
          editor.invalidate();
        });
    stretchReleaseAnimator.start();
  }

  public void cancelStretchRelease() {
    if (stretchReleaseAnimator != null) {
      stretchReleaseAnimator.cancel();
      stretchReleaseAnimator = null;
    }
  }

  public void drawStretch(android.graphics.Canvas canvas) {
    // Custom drawing if needed, but SodiumEditor uses scale() on canvas
  }

  public void reset() {
    stretchX = 0f;
    stretchY = 0f;
    stretchDirX = 0;
    stretchDirY = 0;
    cancelStretchRelease();
  }

  public void setStretchOverscrollEnabled(boolean enabled) {
    this.stretchOverscrollEnabled = enabled;
    if (!enabled) reset();
  }

  public void setStretchOverscrollStrength(float strength) {
    this.stretchOverscrollStrength = strength;
  }
}
