package com.yn.sodiumeditor;

import android.os.SystemClock;

public final class CursorAnimationManager {
  private final SodiumEditorView view;

  private boolean isCursorVisible = true;

  private boolean isCursorAnimationEnabled = false;
  private float cursorAnimNormalTauMs = 80f;
  private float cursorAnimFastTauMs = 35f;
  private long cursorAnimFastThresholdMs = 85;
  private int lastCursorAnimLine = -1;
  private int lastCursorAnimChar = -1;
  private long lastCursorMoveUptime = 0L;
  private long cursorAnimLastFrameUptime = 0L;
  private float cursorAnimX = 0f;
  private float cursorAnimY = 0f;
  private float cursorAnimTargetX = 0f;
  private float cursorAnimTargetY = 0f;
  private float cursorDrawX = 0f;
  private float cursorDrawY = 0f;
  private boolean cursorAnimValid = false;
  private boolean cursorAnimRunning = false;

  private final Runnable blinkRunnable =
      new Runnable() {
        @Override
        public void run() {
          if (view.isFocused() && !view.selectionManager.hasSelection()) {
            isCursorVisible = !isCursorVisible;
            view.cursorManager.invalidateCursorArea();
            view.mainHandler.postDelayed(this, 500);
          }
        }
      };

  private final Runnable cursorAnimStep =
      new Runnable() {
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
            view.cursorManager.invalidateCursorArea();
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
          view.cursorManager.invalidateCursorArea();
          view.postOnAnimation(this);
        }
      };

  CursorAnimationManager(SodiumEditorView view) {
    this.view = view;
  }

  boolean isCursorVisible() {
    return isCursorVisible;
  }

  float getCursorDrawX() {
    return cursorDrawX;
  }

  float getCursorDrawY() {
    return cursorDrawY;
  }

  public void setCursorAnimationEnabled(boolean enabled) {
    if (isCursorAnimationEnabled == enabled) return;
    isCursorAnimationEnabled = enabled;
    if (!enabled) {
      view.removeCallbacks(cursorAnimStep);
      cursorAnimRunning = false;
      cursorAnimValid = false;
    }
    view.invalidate();
  }

  void updateCursorDrawPosition(float targetX, float targetY) {
    if (!isCursorAnimationEnabled) {
      view.removeCallbacks(cursorAnimStep);
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

    boolean cursorMoved = (view.cursorManager.getLine() != lastCursorAnimLine || view.cursorManager.getChar() != lastCursorAnimChar);
    if (cursorMoved) {
      lastCursorAnimLine = view.cursorManager.getLine();
      lastCursorAnimChar = view.cursorManager.getChar();
      lastCursorMoveUptime = SystemClock.uptimeMillis();
    } else if (Math.abs(targetX - cursorAnimTargetX) > 0.5f
        || Math.abs(targetY - cursorAnimTargetY) > 0.5f) {
      view.removeCallbacks(cursorAnimStep);
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
    if (distance > view.lineHeight * 6f) {
      view.removeCallbacks(cursorAnimStep);
      cursorAnimRunning = false;
      cursorAnimX = targetX;
      cursorAnimY = targetY;
      cursorAnimTargetX = targetX;
      cursorAnimTargetY = targetY;
      cursorDrawX = targetX;
      cursorDrawY = targetY;
      return;
    }

    if (!cursorAnimRunning && distance > 0.5f) {
      cursorAnimLastFrameUptime = 0L;
      cursorAnimRunning = true;
      view.postOnAnimation(cursorAnimStep);
    }

    cursorDrawX = cursorAnimX;
    cursorDrawY = cursorAnimY;
  }

  public void resetCursorBlink() {
    view.mainHandler.removeCallbacks(blinkRunnable);
    isCursorVisible = true;
    if (view.isFocused() && !view.selectionManager.hasSelection()) {
      view.invalidate();
      view.mainHandler.postDelayed(blinkRunnable, 500);
    }
  }

  void onFocusChanged(boolean focused) {
    if (focused) {
      resetCursorBlink();
    } else {
      view.mainHandler.removeCallbacks(blinkRunnable);
      isCursorVisible = true;
    }
  }

  void release() {
    view.removeCallbacks(cursorAnimStep);
    view.mainHandler.removeCallbacks(blinkRunnable);
  }
}
