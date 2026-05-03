package com.yn.sodiumeditor.renderer.animation;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.graphics.Paint;
import android.view.animation.DecelerateInterpolator;
import com.yn.sodiumeditor.SodiumEditor;
import com.yn.sodiumeditor.utils.FunctionLog;

/**
 * Manages fold ripple animations and appearance settings for code folding.
 */
public class CodeFoldAnimation {

    private final SodiumEditor editor;

    // Fold marker appearance
    public float foldMarkerGutterWidth = 0f;
    public float foldMarkerTextScale = 1f;
    public float foldMarkerSizeMultiplier = 1.1f;
    public float foldMarkerSpacing = 0f;
    public float foldMarkerEdgePadding = 4f;
    public float foldPlaceholderCorner = 3f;
    public float foldPlaceholderPadX = 3f;
    public float foldPlaceholderPadY = 2f;
    public int foldMarkerColor = 0xFF2196F3;
    public int foldMarkerPendingColor = 0xFFFFA000;
    public final Paint foldPlaceholderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    public final Paint foldMarkerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    public final Paint foldMarkerPendingPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    public final Paint foldRipplePaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    // Fold ripple animation
    public ValueAnimator foldRippleAnimator;
    public int foldRippleLine = -1;
    public float foldRippleRadius = 0f;
    public float foldRippleAlpha = 0f;
    public float foldRippleMaxRadius = 0f;
    public ValueAnimator foldPlaceholderRippleAnimator;
    public int foldPlaceholderRippleLine = -1;
    public float foldPlaceholderRippleRadius = 0f;
    public float foldPlaceholderRippleAlpha = 0f;
    public float foldPlaceholderRippleMaxRadius = 0f;
    public float foldPlaceholderRippleLeft = 0f;
    public float foldPlaceholderRippleRight = 0f;

    public CodeFoldAnimation(SodiumEditor editor) {
        FunctionLog.f("CodeFoldAnimation", "CodeFoldAnimation", editor);
        this.editor = editor;

        foldPlaceholderPaint.setColor(0xFFE0E0E0);
        foldPlaceholderPaint.setStyle(Paint.Style.FILL);
        foldMarkerPaint.setColor(foldMarkerColor);
        foldMarkerPaint.setFakeBoldText(true);
        foldMarkerPaint.setTextAlign(editor.textRender.isRtl ? Paint.Align.LEFT : Paint.Align.RIGHT);
        foldMarkerPaint.setTextSize(editor.textRender.paint.getTextSize() * foldMarkerSizeMultiplier);
        foldMarkerPendingPaint.setColor(foldMarkerPendingColor);
        foldMarkerPendingPaint.setFakeBoldText(true);
        foldMarkerPendingPaint.setTextAlign(editor.textRender.isRtl ? Paint.Align.LEFT : Paint.Align.RIGHT);
        foldMarkerPendingPaint.setTextSize(editor.textRender.paint.getTextSize() * foldMarkerSizeMultiplier);
        foldRipplePaint.setStyle(Paint.Style.FILL);
    }

    // ============================================================================
    // Ripple Animations
    // ============================================================================

