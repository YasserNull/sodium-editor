package com.yn.sodiumeditor.core;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import com.yn.sodiumeditor.SodiumEditor;

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

    public ScrollBar(SodiumEditor editor, Scroll scroll) {
        this.editor = editor;
        this.scroll = scroll;
    }

    public void draw(Canvas canvas) {
        if (!scroll.scrollBarEnabled || (scroll.scrollBarFadeEnabled && scroll.scrollBarAlpha <= 0f)) return;
        int w = editor.getWidth();
        int h = editor.getHeight();
        if (w <= 0 || h <= 0) return;
        float maxScroll = scroll.getMaxScrollYForClamp();
        if (maxScroll <= 0f) return;

        float trackHeight = h;
        float contentHeight = maxScroll + h;
        float thumbHeight = (trackHeight * trackHeight) / Math.max(1f, contentHeight);
        if (thumbHeight < scroll.scrollBarMinThumbPx) thumbHeight = scroll.scrollBarMinThumbPx;
        if (thumbHeight > trackHeight) thumbHeight = trackHeight;
        float thumbRange = Math.max(1f, trackHeight - thumbHeight);
        float thumbTop = (scroll.scrollY / maxScroll) * thumbRange;

        float right = w - scroll.scrollBarMarginPx;
        float left = right - scroll.scrollBarWidthPx;
        thumbRect.set(left, thumbTop, right, thumbTop + thumbHeight);
        
        int alpha = (int) (Math.min(1f, scroll.scrollBarAlpha) * 255);
        paint.setColor((scroll.scrollBarColor & 0x00FFFFFF) | (alpha << 24));
        
        if (scroll.draggingScrollBar) {
            int haloAlpha = (int) (alpha * 0.6f);
            haloPaint.setColor((scroll.scrollBarHaloColor & 0x00FFFFFF) | (haloAlpha << 24));
            float inset = Math.max(0f, scroll.scrollBarHaloSizePx);
            RectF halo = new RectF(thumbRect.left - inset, thumbRect.top - inset, thumbRect.right + inset, thumbRect.bottom + inset);
            float haloRadius = scroll.scrollBarCornerRadiusPx + inset;
            canvas.drawRoundRect(halo, haloRadius, haloRadius, haloPaint);
        }
        canvas.drawRoundRect(thumbRect, scroll.scrollBarCornerRadiusPx, scroll.scrollBarCornerRadiusPx, paint);
    }

    public void show() {
        if (!scroll.scrollBarEnabled) return;
        if (!scroll.scrollBarFadeEnabled) { scroll.scrollBarAlpha = 1f; return; }
        cancelFade();
        scroll.scrollBarAlpha = 1f;
        editor.invalidate();
        editor.caret.mainHandler.removeCallbacks(hideRunnable);
        editor.caret.mainHandler.postDelayed(hideRunnable, scroll.scrollBarFadeDelayMs);
    }

    public void startFadeOut() {
        if (!scroll.scrollBarFadeEnabled || scroll.draggingScrollBar) return;
        if (fadeAnimator != null) fadeAnimator.cancel();
        fadeAnimator = ValueAnimator.ofFloat(scroll.scrollBarAlpha, 0f);
        fadeAnimator.setDuration(scroll.scrollBarFadeDurationMs);
        fadeAnimator.addUpdateListener(a -> { scroll.scrollBarAlpha = (float) a.getAnimatedValue(); editor.invalidate(); });
        fadeAnimator.addListener(new AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(Animator a) { fadeAnimator = null; }
        });
        fadeAnimator.start();
    }

    public void cancelFade() {
        if (fadeAnimator != null) { fadeAnimator.cancel(); fadeAnimator = null; }
    }
}
