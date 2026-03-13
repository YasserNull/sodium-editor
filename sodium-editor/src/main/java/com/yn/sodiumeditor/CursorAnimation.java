package com.yn.sodiumeditor;

import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.View;

/**
 * Manages cursor movement animation for SodiumEditor.
 * Handles smooth interpolation of cursor position when moving between locations.
 */
public class CursorAnimation {

  // Animation configuration
  public boolean isCursorAnimationEnabled = true;
  public float cursorAnimNormalTauMs = 80f;
  public float cursorAnimFastTauMs = 35f;
  public long cursorAnimFastThresholdMs = 85;

  // Animation state
  public int lastCursorAnimLine = -1;
  public int lastCursorAnimChar = -1;
  public long lastCursorMoveUptime = 0L;
  public long cursorAnimLastFrameUptime = 0L;
  public float cursorAnimX = 0f;
  public float cursorAnimY = 0f;
  public float cursorAnimTargetX = 0f;
  public float cursorAnimTargetY = 0f;
  public float cursorDrawX = 0f;
  public float cursorDrawY = 0f;
  public boolean cursorAnimValid = false;
  public boolean cursorAnimRunning = false;

  // Reference to parent editor
  private final SodiumEditor editor;

  // Animation step runnable
  public final Runnable cursorAnimStep;

  public CursorAnimation(SodiumEditor editor) {
    this.editor = editor;
    this.cursorAnimStep = new CursorAnimStepRunnable();
  }

  /**
   * Enable or disable cursor animation.
   * @param enabled true to enable, false to disable
   */
  public void setCursorAnimationEnabled(boolean enabled) {
    if (this.isCursorAnimationEnabled == enabled) return;
    this.isCursorAnimationEnabled = enabled;
    if (!enabled) {
      editor.removeCallbacks(cursorAnimStep);
      cursorAnimRunning = false;
      cursorAnimValid = false;
    }
    editor.invalidate();
  }

  /**
   * Check if cursor animation is enabled.
   * @return true if enabled
   */
  public boolean isCursorAnimationEnabled() {
    return isCursorAnimationEnabled;
  }

  /**
   * Set animation speed parameters.
   * @param normalTauMs Time constant for normal movement (ms)
   * @param fastTauMs Time constant for fast movement (ms)
   * @param fastThresholdMs Threshold for detecting fast movement (ms)
   */
  public void setAnimationParameters(float normalTauMs, float fastTauMs, long fastThresholdMs) {
    this.cursorAnimNormalTauMs = normalTauMs;
    this.cursorAnimFastTauMs = fastTauMs;
    this.cursorAnimFastThresholdMs = fastThresholdMs;
  }

  /**
   * Update cursor draw position with animation.
   * @param targetX Target X position in pixels
   * @param targetY Target Y position in pixels
   */
  public void updateCursorDrawPosition(float targetX, float targetY) {
    if (!isCursorAnimationEnabled) {
      editor.removeCallbacks(cursorAnimStep);
      cursorAnimRunning = false;
      cursorAnimValid = true;
      cursorAnimX = targetX;
      cursorAnimY = targetY;
      cursorAnimTargetX = targetX;
      cursorAnimTargetY = targetY;
      cursorDrawX = targetX;
      cursorDrawY = targetY;
      return;
    }

    boolean cursorMoved = (editor.cursor.cursorLine != lastCursorAnimLine || 
                           editor.cursor.cursorChar != lastCursorAnimChar);
    if (cursorMoved) {
      lastCursorAnimLine = editor.cursor.cursorLine;
      lastCursorAnimChar = editor.cursor.cursorChar;
      lastCursorMoveUptime = SystemClock.uptimeMillis();
    } else if (Math.abs(targetX - cursorAnimTargetX) > 0.5f
        || Math.abs(targetY - cursorAnimTargetY) > 0.5f) {
      // Layout/zoom changed while caret stayed; snap to the new target.
      editor.removeCallbacks(cursorAnimStep);
      cursorAnimRunning = false;
      cursorAnimX = targetX;
      cursorAnimY = targetY;
      cursorAnimTargetX = targetX;
      cursorAnimTargetY = targetY;
      cursorAnimValid = true;
      cursorDrawX = targetX;
      cursorDrawY = targetY;
      return;
    }

    if (!cursorAnimValid) {
      cursorAnimX = targetX;
      cursorAnimY = targetY;
      cursorAnimValid = true;
    }

    cursorAnimTargetX = targetX;
    cursorAnimTargetY = targetY;

    float dx = cursorAnimTargetX - cursorAnimX;
    float dy = cursorAnimTargetY - cursorAnimY;
    float distance = (float) Math.hypot(dx, dy);
    
    // If distance is too large, snap to target
    if (distance > editor.lineHeight * 6f) {
      editor.removeCallbacks(cursorAnimStep);
      cursorAnimRunning = false;
      cursorAnimX = targetX;
      cursorAnimY = targetY;
      cursorAnimTargetX = targetX;
      cursorAnimTargetY = targetY;
      cursorDrawX = targetX;
      cursorDrawY = targetY;
      return;
    }

    // Start animation if not running and there's distance to cover
    if (!cursorAnimRunning && distance > 0.5f) {
      cursorAnimLastFrameUptime = 0L;
      cursorAnimRunning = true;
      editor.postOnAnimation(cursorAnimStep);
    }

    cursorDrawX = cursorAnimX;
    cursorDrawY = cursorAnimY;
  }

