package com.yn.sodiumeditor.renderer.animation;

import android.animation.ValueAnimator;
import android.view.animation.PathInterpolator;
import com.yn.sodiumeditor.SodiumEditor;
import com.yn.sodiumeditor.utils.FunctionLog;

/**
 * CurrentLineHighlightAnimation handles the smooth sliding animation
 * for the current line highlight indicator.
 */
public class CurrentLineHighlightAnimation {
    private static final long NORMAL_MIN_DURATION_MS = 90L;
    private static final long NORMAL_MAX_DURATION_MS = 180L;
    private static final long DRAG_MIN_DURATION_MS = 16L;
    private static final long DRAG_MAX_DURATION_MS = 72L;
    private static final float NORMAL_DISTANCE_FOR_MIN_DURATION = 12f;
    private static final float DRAG_DISTANCE_FOR_MIN_DURATION = 4f;

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
        FunctionLog.f("CurrentLineHighlightAnimation", "CurrentLineHighlightAnimation", editor);
        this.editor = editor;
    }

    /**
     * Get the target visual index based on cursor position.
     */
    private float getTargetVisualIndex() {
        FunctionLog.f("CurrentLineHighlightAnimation", "getTargetVisualIndex");
        if (editor.wordWrap.isWordWrapEnabled) {
            return (float) editor.wordWrap.getVisualIndexForLineAndChar(editor.cursor.cursorLine, editor.cursor.cursorChar);
        }
        if (editor.codeFold.isCodeFoldingEnabled) {
            int visibleIdx = editor.codeFold.getVisibleIndexForGlobalLine(editor.cursor.cursorLine);
            if (visibleIdx < 0) {
                // If cursor is on a hidden line, highlight the fold start
                com.yn.sodiumeditor.core.fold.CodeFold.FoldRange range = editor.codeFold.getCollapsedRangeContainingLine(editor.cursor.cursorLine);
                if (range != null) {
                    visibleIdx = editor.codeFold.getVisibleIndexForGlobalLine(range.startLine);
                }
            }
            return (float) Math.max(0, visibleIdx);
        }
        return (float) editor.cursor.cursorLine;
    }

    /**
     * Check if animation needs to be started and run it.
     */
    public void checkAndStartAnimation() {
        FunctionLog.f("CurrentLineHighlightAnimation", "checkAndStartAnimation");
        float target = getTargetVisualIndex();
        if (!isCurrentLineAnimationEnabled) {
            cancelAnimation();
            animatedVisualIndex = target;
            lastTargetIndex = target;
            return;
        }
        if (Math.abs(lastTargetIndex - target) > 0.01f) {
            cancelAnimation();
            float start = animatedVisualIndex < 0 ? target : animatedVisualIndex;
            float distance = Math.abs(target - start);
            boolean dragActive =
                    editor.selectionHandles.draggingHandle != 0
                            || editor.onTouch.pointerDown
                            || editor.selection.selecting
                            || editor.selection.longPressSelecting;
            long duration =
                    computeDuration(
                            distance,
                            dragActive ? DRAG_MIN_DURATION_MS : NORMAL_MIN_DURATION_MS,
                            dragActive ? DRAG_MAX_DURATION_MS : NORMAL_MAX_DURATION_MS,
                            dragActive
                                    ? DRAG_DISTANCE_FOR_MIN_DURATION
                                    : NORMAL_DISTANCE_FOR_MIN_DURATION);
            lineAnimator = ValueAnimator.ofFloat(start, target);
            lineAnimator.setDuration(duration);
            lineAnimator.setInterpolator(smoothInterpolator);
            lineAnimator.addUpdateListener(animation -> {
                animatedVisualIndex = (float) animation.getAnimatedValue();
                editor.invalidate();
            });
            lineAnimator.start();
            lastTargetIndex = target;
        }
    }

    private long computeDuration(
            float distance, long minDuration, long maxDuration, float distanceForMinDuration) {
        if (distance <= 0.01f) {
            return minDuration;
        }
        float ratio = Math.min(1f, distance / Math.max(0.01f, distanceForMinDuration));
        long duration = Math.round(maxDuration - ((maxDuration - minDuration) * ratio));
        return Math.max(minDuration, Math.min(maxDuration, duration));
    }
    /**
     * Get the current animated visual index (starts animation if needed).
     */
    public float getAnimatedVisualIndex() {
        FunctionLog.f("CurrentLineHighlightAnimation", "getAnimatedVisualIndex");
        checkAndStartAnimation();
        return animatedVisualIndex;
    }

    /**
     * Enable or disable the animation.
     */
    public void setAnimationEnabled(boolean enabled) {
        FunctionLog.f("CurrentLineHighlightAnimation", "setAnimationEnabled", enabled);
        this.isCurrentLineAnimationEnabled = enabled;
    }

    /**
     * Cancel any running animation.
     */
    public void cancelAnimation() {
        FunctionLog.f("CurrentLineHighlightAnimation", "cancelAnimation");
        if (lineAnimator != null) {
            lineAnimator.cancel();
        }
    }
}
