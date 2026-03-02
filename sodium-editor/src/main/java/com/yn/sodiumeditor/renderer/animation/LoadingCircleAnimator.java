package com.yn.sodiumeditor.renderer.animation;

import android.animation.ValueAnimator;
import com.yn.sodiumeditor.SodiumEditor;
import com.yn.sodiumeditor.state.LoadingCircleState;

/**
 * Animator class for loading circle rotation.
 * Handles the spinning animation of the loading indicator.
 */
public class LoadingCircleAnimator {

    private static final long ANIMATION_DURATION_MS = 1000;

    private final SodiumEditor view;
    private final LoadingCircleState state;
    private ValueAnimator rotationAnimator;

    public LoadingCircleAnimator(SodiumEditor view, LoadingCircleState state) {
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

    //================================================================================
    // Large Edit UI Management
    //================================================================================

    /**
     * Checks if a large edit UI should be shown based on the selection size.
     */
    public boolean shouldShowLargeEditUi(int sL, int eL, boolean isSelectAllLike, int largeEditLinesThreshold) {
        int span = Math.abs(eL - sL) + 1;
        return isSelectAllLike || span >= largeEditLinesThreshold;
    }

    /**
     * Begins the large edit UI if needed (shows loading circle and disables the view).
     */
    public void beginLargeEditUiIfNeeded(
            boolean enable,
            int sL,
            int eL,
            boolean isSelectAllLike,
            int largeEditLinesThreshold,
            android.os.Handler mainHandler,
            Runnable largeEditUiWatchdog,
            android.view.View viewToDisable,
            java.util.concurrent.atomic.AtomicInteger largeEditUiToken) {
        
        if (!enable) return;
        if (!shouldShowLargeEditUi(sL, eL, isSelectAllLike, largeEditLinesThreshold)) return;

        final int token = largeEditUiToken.incrementAndGet();
        viewToDisable.setEnabled(false);
        show(true);

        // Watchdog: force hide after a short time in case any path forgets to hide.
        mainHandler.removeCallbacks(largeEditUiWatchdog);
        mainHandler.postDelayed(largeEditUiWatchdog, 1500);
    }

    /**
     * Ends the large edit UI (hides loading circle and enables the view).
     */
    public void endLargeEditUi(
            boolean invalidate,
            android.view.View viewToDisable,
            java.util.concurrent.atomic.AtomicInteger largeEditUiToken,
            android.os.Handler mainHandler,
            Runnable largeEditUiWatchdog) {
        
        largeEditUiToken.incrementAndGet();
        mainHandler.removeCallbacks(largeEditUiWatchdog);
        viewToDisable.setEnabled(true);
        show(false);
        if (invalidate && viewToDisable instanceof android.view.View) {
            ((android.view.View) viewToDisable).invalidate();
        }
    }
}
