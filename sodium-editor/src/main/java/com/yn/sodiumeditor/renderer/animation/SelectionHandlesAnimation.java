package com.yn.sodiumeditor.renderer.animation;

import android.os.SystemClock;

/**
 * SelectionHandlesAnimation handles the animation logic for selection handles.
 */
public class SelectionHandlesAnimation {

    // Animation duration in ms
    private static final long ANIM_DURATION = 120;
    private static final long FAST_REDIRECT_MIN_DURATION = 18;
    private static final long FAST_REDIRECT_MAX_DURATION = 68;
    private static final float SNAP_DISTANCE_THRESHOLD_PX = 420f;
    private static final float FAST_SPEED_THRESHOLD_PX_PER_MS = 3.8f;

    public boolean handleMoveAnimationEnabled = true;
    public boolean fastDragAnimationActive = false;
    
    private float leftStartX = Float.NaN, leftStartY = Float.NaN;
    private float leftTargetX = Float.NaN, leftTargetY = Float.NaN;
    private long leftStartTime = 0;
    
    private float rightStartX = Float.NaN, rightStartY = Float.NaN;
    private float rightTargetX = Float.NaN, rightTargetY = Float.NaN;
    private long rightStartTime = 0;
    private long leftAnimDuration = ANIM_DURATION;
    private long rightAnimDuration = ANIM_DURATION;

    public float[] getAnimatedHandlePosition(boolean isLeft, float targetX, float targetY) {
        if (!handleMoveAnimationEnabled) {
            return new float[] {targetX, targetY};
        }
        
        long now = SystemClock.uptimeMillis();
        float curStartX = isLeft ? leftStartX : rightStartX;
        float curTargetX = isLeft ? leftTargetX : rightTargetX;
        float curTargetY = isLeft ? leftTargetY : rightTargetY;
        long curStartTime = isLeft ? leftStartTime : rightStartTime;

        if (Float.isNaN(curStartX) || curTargetX != targetX || curTargetY != targetY) {
            float currentDrawX = isLeft ? animLeftX : animRightX;
            float currentDrawY = isLeft ? animLeftY : animRightY;
            float drawX = Float.isNaN(currentDrawX) ? targetX : currentDrawX;
            float drawY = Float.isNaN(currentDrawY) ? targetY : currentDrawY;
            float redirectDistance = (float) Math.hypot(targetX - drawX, targetY - drawY);
            long timeSinceLastRedirect = Math.max(0L, now - curStartTime);
            float redirectSpeed = redirectDistance / Math.max(1f, (float) timeSinceLastRedirect);
            boolean shouldSnap = redirectDistance >= SNAP_DISTANCE_THRESHOLD_PX;
            long duration = ANIM_DURATION;
            if (fastDragAnimationActive) {
                float speedRatio = Math.min(1f, redirectSpeed / FAST_SPEED_THRESHOLD_PX_PER_MS);
                duration =
                    Math.round(
                        FAST_REDIRECT_MAX_DURATION
                            - ((FAST_REDIRECT_MAX_DURATION - FAST_REDIRECT_MIN_DURATION) * speedRatio));
                duration = Math.max(FAST_REDIRECT_MIN_DURATION, Math.min(FAST_REDIRECT_MAX_DURATION, duration));
            }
            // Target changed or first time
            if (isLeft) {
                leftStartX = drawX;
                leftStartY = drawY;
                leftTargetX = targetX;
                leftTargetY = targetY;
                leftStartTime = now;
                leftAnimDuration = duration;
            } else {
                rightStartX = drawX;
                rightStartY = drawY;
                rightTargetX = targetX;
                rightTargetY = targetY;
                rightStartTime = now;
                rightAnimDuration = duration;
            }
            if (fastDragAnimationActive && shouldSnap) {
                snapHandlePosition(isLeft, targetX, targetY);
                return new float[] {targetX, targetY};
            }
            curStartX = isLeft ? leftStartX : rightStartX;
            curStartTime = now;
        }

        long duration = isLeft ? leftAnimDuration : rightAnimDuration;
        float t = Math.min(1f, (float)(now - curStartTime) / Math.max(1L, duration));
        // Quadratic ease-out
        float eased = 1f - (1f - t) * (1f - t);
        
        float ax, ay;
        if (isLeft) {
            ax = leftStartX + (leftTargetX - leftStartX) * eased;
            ay = leftStartY + (leftTargetY - leftStartY) * eased;
            animLeftX = ax;
            animLeftY = ay;
        } else {
            ax = rightStartX + (rightTargetX - rightStartX) * eased;
            ay = rightStartY + (rightTargetY - rightStartY) * eased;
            animRightX = ax;
            animRightY = ay;
        }
        
        return new float[] {ax, ay};
    }

    public void snapHandlePosition(boolean isLeft, float targetX, float targetY) {
        long now = SystemClock.uptimeMillis();
        if (isLeft) {
            leftStartX = targetX;
            leftStartY = targetY;
            leftTargetX = targetX;
            leftTargetY = targetY;
            leftStartTime = now;
            leftAnimDuration = 0L;
            animLeftX = targetX;
            animLeftY = targetY;
        } else {
            rightStartX = targetX;
            rightStartY = targetY;
            rightTargetX = targetX;
            rightTargetY = targetY;
            rightStartTime = now;
            rightAnimDuration = 0L;
            animRightX = targetX;
            animRightY = targetY;
        }
    }

    private float animLeftX = Float.NaN, animLeftY = Float.NaN;
    private float animRightX = Float.NaN, animRightY = Float.NaN;

    public void setHandleMoveAnimationEnabled(boolean enabled) {
        handleMoveAnimationEnabled = enabled;
    }

    public void setFastDragAnimationActive(boolean active) {
        fastDragAnimationActive = active;
    }

    public boolean isAnimating() {
        if (!handleMoveAnimationEnabled) return false;
        long now = SystemClock.uptimeMillis();
        boolean leftActive = !Float.isNaN(leftTargetX) && (now - leftStartTime < leftAnimDuration);
        boolean rightActive = !Float.isNaN(rightTargetX) && (now - rightStartTime < rightAnimDuration);
        return leftActive || rightActive;
    }

    public void resetAnimationState() {
        animLeftX = Float.NaN;
        animLeftY = Float.NaN;
        animRightX = Float.NaN;
        animRightY = Float.NaN;
        leftStartX = Float.NaN;
        rightStartX = Float.NaN;
        leftAnimDuration = ANIM_DURATION;
        rightAnimDuration = ANIM_DURATION;
    }
}