    /**
     * Start a ripple animation on the fold marker.
     */
    public void startFoldMarkerRipple(int line) {
        FunctionLog.f("CodeFoldAnimation", "startFoldMarkerRipple", line);
        if (!editor.codeFold.isCodeFoldingEnabled || !editor.lineNumber.showLineNumbers) return;
        foldRippleLine = line;
        float gutterWidth = foldMarkerGutterWidth;
        if (gutterWidth <= 0f) {
            gutterWidth = foldMarkerPaint.measureText("v") + foldMarkerSpacing + foldMarkerEdgePadding;
        }
        foldRippleMaxRadius = Math.max(editor.textRender.lineHeight * 0.35f, Math.min(editor.textRender.lineHeight * 0.6f, gutterWidth * 0.6f));
        if (foldRippleAnimator != null) foldRippleAnimator.cancel();
        foldRippleAnimator = ValueAnimator.ofFloat(0f, 1f);
        foldRippleAnimator.setDuration(220);
        foldRippleAnimator.setInterpolator(new DecelerateInterpolator());
        foldRippleAnimator.addUpdateListener(a -> {
            float t = (float) a.getAnimatedValue();
            foldRippleRadius = foldRippleMaxRadius * t;
            foldRippleAlpha = 0.35f * (1f - t);
            editor.invalidate();
        });
        foldRippleAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                foldRippleAlpha = 0f;
                foldRippleLine = -1;
            }
        });
        foldRippleAnimator.start();
    }

    /**
     * Start a ripple animation on the folded placeholder button.
     */
    public void startFoldPlaceholderRipple(int line, float left, float right) {
        FunctionLog.f("CodeFoldAnimation", "startFoldPlaceholderRipple", line, left, right);
        if (!editor.codeFold.isCodeFoldingEnabled) return;
        foldPlaceholderRippleLine = line;
        foldPlaceholderRippleLeft = left;
        foldPlaceholderRippleRight = right;
        float w = Math.max(1f, right - left);
        foldPlaceholderRippleMaxRadius = Math.max(editor.textRender.lineHeight * 0.35f, w * 0.75f);
        if (foldPlaceholderRippleAnimator != null) foldPlaceholderRippleAnimator.cancel();
        foldPlaceholderRippleAnimator = ValueAnimator.ofFloat(0f, 1f);
        foldPlaceholderRippleAnimator.setDuration(220);
        foldPlaceholderRippleAnimator.setInterpolator(new DecelerateInterpolator());
        foldPlaceholderRippleAnimator.addUpdateListener(a -> {
            float t = (float) a.getAnimatedValue();
            foldPlaceholderRippleRadius = foldPlaceholderRippleMaxRadius * t;
            foldPlaceholderRippleAlpha = 0.5f * (1f - t);
            editor.invalidate();
        });
        foldPlaceholderRippleAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                foldPlaceholderRippleAlpha = 0f;
                foldPlaceholderRippleLine = -1;
            }

            @Override
            public void onAnimationCancel(Animator animation) {
                foldPlaceholderRippleAlpha = 0f;
                foldPlaceholderRippleLine = -1;
            }
        });
        foldPlaceholderRippleAnimator.start();
    }

    /**
     * Clear fold ripple animation
     */
    public void clearFoldRipple() {
        FunctionLog.f("CodeFoldAnimation", "clearFoldRipple");
        if (foldRippleAnimator != null) foldRippleAnimator.cancel();
        foldRippleAlpha = 0f;
        foldRippleRadius = 0f;
        foldRippleLine = -1;
        if (foldPlaceholderRippleAnimator != null) foldPlaceholderRippleAnimator.cancel();
        foldPlaceholderRippleAlpha = 0f;
        foldPlaceholderRippleRadius = 0f;
        foldPlaceholderRippleLine = -1;
    }

    // ============================================================================
    // Text Size / Typeface Updates
    // ============================================================================

    /**
     * Update fold marker text size.
     */
    public void updateTextSize(float sizePx) {
        FunctionLog.f("CodeFoldAnimation", "updateTextSize", sizePx);
        foldMarkerPaint.setTextSize(sizePx * foldMarkerTextScale);
        foldMarkerPendingPaint.setTextSize(sizePx * foldMarkerTextScale);
    }

    /**
     * Update fold marker typeface.
     */
    public void updateTypeface(android.graphics.Typeface typeface) {
        FunctionLog.f("CodeFoldAnimation", "updateTypeface", typeface);
        int typefaceStyle = android.graphics.Typeface.NORMAL;
        if (typeface != null) {
            typefaceStyle = typeface.getStyle();
        }
        android.graphics.Typeface finalTypeface = android.graphics.Typeface.create(typeface, typefaceStyle);
        foldMarkerPaint.setTypeface(finalTypeface);
        foldMarkerPendingPaint.setTypeface(finalTypeface);
    }
}