  /**
   * Snap cursor to target position immediately (no animation).
   * @param x X position in pixels
   * @param y Y position in pixels
   */
  public void snapToPosition(float x, float y) {
    editor.removeCallbacks(cursorAnimStep);
    cursorAnimRunning = false;
    cursorAnimValid = true;
    cursorAnimX = x;
    cursorAnimY = y;
    cursorAnimTargetX = x;
    cursorAnimTargetY = y;
    cursorDrawX = x;
    cursorDrawY = y;
  }

  /**
   * Cancel any running animation.
   */
  public void cancelAnimation() {
    editor.removeCallbacks(cursorAnimStep);
    cursorAnimRunning = false;
  }

  /**
   * Get current draw X position (animated).
   * @return Current X position in pixels
   */
  public float getDrawX() {
    return cursorDrawX;
  }

  /**
   * Get current draw Y position (animated).
   * @return Current Y position in pixels
   */
  public float getDrawY() {
    return cursorDrawY;
  }

  /**
   * Get target X position.
   * @return Target X position in pixels
   */
  public float getTargetX() {
    return cursorAnimTargetX;
  }

  /**
   * Get target Y position.
   * @return Target Y position in pixels
   */
  public float getTargetY() {
    return cursorAnimTargetY;
  }

  /**
   * Check if animation is currently running.
   * @return true if animation is running
   */
  public boolean isRunning() {
    return cursorAnimRunning;
  }

  /**
   * Internal runnable for animation step.
   */
  public class CursorAnimStepRunnable implements Runnable {
    @Override
    public void run() {
      if (!isCursorAnimationEnabled) {
        cursorAnimRunning = false;
        return;
      }
      
      long now = SystemClock.uptimeMillis();
      if (cursorAnimLastFrameUptime == 0L) cursorAnimLastFrameUptime = now;
      long dtMs = Math.max(1, now - cursorAnimLastFrameUptime);
      cursorAnimLastFrameUptime = now;

      float dx = cursorAnimTargetX - cursorAnimX;
      float dy = cursorAnimTargetY - cursorAnimY;
      float dist = (float) Math.hypot(dx, dy);
      
      if (dist <= 0.5f) {
        cursorAnimX = cursorAnimTargetX;
        cursorAnimY = cursorAnimTargetY;
        cursorDrawX = cursorAnimX;
        cursorDrawY = cursorAnimY;
        cursorAnimRunning = false;
        editor.invalidateCursorArea();
        return;
      }

      long moveDelta =
          (lastCursorMoveUptime == 0L) ? Long.MAX_VALUE : (now - lastCursorMoveUptime);
      float tau =
          (moveDelta <= cursorAnimFastThresholdMs)
              ? cursorAnimFastTauMs
              : cursorAnimNormalTauMs;
      float alpha = 1f - (float) Math.exp(-dtMs / Math.max(1f, tau));
      
      cursorAnimX += dx * alpha;
      cursorAnimY += dy * alpha;
      cursorDrawX = cursorAnimX;
      cursorDrawY = cursorAnimY;
      editor.invalidateCursorArea();
      editor.postOnAnimation(this);
    }
  }
}
