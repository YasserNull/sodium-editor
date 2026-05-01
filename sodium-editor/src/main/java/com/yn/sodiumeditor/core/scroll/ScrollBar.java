package com.yn.sodiumeditor.core.scroll;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import com.yn.sodiumeditor.SodiumEditor;
import com.yn.sodiumeditor.utils.FunctionLog;

/**
 * Handles drawing, fading, and dragging of the scroll bar.
 */
public class ScrollBar {
    private final SodiumEditor editor;
    private final Scroll scroll;

    public final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    public final Paint haloPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    public final RectF thumbRect = new RectF();
    public final Runnable hideRunnable = this::startFadeOut;
    public ValueAnimator fadeAnimator;

    // ScrollBar properties
    public boolean enabled = true;
    public int color = 0x88888888;
    public float widthPx = 6f;
    public float minThumbPx = 24f;
    public float cornerRadiusPx = 6f;
    public float marginPx = 2f;
    public boolean fadeEnabled = true;
    public long fadeDelayMs = 1000;
    public long fadeDurationMs = 200;
    public float alpha = 0f;
    public int haloColor = 0x40888888;
    public float haloSizePx = 8f;
    public boolean dragging = false;
    public float dragOffset = 0f;

    public ScrollBar(SodiumEditor editor, Scroll scroll) {
        FunctionLog.f("ScrollBar", "ScrollBar", editor, scroll);
        this.editor = editor;
        this.scroll = scroll;
        float density = editor.getContext().getResources().getDisplayMetrics().density;
        widthPx *= density;
        minThumbPx *= density;
        cornerRadiusPx *= density;
        marginPx *= density;
        haloSizePx *= density;
    }

    public void draw(Canvas canvas) {
        FunctionLog.f("ScrollBar", "draw", canvas);
        if (!enabled) return;
        boolean interacting =
                dragging
                        || editor.onTouch.pointerDown
                        || scroll.scrollerIsScrolling
                        || scroll.flingStopAnimator != null;
        if (fadeEnabled && alpha <= 0f) {
            if (interacting) alpha = 1f;
            else return;
        }
        int w = editor.getWidth();
        int h = editor.getHeight();
        if (w <= 0 || h <= 0) return;
        float maxScroll = scroll.getMaxScrollYForClamp();
        if (maxScroll <= 0f) {
            if (!interacting) return;
            float right = w - marginPx;
            float left = right - widthPx;
            thumbRect.set(left, 0f, right, h);
            int alphaInt = (int) (Math.min(1f, alpha) * 255);
            paint.setColor((color & 0x00FFFFFF) | (alphaInt << 24));
            canvas.drawRoundRect(thumbRect, cornerRadiusPx, cornerRadiusPx, paint);
            return;
        }

        float trackHeight = h;
        float contentHeight = maxScroll + h;
        float thumbHeight = (trackHeight * trackHeight) / Math.max(1f, contentHeight);
        if (thumbHeight < minThumbPx) thumbHeight = minThumbPx;
        if (thumbHeight > trackHeight) thumbHeight = trackHeight;
        float thumbRange = Math.max(1f, trackHeight - thumbHeight);
        float thumbTop = (scroll.scrollY / maxScroll) * thumbRange;

        float right = w - marginPx;
        float left = right - widthPx;
        thumbRect.set(left, thumbTop, right, thumbTop + thumbHeight);
        
        int alphaInt = (int) (Math.min(1f, alpha) * 255);
        paint.setColor((color & 0x00FFFFFF) | (alphaInt << 24));
        
        if (dragging) {
            int haloAlphaInt = (int) (alphaInt * 0.6f);
            haloPaint.setColor((haloColor & 0x00FFFFFF) | (haloAlphaInt << 24));
            float inset = Math.max(0f, haloSizePx);
            RectF halo = new RectF(thumbRect.left - inset, thumbRect.top - inset, thumbRect.right + inset, thumbRect.bottom + inset);
            float haloRadius = cornerRadiusPx + inset;
            canvas.drawRoundRect(halo, haloRadius, haloRadius, haloPaint);
        }
        canvas.drawRoundRect(thumbRect, cornerRadiusPx, cornerRadiusPx, paint);
    }

    public void show() {
        FunctionLog.f("ScrollBar", "show");
        if (!enabled) return;
        if (!fadeEnabled) { alpha = 1f; editor.invalidate(); return; }
        cancelFade();
        alpha = 1f;
        editor.invalidate();
        editor.caret.mainHandler.removeCallbacks(hideRunnable);
        editor.caret.mainHandler.postDelayed(hideRunnable, fadeDelayMs);
    }

    public void startFadeOut() {
        FunctionLog.f("ScrollBar", "startFadeOut");
        if (!fadeEnabled || dragging) return;
        if (fadeAnimator != null) fadeAnimator.cancel();
        fadeAnimator = ValueAnimator.ofFloat(alpha, 0f);
        fadeAnimator.setDuration(fadeDurationMs);
        fadeAnimator.addUpdateListener(a -> { alpha = (float) a.getAnimatedValue(); editor.invalidate(); });
        fadeAnimator.addListener(new AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(Animator a) { fadeAnimator = null; }
        });
        fadeAnimator.start();
    }

    public void cancelFade() {
        FunctionLog.f("ScrollBar", "cancelFade");
        if (fadeAnimator != null) { fadeAnimator.cancel(); fadeAnimator = null; }
    }

    // Setters
    public void setEnabled(boolean e) {
        FunctionLog.f("ScrollBar", "setEnabled", e);
        enabled = e; editor.invalidate();
    }

    public void setFadeEnabled(boolean e) {
        FunctionLog.f("ScrollBar", "setFadeEnabled", e);
        fadeEnabled = e; cancelFade(); alpha = e ? 0f : 1f; editor.invalidate();
    }

    public void setColor(int c) {
        FunctionLog.f("ScrollBar", "setColor", c);
        color = c; editor.invalidate();
    }

    public void setWidthPx(float px) {
        FunctionLog.f("ScrollBar", "setWidthPx", px);
        if (px > 0) widthPx = px; editor.invalidate();
    }

    public void setMinThumbPx(float px) {
        FunctionLog.f("ScrollBar", "setMinThumbPx", px);
        if (px > 0) minThumbPx = px; editor.invalidate();
    }

    public void setFadeDelayMs(long ms) {
        FunctionLog.f("ScrollBar", "setFadeDelayMs", ms);
        fadeDelayMs = Math.max(0, ms);
    }

    public void setFadeDurationMs(long ms) {
        FunctionLog.f("ScrollBar", "setFadeDurationMs", ms);
        fadeDurationMs = Math.max(0, ms);
    }

    public void setHaloColor(int c) {
        FunctionLog.f("ScrollBar", "setHaloColor", c);
        haloColor = c; editor.invalidate();
    }

    public void setHaloSizePx(float px) {
        FunctionLog.f("ScrollBar", "setHaloSizePx", px);
        if (px >= 0) haloSizePx = px; editor.invalidate();
    }

    public void setCornerRadiusPx(float px) {
        FunctionLog.f("ScrollBar", "setCornerRadiusPx", px);
        if (px >= 0) cornerRadiusPx = px; editor.invalidate();
    }

    public void setMarginPx(float px) {
        FunctionLog.f("ScrollBar", "setMarginPx", px);
        if (px >= 0) marginPx = px; editor.invalidate();
    }
}
