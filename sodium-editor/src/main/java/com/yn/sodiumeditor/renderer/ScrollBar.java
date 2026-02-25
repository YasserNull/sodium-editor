package com.yn.sodiumeditor;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.animation.DecelerateInterpolator;
import androidx.annotation.Nullable;

public final class ScrollBar {
    private final SodiumEditorView view;
    private final ScrollConfig config;
    private final ScrollBoundsProvider boundsProvider;

    public final Paint scrollBarPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    public final Paint scrollBarHaloPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    public final RectF scrollBarThumbRect = new RectF();
    @Nullable public ValueAnimator scrollBarFadeAnimator;
    public final Runnable scrollBarHideRunnable;

    public interface ScrollBoundsProvider {
        float getMaxScrollYForClamp();
    }

    public ScrollBar(SodiumEditorView view, ScrollConfig config, ScrollBoundsProvider boundsProvider) {
        this.view = view;
        this.config = config;
        this.boundsProvider = boundsProvider;
        this.scrollBarHideRunnable = this::startScrollBarFadeOut;
    }

    public void drawScrollBar(Canvas canvas) {
        if (!config.scrollBarEnabled) return;
        if (config.scrollBarFadeEnabled && config.scrollBarAlpha <= 0f) return;
        int w = view.getWidth();
        int h = view.getHeight();
        if (w <= 0 || h <= 0) return;
        float maxScroll = boundsProvider.getMaxScrollYForClamp();
        if (maxScroll <= 0f) return;

        float trackHeight = h;
        float contentHeight = maxScroll + h;
        float thumbHeight = (trackHeight * trackHeight) / Math.max(1f, contentHeight);
        if (thumbHeight < config.scrollBarMinThumbPx) thumbHeight = config.scrollBarMinThumbPx;
        if (thumbHeight > trackHeight) thumbHeight = trackHeight;
        float thumbRange = Math.max(1f, trackHeight - thumbHeight);
        float thumbTop = (config.scrollY / maxScroll) * thumbRange;

        float right = w - config.scrollBarMarginPx;
        float left = right - config.scrollBarWidthPx;
        scrollBarThumbRect.set(left, thumbTop, right, thumbTop + thumbHeight);
        int baseColor = config.scrollBarColor;
        int alpha = (int) (Math.min(1f, config.scrollBarAlpha) * 255);
        int color = (baseColor & 0x00FFFFFF) | (alpha << 24);
        scrollBarPaint.setColor(color);
        if (config.draggingScrollBar) {
            int haloAlpha = (int) (alpha * 0.6f);
            int haloColor = (config.scrollBarHaloColor & 0x00FFFFFF) | (haloAlpha << 24);
            scrollBarHaloPaint.setColor(haloColor);
            float inset = Math.max(0f, config.scrollBarHaloSizePx);
            RectF halo = new RectF(
                    scrollBarThumbRect.left - inset,
                    scrollBarThumbRect.top - inset,
                    scrollBarThumbRect.right + inset,
                    scrollBarThumbRect.bottom + inset);
            float haloRadius = config.scrollBarCornerRadiusPx + inset;
            canvas.drawRoundRect(halo, haloRadius, haloRadius, scrollBarHaloPaint);
        }
        canvas.drawRoundRect(
                scrollBarThumbRect,
                config.scrollBarCornerRadiusPx,
                config.scrollBarCornerRadiusPx,
                scrollBarPaint);
    }

    public void showScrollBar() {
        if (!config.scrollBarEnabled) return;
        if (!config.scrollBarFadeEnabled) {
            config.scrollBarAlpha = 1f;
            return;
        }
        cancelScrollBarFade();
        config.scrollBarAlpha = 1f;
        view.invalidate();
        view.mainHandler.removeCallbacks(scrollBarHideRunnable);
        view.mainHandler.postDelayed(scrollBarHideRunnable, config.scrollBarFadeDelayMs);
    }

    public void startScrollBarFadeOut() {
        if (!config.scrollBarFadeEnabled || config.draggingScrollBar) return;
        cancelScrollBarFade();
        final float start = config.scrollBarAlpha;
        if (start <= 0f) return;
        scrollBarFadeAnimator = ValueAnimator.ofFloat(0f, 1f);
        scrollBarFadeAnimator.setDuration(config.scrollBarFadeDurationMs);
        scrollBarFadeAnimator.setInterpolator(new DecelerateInterpolator());
        scrollBarFadeAnimator.addUpdateListener(
                a -> {
                    float t = (float) a.getAnimatedValue();
                    config.scrollBarAlpha = Math.max(0f, start * (1f - t));
                    view.invalidate();
                });
        scrollBarFadeAnimator.addListener(
                new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        scrollBarFadeAnimator = null;
                    }

                    @Override
                    public void onAnimationCancel(Animator animation) {
                        scrollBarFadeAnimator = null;
                    }
                });
        scrollBarFadeAnimator.start();
    }

    public void cancelScrollBarFade() {
        if (scrollBarFadeAnimator != null) {
            scrollBarFadeAnimator.cancel();
            scrollBarFadeAnimator = null;
        }
    }

    public void setScrollBarEnabled(boolean enabled) {
        if (config.scrollBarEnabled == enabled) return;
        config.scrollBarEnabled = enabled;
        view.invalidate();
    }

    public void setScrollBarColor(int color) {
        config.scrollBarColor = color;
        view.invalidate();
    }

    public void setScrollBarWidthPx(float px) {
        if (px <= 0f) return;
        config.scrollBarWidthPx = px;
        view.invalidate();
    }

    public void setScrollBarMinThumbPx(float px) {
        if (px <= 0f) return;
        config.scrollBarMinThumbPx = px;
        view.invalidate();
    }

    public void setScrollBarFadeEnabled(boolean enabled) {
        config.scrollBarFadeEnabled = enabled;
        if (!enabled) {
            cancelScrollBarFade();
            config.scrollBarAlpha = 1f;
            view.invalidate();
        } else {
            cancelScrollBarFade();
            config.scrollBarAlpha = 0f;
            view.invalidate();
        }
    }

    public void setScrollBarFadeDelayMs(long ms) {
        config.scrollBarFadeDelayMs = Math.max(0, ms);
    }

    public void setScrollBarFadeDurationMs(long ms) {
        config.scrollBarFadeDurationMs = Math.max(0, ms);
    }

    public void setScrollBarHaloColor(int color) {
        config.scrollBarHaloColor = color;
        view.invalidate();
    }

    public void setScrollBarHaloSizePx(float px) {
        if (px < 0f) return;
        config.scrollBarHaloSizePx = px;
        view.invalidate();
    }

    public void setScrollBarCornerRadiusPx(float px) {
        if (px < 0f) return;
        config.scrollBarCornerRadiusPx = px;
        view.invalidate();
    }

    public void setScrollBarMarginPx(float px) {
        if (px < 0f) return;
        config.scrollBarMarginPx = px;
        view.invalidate();
    }
}
