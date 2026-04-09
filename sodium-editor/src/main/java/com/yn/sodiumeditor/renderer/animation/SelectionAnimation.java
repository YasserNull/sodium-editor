package com.yn.sodiumeditor.renderer.animation;

import android.animation.ValueAnimator;
import com.yn.sodiumeditor.SodiumEditor;

/**
 * SelectionAnimation handles the fade in/out animation for selection
 * highlights and handles.
 */
public class SelectionAnimation {

    private final SodiumEditor editor;

    // Animation state
    public boolean selectionAnimationEnabled = true;
    public float selectionAlpha = 1f;
    public float handleAlpha = 1f;
    private ValueAnimator selectionFadeAnimator;
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
            if (selectionFadeAnimator != null) {
                selectionFadeAnimator.cancel();
                selectionFadeAnimator = null;
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
            return;
        }
        if (selectionFadeAnimator != null) {
            selectionFadeAnimator.cancel();
        }
        float start = nowHasSelection ? 0f : 1f;
        float end = nowHasSelection ? 1f : 0f;
        selectionFadeAnimator = ValueAnimator.ofFloat(start, end);
        selectionFadeAnimator.setDuration(140);
        selectionFadeAnimator.addUpdateListener(a -> {
            float v = (float) a.getAnimatedValue();
            selectionAlpha = v;
            handleAlpha = v;
            editor.invalidate();
        });
        selectionFadeAnimator.start();
    }

    /**
     * Cancel any running animation.
     */
    public void cancelAnimation() {
        if (selectionFadeAnimator != null) {
            selectionFadeAnimator.cancel();
        }
    }

    /**
     * Check if animation is currently running.
     */
    public boolean isAnimating() {
        return selectionFadeAnimator != null && selectionFadeAnimator.isRunning();
    }
}
