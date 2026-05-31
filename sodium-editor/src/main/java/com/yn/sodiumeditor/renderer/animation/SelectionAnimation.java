package com.yn.sodiumeditor.renderer.animation;

import android.animation.ValueAnimator;
import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.view.animation.PathInterpolator;
import com.yn.sodiumeditor.SodiumEditor;

/**
 * SelectionAnimation handles the fade in/out animation for selection
 * highlights and handles.
 */
public class SelectionAnimation {
    private static final long FADE_DURATION_MS = 220L;
    private static final long SMART_GEOMETRY_DURATION_MS = 220L;

    private final SodiumEditor editor;
    private final PathInterpolator smoothInterpolator = new PathInterpolator(0.4f, 0f, 0.2f, 1f);

    // Animation state
    public boolean selectionAnimationEnabled = true;
    public float selectionAlpha = 1f;
    public float handleAlpha = 1f;
    public float geometryProgress = 1f;
    public int fadeOutStartLine = 0;
    public int fadeOutStartChar = 0;
    public int fadeOutEndLine = 0;
    public int fadeOutEndChar = 0;
    private ValueAnimator selectionFadeAnimator;
    private ValueAnimator smartGeometryAnimator;
    private boolean lastHasSelection = false;
    private boolean drawingFadeOutSelection = false;

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
            if (smartGeometryAnimator != null) {
                smartGeometryAnimator.cancel();
                smartGeometryAnimator = null;
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
        if (nowHasSelection) {
            drawingFadeOutSelection = false;
        } else {
            captureFadeOutSelection();
            drawingFadeOutSelection = true;
        }
        if (!selectionAnimationEnabled) {
            selectionAlpha = nowHasSelection ? 1f : 0f;
            handleAlpha = nowHasSelection ? 1f : 0f;
            geometryProgress = 1f;
            drawingFadeOutSelection = false;
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
        selectionFadeAnimator.addListener(
                new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        if (!lastHasSelection) {
                            drawingFadeOutSelection = false;
                            editor.invalidate();
                        }
                    }
                });
        selectionFadeAnimator.start();
    }

    public void updateSelectionGeometry(boolean hasSelection, boolean geometryChanged) {
        geometryProgress = 1f;
    }

    public void startSmartSelectionGeometryAnimation() {
        if (!selectionAnimationEnabled) {
            geometryProgress = 1f;
            return;
        }
        if (smartGeometryAnimator != null) {
            smartGeometryAnimator.cancel();
        }
        geometryProgress = 0.68f;
        smartGeometryAnimator = ValueAnimator.ofFloat(geometryProgress, 1f);
        smartGeometryAnimator.setDuration(SMART_GEOMETRY_DURATION_MS);
        smartGeometryAnimator.setInterpolator(smoothInterpolator);
        smartGeometryAnimator.addUpdateListener(
                a -> {
                    geometryProgress = (float) a.getAnimatedValue();
                    editor.invalidate();
                });
        smartGeometryAnimator.start();
    }

    /**
     * Cancel any running animation.
     */
    public void cancelAnimation() {
        if (selectionFadeAnimator != null) {
            selectionFadeAnimator.cancel();
        }
        if (smartGeometryAnimator != null) {
            smartGeometryAnimator.cancel();
        }
    }

    /**
     * Check if animation is currently running.
     */
    public boolean isAnimating() {
        return (selectionFadeAnimator != null && selectionFadeAnimator.isRunning())
                || (smartGeometryAnimator != null && smartGeometryAnimator.isRunning());
    }

    public boolean shouldDrawSelectionHighlight() {
        return editor.selection.hasSelection || (drawingFadeOutSelection && selectionAlpha > 0f);
    }

    public boolean isDrawingFadeOutSelection() {
        return drawingFadeOutSelection && !editor.selection.hasSelection;
    }

    private void captureFadeOutSelection() {
        fadeOutStartLine = editor.selection.selStartLine;
        fadeOutStartChar = editor.selection.selStartChar;
        fadeOutEndLine = editor.selection.selEndLine;
        fadeOutEndChar = editor.selection.selEndChar;
    }
}
