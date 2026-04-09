package com.yn.sodiumeditor.renderer.animation;

import android.animation.ValueAnimator;
import com.yn.sodiumeditor.SodiumEditor;

/**
 * LoadingCircleAnimation handles the rotation animation for the loading circle.
 * This includes:
 * - Rotation value tracking
 * - Start/stop rotation animation
 * - Animation state queries
 */
public class LoadingCircleAnimation {

    public static final long ROTATION_ANIM_DURATION_MS = 1000;

    private final SodiumEditor editor;

    // Rotation state
    public float loadingCircleRotation = 0f;
    public ValueAnimator rotationAnimator;

    public LoadingCircleAnimation(SodiumEditor editor) {
        this.editor = editor;
    }

    /**
     * Start rotation animation.
     */
    public void startRotation() {
        if (rotationAnimator == null) {
            rotationAnimator = ValueAnimator.ofFloat(0f, 360f);
            rotationAnimator.setDuration(ROTATION_ANIM_DURATION_MS);
            rotationAnimator.setRepeatCount(ValueAnimator.INFINITE);
            rotationAnimator.addUpdateListener(
                animation -> {
                    loadingCircleRotation = (float) animation.getAnimatedValue();
                    editor.invalidate();
                });
        }
        if (!rotationAnimator.isRunning()) {
            rotationAnimator.start();
        }
    }

    /**
     * Stop rotation animation.
     */
    public void stopRotation() {
        if (rotationAnimator != null && rotationAnimator.isRunning()) {
            rotationAnimator.cancel();
        }
        loadingCircleRotation = 0f;
    }

    /**
     * Check if the animation is currently running.
     */
    public boolean isAnimating() {
        return rotationAnimator != null && rotationAnimator.isRunning();
    }

    /**
     * Get the current rotation angle in degrees.
     */
    public float getRotation() {
        return loadingCircleRotation;
    }

    /**
     * Cancel animation and cleanup.
     */
    public void cancel() {
        stopRotation();
        if (rotationAnimator != null) {
            rotationAnimator = null;
        }
    }
}
