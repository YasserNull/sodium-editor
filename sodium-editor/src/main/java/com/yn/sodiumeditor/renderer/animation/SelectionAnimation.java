package com.yn.sodiumeditor.renderer.animation;

import android.animation.ValueAnimator;
import android.view.animation.PathInterpolator;
import com.yn.sodiumeditor.SodiumEditor;

/**
 * SelectionAnimation handles the fade in/out animation for selection
 * highlights and handles.
 */
public class SelectionAnimation {
    private static final long FADE_DURATION_MS = 140L;
    private static final long GEOMETRY_DURATION_MS = 120L;

    private final SodiumEditor editor;
    private final PathInterpolator smoothInterpolator = new PathInterpolator(0.4f, 0f, 0.2f, 1f);

    // Animation state
    public boolean selectionAnimationEnabled = true;
    public float selectionAlpha = 1f;
    public float handleAlpha = 1f;
    public float geometryProgress = 1f;
    private ValueAnimator selectionFadeAnimator;
    private ValueAnimator selectionGeometryAnimator;
    private boolean lastHasSelection = false;

    public SelectionAnimation(SodiumEditor editor) {
        this.editor = editor;
    }

    /**
     * Enable or disable selection animation.
     */
    public void setSelectionAnimationEnabled(boolean enabled) {
        if (selectionAnimationEnabled == enabled) return;
        selectionAnimationEnabled = enabled;
        if (!selectionAnimationEnabled) {
            selectionAlpha = 1f;
            handleAlpha = 1f;
            geometryProgress = 1f;
            if (selectionFadeAnimator != null) {
                selectionFadeAnimator.cancel();
                selectionFadeAnimator = null;
            }
            if (selectionGeometryAnimator != null) {
                selectionGeometryAnimator.cancel();
                selectionGeometryAnimator = null;
            }
            editor.invalidate();
        }
    }

    /**
     * Update selection visibility with fade animation.
     * @param nowHasSelection true if selection is now active
     */
    public void updateSelectionVisibility(boolean nowHasSelection) {
        if (nowHasSelection == lastHasSelection) return;
        lastHasSelection = nowHasSelection;
        if (!selectionAnimationEnabled) {
            selectionAlpha = nowHasSelection ? 1f : 0f;
            handleAlpha = nowHasSelection ? 1f : 0f;
            geometryProgress = 1f;
            return;
        }
        if (selectionFadeAnimator != null) {
            selectionFadeAnimator.cancel();
        }
        float start = nowHasSelection ? 0f : 1f;
        float end = nowHasSelection ? 1f : 0f;
        selectionFadeAnimator = ValueAnimator.ofFloat(start, end);
        selectionFadeAnimator.setDuration(FADE_DURATION_MS);
        selectionFadeAnimator.setInterpolator(smoothInterpolator);
        selectionFadeAnimator.addUpdateListener(a -> {
            float v = (float) a.getAnimatedValue();
            selectionAlpha = v;
            handleAlpha = v;
            editor.invalidate();
        });
        selectionFadeAnimator.start();
    }

    public void updateSelectionGeometry(boolean hasSelection, boolean geometryChanged) {
        if (!hasSelection || !geometryChanged) return;
        if (!selectionAnimationEnabled) {
            geometryProgress = 1f;
            return;
        }
        if (selectionGeometryAnimator != null) {
            selectionGeometryAnimator.cancel();
        }
        float start = Math.max(0f, Math.min(1f, geometryProgress));
        if (start > 0.98f) {
            start = 0.72f;
        } else {
            start = Math.max(0.58f, start * 0.82f);
        }
        selectionGeometryAnimator = ValueAnimator.ofFloat(start, 1f);
        selectionGeometryAnimator.setDuration(GEOMETRY_DURATION_MS);
        selectionGeometryAnimator.setInterpolator(smoothInterpolator);
        selectionGeometryAnimator.addUpdateListener(
                a -> {
                    geometryProgress = (float) a.getAnimatedValue();
                    editor.invalidate();
                });
        selectionGeometryAnimator.start();
    }

    /**
     * Cancel any running animation.
     */
    public void cancelAnimation() {
        if (selectionFadeAnimator != null) {
            selectionFadeAnimator.cancel();
        }
        if (selectionGeometryAnimator != null) {
            selectionGeometryAnimator.cancel();
        }
    }

    /**
     * Check if animation is currently running.
     */
    public boolean isAnimating() {
        return (selectionFadeAnimator != null && selectionFadeAnimator.isRunning())
                || (selectionGeometryAnimator != null && selectionGeometryAnimator.isRunning());
    }
}
