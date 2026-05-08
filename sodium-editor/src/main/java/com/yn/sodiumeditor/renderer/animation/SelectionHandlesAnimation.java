package com.yn.sodiumeditor.renderer.animation;

import android.os.SystemClock;
import com.yn.sodiumeditor.utils.FunctionLog;

/**
 * SelectionHandlesAnimation handles the animation logic for selection handles.
 */
public class SelectionHandlesAnimation {
    private static final String SELECTION_HANDLE_DBG = "SelectionHandleDbg";

    // Animation duration in ms
    private static final long ANIM_DURATION = 120;

    public boolean handleMoveAnimationEnabled = false;
    
    private float leftStartX = Float.NaN, leftStartY = Float.NaN;
    private float leftTargetX = Float.NaN, leftTargetY = Float.NaN;
    private long leftStartTime = 0;
    
    private float rightStartX = Float.NaN, rightStartY = Float.NaN;
    private float rightTargetX = Float.NaN, rightTargetY = Float.NaN;
    private long rightStartTime = 0;

    public float[] getAnimatedHandlePosition(boolean isLeft, float targetX, float targetY) {
        FunctionLog.f("SelectionHandlesAnimation", "getAnimatedHandlePosition", isLeft, targetX, targetY);
        if (!handleMoveAnimationEnabled) {
            android.util.Log.i(
                SELECTION_HANDLE_DBG,
                "anim bypass side="
                    + (isLeft ? "left" : "right")
                    + " targetX="
                    + targetX
                    + " targetY="
                    + targetY);
            return new float[] {targetX, targetY};
        }
        
        long now = SystemClock.uptimeMillis();
        float curStartX = isLeft ? leftStartX : rightStartX;
        float curTargetX = isLeft ? leftTargetX : rightTargetX;
        float curTargetY = isLeft ? leftTargetY : rightTargetY;
        long curStartTime = isLeft ? leftStartTime : rightStartTime;

        if (Float.isNaN(curStartX) || curTargetX != targetX || curTargetY != targetY) {
            // Target changed or first time
            if (isLeft) {
                leftStartX = Float.isNaN(animLeftX) ? targetX : animLeftX;
                leftStartY = Float.isNaN(animLeftY) ? targetY : animLeftY;
                leftTargetX = targetX;
                leftTargetY = targetY;
                leftStartTime = now;
            } else {
                rightStartX = Float.isNaN(animRightX) ? targetX : animRightX;
                rightStartY = Float.isNaN(animRightY) ? targetY : animRightY;
                rightTargetX = targetX;
                rightTargetY = targetY;
                rightStartTime = now;
            }
            curStartX = isLeft ? leftStartX : rightStartX;
            curStartTime = now;
        }

        float t = Math.min(1f, (float)(now - curStartTime) / ANIM_DURATION);
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
        android.util.Log.i(
            SELECTION_HANDLE_DBG,
            "anim side="
                + (isLeft ? "left" : "right")
                + " targetX="
                + targetX
                + " targetY="
                + targetY
                + " drawX="
                + ax
                + " drawY="
                + ay
                + " t="
                + t
                + " enabled="
                + handleMoveAnimationEnabled);
        
        return new float[] {ax, ay};
    }

    private float animLeftX = Float.NaN, animLeftY = Float.NaN;
    private float animRightX = Float.NaN, animRightY = Float.NaN;

    public void setHandleMoveAnimationEnabled(boolean enabled) {
        FunctionLog.f("SelectionHandlesAnimation", "setHandleMoveAnimationEnabled", enabled);
        handleMoveAnimationEnabled = enabled;
    }

    public boolean isAnimating() {
        FunctionLog.f("SelectionHandlesAnimation", "isAnimating");
        if (!handleMoveAnimationEnabled) return false;
        long now = SystemClock.uptimeMillis();
        boolean leftActive = !Float.isNaN(leftTargetX) && (now - leftStartTime < ANIM_DURATION);
        boolean rightActive = !Float.isNaN(rightTargetX) && (now - rightStartTime < ANIM_DURATION);
        return leftActive || rightActive;
    }

    public void resetAnimationState() {
        FunctionLog.f("SelectionHandlesAnimation", "resetAnimationState");
        animLeftX = Float.NaN;
        animLeftY = Float.NaN;
        animRightX = Float.NaN;
        animRightY = Float.NaN;
        leftStartX = Float.NaN;
        rightStartX = Float.NaN;
    }
}
