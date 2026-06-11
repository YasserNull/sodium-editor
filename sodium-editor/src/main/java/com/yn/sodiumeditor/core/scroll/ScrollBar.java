package com.yn.sodiumeditor.core.scroll;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import com.yn.sodiumeditor.SodiumEditor;

/** Handles drawing, fading, and dragging of the scroll bar. */
public class ScrollBar {
  private final SodiumEditor editor;
  private final Scroll scroll;

  public final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
  public final Paint haloPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  public final RectF thumbRect = new RectF();
  public final Runnable hideRunnable = this::startFadeOut;
  public ValueAnimator fadeAnimator;

  // ScrollBar properties
  public boolean scrollBarEnabled = true;
  public int scrollBarColor = 0x88888888;
  public float scrollBarWidthPx = 6f;
  public float scrollBarMinThumbPx = 24f;
  public float scrollBarCornerRadiusPx = 6f;
  public float scrollBarMarginPx = 2f;
  public boolean scrollBarFadeEnabled = true;
  public long scrollBarFadeDelayMs = 1000;
  public long scrollBarFadeDurationMs = 200;
  public float scrollBarAlpha = 0f;
  public int scrollBarHaloColor = 0x40888888;
  public float scrollBarHaloSizePx = 8f;
  public boolean scrollBarDragging = false;
  public float scrollBarDragOffset = 0f;

  public ScrollBar(SodiumEditor editor, Scroll scroll) {
    this.editor = editor;
    this.scroll = scroll;
    float density = editor.getContext().getResources().getDisplayMetrics().density;
    scrollBarWidthPx *= density;
    scrollBarMinThumbPx *= density;
    scrollBarCornerRadiusPx *= density;
    scrollBarMarginPx *= density;
    scrollBarHaloSizePx *= density;
  }

  public void draw(Canvas canvas) {
    if (!scrollBarEnabled) return;
    boolean interacting =
        scrollBarDragging
            || editor.onTouch.pointerDown
            || scroll.scrollerIsScrolling
            || scroll.flingStopAnimator != null;
    if (scrollBarFadeEnabled && scrollBarAlpha <= 0f) {
      if (interacting && fadeAnimator == null) show();
      if (!interacting) return;
    }
    int w = editor.getWidth();
    int h = editor.getHeight();
    if (w <= 0 || h <= 0) return;
    float maxScroll = scroll.getMaxScrollYForClamp();
    if (maxScroll <= 0f) {
      if (!interacting && scrollBarAlpha <= 0f) return;
      float right = w - scrollBarMarginPx;
      float left = right - scrollBarWidthPx;
      thumbRect.set(left, 0f, right, h);
      int alphaInt = (int) (Math.min(1f, scrollBarAlpha) * 255);
      paint.setColor((scrollBarColor & 0x00FFFFFF) | (alphaInt << 24));
      canvas.drawRoundRect(thumbRect, scrollBarCornerRadiusPx, scrollBarCornerRadiusPx, paint);
      return;
    }

    float trackHeight = h;
    float contentHeight = maxScroll + h;
    float thumbHeight = (trackHeight * trackHeight) / Math.max(1f, contentHeight);
    if (thumbHeight < scrollBarMinThumbPx) thumbHeight = scrollBarMinThumbPx;
    if (thumbHeight > trackHeight) thumbHeight = trackHeight;
    float thumbRange = Math.max(1f, trackHeight - thumbHeight);
    float thumbTop = (scroll.scrollY / maxScroll) * thumbRange;

    float right = w - scrollBarMarginPx;
    float left = right - scrollBarWidthPx;
    thumbRect.set(left, thumbTop, right, thumbTop + thumbHeight);

    int alphaInt = (int) (Math.min(1f, scrollBarAlpha) * 255);
    paint.setColor((scrollBarColor & 0x00FFFFFF) | (alphaInt << 24));

    if (scrollBarDragging) {
      int haloAlphaInt = (int) (alphaInt * 0.6f);
      haloPaint.setColor((scrollBarHaloColor & 0x00FFFFFF) | (haloAlphaInt << 24));
      float inset = Math.max(0f, scrollBarHaloSizePx);
      RectF halo =
          new RectF(
              thumbRect.left - inset,
              thumbRect.top - inset,
              thumbRect.right + inset,
              thumbRect.bottom + inset);
      float haloRadius = scrollBarCornerRadiusPx + inset;
      canvas.drawRoundRect(halo, haloRadius, haloRadius, haloPaint);
    }
    canvas.drawRoundRect(thumbRect, scrollBarCornerRadiusPx, scrollBarCornerRadiusPx, paint);
  }

