package com.yn.sodiumeditor.renderer.animation;

/**
 * SelectionHandlesAnimation handles the animation logic for selection handles.
 * This includes:
 * - Smooth handle position interpolation
 * - Animation enable/disable state
 */
public class SelectionHandlesAnimation {

    // Handle move animation state
    public boolean handleMoveAnimationEnabled = true;
    private float animLeftX = Float.NaN;
    private float animLeftY = Float.NaN;
    private float animRightX = Float.NaN;
    private float animRightY = Float.NaN;

    /**
     * Get animated handle position with smooth interpolation.
     * @param isLeft true for left handle, false for right handle
     * @param targetX target X position
     * @param targetY target Y position
     * @return float array [x, y] with the animated position
     */
    public float[] getAnimatedHandlePosition(boolean isLeft, float targetX, float targetY) {
        if (!handleMoveAnimationEnabled) {
            return new float[] {targetX, targetY};
        }
        float ax = isLeft ? animLeftX : animRightX;
        float ay = isLeft ? animLeftY : animRightY;
        if (Float.isNaN(ax) || Float.isNaN(ay)) {
            ax = targetX;
            ay = targetY;
        } else {
            float t = 0.35f;
            ax = ax + (targetX - ax) * t;
            ay = ay + (targetY - ay) * t;
        }
        if (isLeft) {
            animLeftX = ax;
            animLeftY = ay;
        } else {
            animRightX = ax;
            animRightY = ay;
        }
        return new float[] {ax, ay};
    }

    /**
     * Enable or disable handle move animation.
     */
    public void setHandleMoveAnimationEnabled(boolean enabled) {
        handleMoveAnimationEnabled = enabled;
    }

    /**
     * Reset animation state (e.g., when selection changes abruptly).
     */
    public void resetAnimationState() {
        animLeftX = Float.NaN;
        animLeftY = Float.NaN;
        animRightX = Float.NaN;
        animRightY = Float.NaN;
    }
}
