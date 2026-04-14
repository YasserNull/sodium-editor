package com.yn.sodiumeditor.renderer.animation;

import android.animation.ValueAnimator;
import android.view.animation.PathInterpolator;
import com.yn.sodiumeditor.SodiumEditor;

/**
 * CurrentLineHighlightAnimation handles the smooth sliding animation
 * for the current line highlight indicator.
 */
public class CurrentLineHighlightAnimation {

    private final SodiumEditor editor;

    // Animation state
    private float animatedVisualIndex = -1f;
    private float lastTargetIndex = -1f;
    private ValueAnimator lineAnimator;

    // Smooth interpolator (Bezier curve for material motion)
    private final PathInterpolator smoothInterpolator = new PathInterpolator(0.4f, 0f, 0.2f, 1f);

    // Animation enable/disable
    public boolean isCurrentLineAnimationEnabled = true;

    public CurrentLineHighlightAnimation(SodiumEditor editor) {
        this.editor = editor;
    }

    /**
     * Get the target visual index based on cursor position.
     */
    private float getTargetVisualIndex() {
        if (editor.wordWrap.isWordWrapEnabled) {
            return editor.wordWrap.getVisualIndexForLineAndChar(editor.cursor.cursorLine, 0);
        } else if (editor.codeFold.isCodeFoldingEnabled) {
            return editor.codeFold.getVisibleIndexForGlobalLine(editor.cursor.cursorLine);
        } else {
            return editor.cursor.cursorLine;
        }
    }

    /**
     * Check if animation needs to be started and run it.
     */
    public void checkAndStartAnimation() {
        float target = getTargetVisualIndex();
        if (target < 0) return;

        // Initialize if first time
        if (animatedVisualIndex < 0) {
            animatedVisualIndex = target;
            lastTargetIndex = target;
            return;
        }

        // If target hasn't changed, do nothing
        if (Math.abs(target - lastTargetIndex) < 0.01f) {
            return;
        }

        lastTargetIndex = target;

        if (!isCurrentLineAnimationEnabled) {
            animatedVisualIndex = target;
            return;
        }

        if (lineAnimator != null) lineAnimator.cancel();

        lineAnimator = ValueAnimator.ofFloat(animatedVisualIndex, target);
        long duration = 140L;
        lineAnimator.setDuration(duration);
        editor.cursorAnimation.setAnimationDurationMs(duration);
        lineAnimator.setInterpolator(smoothInterpolator);
        lineAnimator.addUpdateListener(animation -> {
            animatedVisualIndex = (float) animation.getAnimatedValue();
            editor.postInvalidateOnAnimation();
        });
        lineAnimator.start();
    }

    /**
     * Get the current animated visual index (starts animation if needed).
     */
    public float getAnimatedVisualIndex() {
        checkAndStartAnimation();
        return animatedVisualIndex;
    }

    /**
     * Enable or disable the animation.
     */
    public void setAnimationEnabled(boolean enabled) {
        this.isCurrentLineAnimationEnabled = enabled;
    }

    /**
     * Cancel any running animation.
     */
    public void cancelAnimation() {
        if (lineAnimator != null) {
            lineAnimator.cancel();
        }
    }
}
