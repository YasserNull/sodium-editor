package com.yn.sodiumeditor.renderer.animation;

import android.animation.ValueAnimator;
import com.yn.sodiumeditor.SodiumEditorView;
import com.yn.sodiumeditor.state.LoadingCircleState;

/**
 * Animator class for loading circle rotation.
 * Handles the spinning animation of the loading indicator.
 */
public class LoadingCircleAnimator {

    private static final long ANIMATION_DURATION_MS = 1000;

    private final SodiumEditorView view;
    private final LoadingCircleState state;
    private ValueAnimator rotationAnimator;

    public LoadingCircleAnimator(SodiumEditorView view, LoadingCircleState state) {
        this.view = view;
        this.state = state;
    }

    public void show(boolean enabled) {
        state.setShow(enabled);

        if (enabled) {
            if (rotationAnimator == null) {
                rotationAnimator = ValueAnimator.ofFloat(0f, 360f);
                rotationAnimator.setDuration(ANIMATION_DURATION_MS);
                rotationAnimator.setRepeatCount(ValueAnimator.INFINITE);
                rotationAnimator.addUpdateListener(
                        animation -> {
                            state.setRotation((float) animation.getAnimatedValue());
                            view.invalidate();
                        });
            }
            if (!rotationAnimator.isRunning()) {
                rotationAnimator.start();
            }
        } else {
            if (rotationAnimator != null && rotationAnimator.isRunning()) {
                rotationAnimator.cancel();
            }
            state.setRotation(0f);
        }

        view.invalidate();
    }

    public boolean isRunning() {
        return rotationAnimator != null && rotationAnimator.isRunning();
    }

    public void cancel() {
        if (rotationAnimator != null && rotationAnimator.isRunning()) {
            rotationAnimator.cancel();
        }
        state.setRotation(0f);
    }

    public void cleanup() {
        if (rotationAnimator != null) {
            rotationAnimator.cancel();
            rotationAnimator = null;
        }
    }
}
