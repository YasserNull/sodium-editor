package com.yn.sodiumeditor.renderer.animation;

import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import com.yn.sodiumeditor.SodiumEditor;
import com.yn.sodiumeditor.config.CursorAnimationConfig;

public final class CursorAnimator {
    private final SodiumEditor view;
    private final CursorAnimationConfig config;

    private boolean isCursorVisible = true;
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

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Runnable blinkRunnable =
            new Runnable() {
                @Override
                public void run() {
                    if (view.isFocused() && !view.selectionState.hasSelection()) {
                        isCursorVisible = !isCursorVisible;
                        view.viewRender.textRender.invalidateLineGlobal(view.cursorState.getCursorLine());
                        mainHandler.postDelayed(this, 500);
                    }
                }
            };

    private final Runnable cursorAnimStep =
            new Runnable() {
                @Override
                public void run() {
                    if (!config.isCursorAnimationEnabled()) {
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
                        view.viewRender.textRender.invalidateLineGlobal(view.cursorState.getCursorLine());
                        return;
                    }

                    long moveDelta =
                            (lastCursorMoveUptime == 0L) ? Long.MAX_VALUE : (now - lastCursorMoveUptime);
                    float tau =
                            (moveDelta <= config.getFastThresholdMs())
                                    ? config.getFastTauMs()
                                    : config.getNormalTauMs();
                    float alpha = 1f - (float) Math.exp(-dtMs / Math.max(1f, tau));
                    cursorAnimX += dx * alpha;
                    cursorAnimY += dy * alpha;
                    cursorDrawX = cursorAnimX;
                    cursorDrawY = cursorAnimY;
                    view.viewRender.textRender.invalidateLineGlobal(view.cursorState.getCursorLine());
                    view.postOnAnimation(cursorAnimStep);
                }
            };

    public CursorAnimator(SodiumEditor view, CursorAnimationConfig config) {
        this.view = view;
        this.config = config;
    }

    public void updateCursorDrawPosition(float targetX, float targetY) {
        if (!config.isCursorAnimationEnabled()) {
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

        boolean cursorMoved =
                (view.cursorState.getCursorLine() != lastCursorAnimLine
                        || view.cursorState.getCursorChar() != lastCursorAnimChar);
        if (cursorMoved) {
            lastCursorAnimLine = view.cursorState.getCursorLine();
            lastCursorAnimChar = view.cursorState.getCursorChar();
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
        mainHandler.removeCallbacks(blinkRunnable);
        isCursorVisible = true;
        if (view.isFocused() && !view.selectionState.hasSelection()) {
            view.invalidate();
            mainHandler.postDelayed(blinkRunnable, 500);
        }
    }

    public void onFocusChanged(boolean focused) {
        if (focused) {
            resetCursorBlink();
        } else {
            mainHandler.removeCallbacks(blinkRunnable);
            isCursorVisible = true;
        }
    }

    public void release() {
        view.removeCallbacks(cursorAnimStep);
        mainHandler.removeCallbacks(blinkRunnable);
    }

    public void reset() {
        view.removeCallbacks(cursorAnimStep);
        mainHandler.removeCallbacks(blinkRunnable);
        cursorAnimRunning = false;
        cursorAnimValid = false;
    }

    public boolean isCursorVisible() {
        return isCursorVisible;
    }

    public void setCursorVisible(boolean visible) {
        isCursorVisible = visible;
    }

    public float getCursorDrawX() {
        return cursorDrawX;
    }

    public float getCursorDrawY() {
        return cursorDrawY;
    }

    public float getCursorAnimX() {
        return cursorAnimX;
    }

    public float getCursorAnimY() {
        return cursorAnimY;
    }

    public float getCursorAnimTargetX() {
        return cursorAnimTargetX;
    }

    public float getCursorAnimTargetY() {
        return cursorAnimTargetY;
    }

    public boolean isCursorAnimRunning() {
        return cursorAnimRunning;
    }

    public boolean isCursorAnimValid() {
        return cursorAnimValid;
    }
}
