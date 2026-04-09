package com.yn.sodiumeditor.renderer.animation;

import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.View;
import com.yn.sodiumeditor.SodiumEditor;
/**
 * Manages cursor movement animation for SodiumEditor.
 * Handles smooth interpolation of cursor position when moving between locations.
 */
public class CursorAnimation {

  // Animation configuration
  public boolean isCursorAnimationEnabled = true;
  public long cursorAnimDurationMs = 80; // Reduced from 140ms for faster response

  // Animation state
  public int lastCursorAnimLine = -1;
  public int lastCursorAnimChar = -1;
  public long lastCursorMoveUptime = 0L;
  public long cursorAnimLastFrameUptime = 0L;
  public long cursorAnimStartUptime = 0L;
  public float cursorAnimStartX = 0f;
  public float cursorAnimStartY = 0f;
  public float cursorAnimX = 0f;
  public float cursorAnimY = 0f;
  public float cursorAnimTargetX = 0f;
  public float cursorAnimTargetY = 0f;
  public float cursorDrawX = 0f;
  public float cursorDrawY = 0f;
  public boolean cursorAnimValid = false;
  public boolean cursorAnimRunning = false;
  public float lastScrollX = Float.NaN;
  public float lastScrollY = Float.NaN;

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
    // Deprecated in time-based animation mode.
  }

  public void setAnimationDurationMs(long durationMs) {
    long d = Math.max(60L, Math.min(300L, durationMs));
    cursorAnimDurationMs = d;
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
    boolean scrollChanged =
        (Float.isNaN(lastScrollX) || Float.isNaN(lastScrollY))
            || editor.scroll.scrollX != lastScrollX
            || editor.scroll.scrollY != lastScrollY;
    if (cursorMoved) {
      lastCursorAnimLine = editor.cursor.cursorLine;
      lastCursorAnimChar = editor.cursor.cursorChar;
      lastCursorMoveUptime = SystemClock.uptimeMillis();
      
      // Always capture current drawn position as animation start
      if (cursorAnimValid && cursorAnimRunning) {
        // If animation is running, start from current interpolated position
        cursorAnimStartX = cursorDrawX;
        cursorAnimStartY = cursorDrawY;
      } else {
        // First time or invalid state, start from target
        cursorAnimStartX = targetX;
        cursorAnimStartY = targetY;
      }
      cursorAnimX = cursorAnimStartX;
      cursorAnimY = cursorAnimStartY;
      cursorAnimValid = true;
      
      if (SodiumEditor.DEBUG_RENDER_LOGS) {
        editor.logRender(
            "cursorAnimMove",
            "cursorAnim move line=" + lastCursorAnimLine
                + " ch=" + lastCursorAnimChar
                + " targetX=" + targetX
                + " targetY=" + targetY,
            120);
      }
    }

    if (!cursorAnimValid) {
      cursorAnimX = targetX;
      cursorAnimY = targetY;
      cursorAnimStartX = targetX;
      cursorAnimStartY = targetY;
      cursorAnimValid = true;
    }

    if (!cursorMoved && scrollChanged) {
      editor.removeCallbacks(cursorAnimStep);
      cursorAnimRunning = false;
      cursorAnimX = targetX;
      cursorAnimY = targetY;
      cursorAnimTargetX = targetX;
      cursorAnimTargetY = targetY;
      cursorDrawX = targetX;
      cursorDrawY = targetY;
      lastScrollX = editor.scroll.scrollX;
      lastScrollY = editor.scroll.scrollY;
      return;
    }

    cursorAnimTargetX = targetX;
    cursorAnimTargetY = targetY;
    lastScrollX = editor.scroll.scrollX;
    lastScrollY = editor.scroll.scrollY;

    float dx = cursorAnimTargetX - cursorAnimX;
    float dy = cursorAnimTargetY - cursorAnimY;
    float distance = (float) Math.hypot(dx, dy);

    // Start animation if not running and there's distance to cover
    if (!cursorAnimRunning && distance > 0.5f) {
      cursorAnimStartUptime = SystemClock.uptimeMillis();
      cursorAnimLastFrameUptime = 0L;
      cursorAnimRunning = true;
      if (SodiumEditor.DEBUG_RENDER_LOGS) {
        editor.logRender(
            "cursorAnimStart",
            "cursorAnim start dist=" + distance
                + " dur=" + cursorAnimDurationMs,
            120);
      }
      editor.postOnAnimation(cursorAnimStep);
    } else if (distance <= 0.5f) {
      // No distance, snap to target
      cursorDrawX = targetX;
      cursorDrawY = targetY;
      cursorAnimRunning = false;
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
      if (cursorAnimStartUptime == 0L) cursorAnimStartUptime = now;
      long elapsed = Math.max(0L, now - cursorAnimStartUptime);
      float t = Math.min(1f, (float) elapsed / (float) Math.max(1L, cursorAnimDurationMs));
      // Faster ease-out animation
      float eased = 1f - (1f - t) * (1f - t);
      cursorAnimX = cursorAnimStartX + (cursorAnimTargetX - cursorAnimStartX) * eased;
      cursorAnimY = cursorAnimStartY + (cursorAnimTargetY - cursorAnimStartY) * eased;
      cursorDrawX = cursorAnimX;
      cursorDrawY = cursorAnimY;
      editor.invalidateCursorArea();
      if (t >= 1f) {
        cursorAnimRunning = false;
        if (SodiumEditor.DEBUG_RENDER_LOGS) {
          editor.logRender(
              "cursorAnimEnd",
              "cursorAnim end x=" + cursorAnimX + " y=" + cursorAnimY,
              120);
        }
      } else {
        editor.postOnAnimation(this);
      }
    }
  }
}
