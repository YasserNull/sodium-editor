package com.yn.sodiumeditor.core.scroll;

import android.animation.ValueAnimator;
import android.view.animation.DecelerateInterpolator;
import androidx.annotation.Nullable;
import com.yn.sodiumeditor.SodiumEditor;
import com.yn.sodiumeditor.utils.FunctionLog;

/**
 * Stretch handles all stretch/overscroll effects for SodiumEditor.
 */
public class Stretch {
  private static final String ANIM_DBG = "AnimDbg";

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
    FunctionLog.f("Stretch", "Stretch", editor);
    this.editor = editor;
  }

  public void pullStretchX(float deltaPx, boolean toRight) {
    FunctionLog.f("Stretch", "pullStretchX", deltaPx, toRight);
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
    android.util.Log.i(ANIM_DBG, "stretch pullX delta=" + deltaPx + " toRight=" + toRight + " stretchX=" + stretchX);
    editor.invalidate();
  }

  public void pullStretchY(float deltaPx, boolean toBottom) {
    FunctionLog.f("Stretch", "pullStretchY", deltaPx, toBottom);
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
    android.util.Log.i(ANIM_DBG, "stretch pullY delta=" + deltaPx + " toBottom=" + toBottom + " stretchY=" + stretchY);
    editor.invalidate();
  }

  public void absorbStretchX(float velocity, boolean toRight) {
    FunctionLog.f("Stretch", "absorbStretchX", velocity, toRight);
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
    FunctionLog.f("Stretch", "absorbStretchY", velocity, toBottom);
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
    FunctionLog.f("Stretch", "releaseStretch");
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
    android.util.Log.i(ANIM_DBG, "stretch release start x=" + stretchX + " y=" + stretchY);

    stretchReleaseAnimator = ValueAnimator.ofFloat(1f, 0f);
    stretchReleaseAnimator.setDuration(300); // مدة الارتداد السلس
    stretchReleaseAnimator.setInterpolator(new DecelerateInterpolator());
    stretchReleaseAnimator.addUpdateListener(animation -> {
      float t = (float) animation.getAnimatedValue();
      stretchX *= t;
      stretchY *= t;
      android.util.Log.i(ANIM_DBG, "stretch release frame t=" + t + " x=" + stretchX + " y=" + stretchY);
      editor.invalidate();
    });
    stretchReleaseAnimator.start();
  }

  public void cancelStretchRelease() {
    FunctionLog.f("Stretch", "cancelStretchRelease");
    if (stretchReleaseAnimator != null) {
      stretchReleaseAnimator.cancel();
      stretchReleaseAnimator = null;
    }
  }

  public void drawStretch(android.graphics.Canvas canvas) {
    FunctionLog.f("Stretch", "drawStretch", canvas);
    // Custom drawing if needed, but SodiumEditor uses scale() on canvas
  }

  public void reset() {
    FunctionLog.f("Stretch", "reset");
    stretchX = 0f;
    stretchY = 0f;
    stretchDirX = 0;
    stretchDirY = 0;
    cancelStretchRelease();
  }

  public void setStretchOverscrollEnabled(boolean enabled) {
    FunctionLog.f("Stretch", "setStretchOverscrollEnabled", enabled);
    this.stretchOverscrollEnabled = enabled;
    if (!enabled) reset();
  }

  public void setStretchOverscrollStrength(float strength) {
    FunctionLog.f("Stretch", "setStretchOverscrollStrength", strength);
    this.stretchOverscrollStrength = strength;
  }
}