  public void show() {
    if (!scrollBarEnabled) return;
    if (!scrollBarFadeEnabled) {
      scrollBarAlpha = 1f;
      editor.invalidate();
      return;
    }
    if (scrollBarAlpha >= 1f || (fadeAnimator != null && fadeAnimator.isRunning())) {
      editor.caret.mainHandler.removeCallbacks(hideRunnable);
      editor.caret.mainHandler.postDelayed(hideRunnable, scrollBarFadeDelayMs);
      return;
    }
    cancelFade();
    editor.caret.mainHandler.removeCallbacks(hideRunnable);
    fadeAnimator = ValueAnimator.ofFloat(scrollBarAlpha, 1f);
    fadeAnimator.setDuration(scrollBarFadeDurationMs);
    fadeAnimator.addUpdateListener(
        a -> {
          scrollBarAlpha = (float) a.getAnimatedValue();
          editor.invalidate();
        });
    fadeAnimator.addListener(
        new AnimatorListenerAdapter() {
          @Override
          public void onAnimationEnd(Animator a) {
            fadeAnimator = null;
            editor.caret.mainHandler.postDelayed(hideRunnable, scrollBarFadeDelayMs);
          }
        });
    fadeAnimator.start();
  }

  public void startFadeOut() {
    if (!scrollBarFadeEnabled || scrollBarDragging) return;
    if (fadeAnimator != null) fadeAnimator.cancel();
    fadeAnimator = ValueAnimator.ofFloat(scrollBarAlpha, 0f);
    fadeAnimator.setDuration(scrollBarFadeDurationMs);
    fadeAnimator.addUpdateListener(
        a -> {
          scrollBarAlpha = (float) a.getAnimatedValue();
          editor.invalidate();
        });
    fadeAnimator.addListener(
        new AnimatorListenerAdapter() {
          @Override
          public void onAnimationEnd(Animator a) {
            fadeAnimator = null;
          }
        });
    fadeAnimator.start();
  }

  public void cancelFade() {
    if (fadeAnimator != null) {
      fadeAnimator.cancel();
      fadeAnimator = null;
    }
  }

  // Setters
  public void setScrollBarEnabled(boolean e) {
    scrollBarEnabled = e;
    editor.invalidate();
  }

  public boolean getScrollBarEnabled() {
    return scrollBarEnabled;
  }

  public void setScrollBarFadeEnabled(boolean e) {
    scrollBarFadeEnabled = e;
    cancelFade();
    scrollBarAlpha = e ? 0f : 1f;
    editor.invalidate();
  }

  public boolean getScrollBarFadeEnabled() {
    return scrollBarFadeEnabled;
  }

  public void setScrollBarColor(int c) {
    scrollBarColor = c;
    editor.invalidate();
  }

  public int getScrollBarColor() {
    return scrollBarColor;
  }

  public void setScrollBarWidthPx(float px) {
    if (px > 0) scrollBarWidthPx = px;
    editor.invalidate();
  }

  public float getScrollBarWidthPx() {
    return scrollBarWidthPx;
  }

  public void setScrollBarMinThumbPx(float px) {
    if (px > 0) scrollBarMinThumbPx = px;
    editor.invalidate();
  }

  public float getScrollBarMinThumbPx() {
    return scrollBarMinThumbPx;
  }

  public void setScrollBarFadeDelayMs(long ms) {
    scrollBarFadeDelayMs = Math.max(0, ms);
  }

  public long getScrollBarFadeDelayMs() {
    return scrollBarFadeDelayMs;
  }

  public void setScrollBarFadeDurationMs(long ms) {
    scrollBarFadeDurationMs = Math.max(0, ms);
  }

  public long getScrollBarFadeDurationMs() {
    return scrollBarFadeDurationMs;
  }

  public void setScrollBarHaloColor(int c) {
    scrollBarHaloColor = c;
    editor.invalidate();
  }

  public int getScrollBarHaloColor() {
    return scrollBarHaloColor;
  }

  public void setScrollBarHaloSizePx(float px) {
    if (px >= 0) scrollBarHaloSizePx = px;
    editor.invalidate();
  }

  public float getScrollBarHaloSizePx() {
    return scrollBarHaloSizePx;
  }

  public void setScrollBarCornerRadiusPx(float px) {
    if (px >= 0) scrollBarCornerRadiusPx = px;
    editor.invalidate();
  }

  public float getScrollBarCornerRadiusPx() {
    return scrollBarCornerRadiusPx;
  }

  public void setScrollBarMarginPx(float px) {
    if (px >= 0) scrollBarMarginPx = px;
    editor.invalidate();
  }

  public float getScrollBarMarginPx() {
    return scrollBarMarginPx;
  }
}
