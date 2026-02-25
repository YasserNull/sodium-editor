package com.yn.sodiumeditor;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.view.animation.DecelerateInterpolator;
import androidx.annotation.Nullable;

public final class Stretch {
    private final SodiumEditorView view;
    private final ScrollConfig config;
    @Nullable public ValueAnimator stretchReleaseAnimator;

    public Stretch(SodiumEditorView view, ScrollConfig config) {
        this.view = view;
        this.config = config;
    }

    public void releaseStretch() {
        if (!config.stretchOverscrollEnabled) return;
        if (config.stretchX == 0f && config.stretchY == 0f) return;
        cancelStretchRelease();
        final float startX = config.stretchX;
        final float startY = config.stretchY;
        stretchReleaseAnimator = ValueAnimator.ofFloat(0f, 1f);
        stretchReleaseAnimator.setDuration(220);
        stretchReleaseAnimator.setInterpolator(new DecelerateInterpolator());
        stretchReleaseAnimator.addUpdateListener(
                a -> {
                    float t = (float) a.getAnimatedValue();
                    float inv = 1f - t;
                    config.stretchX = startX * inv;
                    config.stretchY = startY * inv;
                    view.scrollManager.stretchX = config.stretchX;
                    view.scrollManager.stretchY = config.stretchY;
                    if (config.stretchX == 0f) config.stretchDirX = 0;
                    if (config.stretchY == 0f) config.stretchDirY = 0;
                    view.scrollManager.stretchDirX = config.stretchDirX;
                    view.scrollManager.stretchDirY = config.stretchDirY;
                    view.invalidate();
                });
        stretchReleaseAnimator.addListener(
                new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        stretchReleaseAnimator = null;
                        config.stretchX = 0f;
                        config.stretchY = 0f;
                        config.stretchDirX = 0;
                        config.stretchDirY = 0;
                        view.scrollManager.stretchX = 0f;
                        view.scrollManager.stretchY = 0f;
                        view.scrollManager.stretchDirX = 0;
                        view.scrollManager.stretchDirY = 0;
                    }

                    @Override
                    public void onAnimationCancel(Animator animation) {
                        stretchReleaseAnimator = null;
                    }
                });
        stretchReleaseAnimator.start();
    }

    public void pullStretchX(float deltaPx, boolean toRight) {
        if (!config.stretchOverscrollEnabled || view.wrapWordState.isWordWrapEnabled) return;
        if (view.getWidth() <= 0) return;
        cancelStretchRelease();
        float norm = Math.abs(deltaPx) / (float) view.getWidth();
        float gain = norm * 0.6f * config.stretchOverscrollStrength;
        config.stretchDirX = toRight ? 1 : -1;
        config.stretchX = Math.min(1f, config.stretchX + gain);
        view.scrollManager.stretchDirX = config.stretchDirX;
        view.scrollManager.stretchX = config.stretchX;
    }

    public void pullStretchY(float deltaPx, boolean toBottom) {
        if (!config.stretchOverscrollEnabled) return;
        if (view.getHeight() <= 0) return;
        cancelStretchRelease();
        float norm = Math.abs(deltaPx) / (float) view.getHeight();
        float gain = norm * 0.6f * config.stretchOverscrollStrength;
        config.stretchDirY = toBottom ? 1 : -1;
        config.stretchY = Math.min(1f, config.stretchY + gain);
        view.scrollManager.stretchDirY = config.stretchDirY;
        view.scrollManager.stretchY = config.stretchY;
    }

    public void absorbStretchX(float velocityPxPerSec, boolean toRight) {
        if (!config.stretchOverscrollEnabled || view.wrapWordState.isWordWrapEnabled) return;
        cancelStretchRelease();
        float v = Math.min(1f, Math.abs(velocityPxPerSec) / 6000f);
        config.stretchDirX = toRight ? 1 : -1;
        config.stretchX = Math.min(1f, config.stretchX + v * 0.8f * config.stretchOverscrollStrength);
        view.scrollManager.stretchDirX = config.stretchDirX;
        view.scrollManager.stretchX = config.stretchX;
    }

    public void absorbStretchY(float velocityPxPerSec, boolean toBottom) {
        if (!config.stretchOverscrollEnabled) return;
        cancelStretchRelease();
        float v = Math.min(1f, Math.abs(velocityPxPerSec) / 6000f);
        config.stretchDirY = toBottom ? 1 : -1;
        config.stretchY = Math.min(1f, config.stretchY + v * 0.8f * config.stretchOverscrollStrength);
        view.scrollManager.stretchDirY = config.stretchDirY;
        view.scrollManager.stretchY = config.stretchY;
    }

    public void cancelStretchRelease() {
        if (stretchReleaseAnimator != null) {
            stretchReleaseAnimator.cancel();
            stretchReleaseAnimator = null;
        }
    }

    public void setStretchOverscrollEnabled(boolean enabled) {
        if (config.stretchOverscrollEnabled == enabled) return;
        config.stretchOverscrollEnabled = enabled;
        if (!enabled) {
            config.resetStretchState();
            cancelStretchRelease();
            view.invalidate();
        }
    }

    public void setStretchOverscrollStrength(float strength) {
        if (strength <= 0f) return;
        config.stretchOverscrollStrength = strength;
    }
}
