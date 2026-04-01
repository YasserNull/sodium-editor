package com.yn.sodiumeditor.core.highlite;

import android.animation.ValueAnimator;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.animation.PathInterpolator;
import com.yn.sodiumeditor.SodiumEditor;
import com.yn.sodiumeditor.core.WordWrap;

/**
 * Manages current line highlighting for the SodiumEditor.
 * Enhanced with ultra-smooth sliding animation.
 */
public class CurrentLineHighlight {

    public boolean highlightCurrentLine = true;
    public boolean highlightCurrentLineInGutter = true;
    public boolean isCurrentLineAnimationEnabled = true; 
    
    public int currentLineHighlightColor = 0x302196F3;
    public final Paint currentLinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final SodiumEditor editor;
    
    // Animation state
    private float animatedVisualIndex = -1f;
    private float lastTargetIndex = -1f;
    private ValueAnimator lineAnimator;
    
    // Smooth interpolator (Bezier curve for material motion)
    private final PathInterpolator smoothInterpolator = new PathInterpolator(0.4f, 0f, 0.2f, 1f);

    public CurrentLineHighlight(SodiumEditor editor) {
        this.editor = editor;
        currentLinePaint.setStyle(Paint.Style.FILL);
        currentLinePaint.setColor(currentLineHighlightColor);
    }

    public void setHighlightCurrentLine(boolean enabled) {
        if (this.highlightCurrentLine == enabled) return;
        this.highlightCurrentLine = enabled;
        editor.invalidate();
    }

    public void setCurrentLineHighlightColor(int color) {
        if (this.currentLineHighlightColor == color) return;
        this.currentLineHighlightColor = color;
        currentLinePaint.setColor(color);
        if (highlightCurrentLine) editor.invalidate();
    }

    public void setCurrentLineGutterHighlightEnabled(boolean enabled) {
        if (highlightCurrentLineInGutter == enabled) return;
        highlightCurrentLineInGutter = enabled;
        if (editor.lineNumber.showLineNumbers) editor.invalidate();
    }

    public void setAnimationEnabled(boolean enabled) {
        this.isCurrentLineAnimationEnabled = enabled;
    }

    private float getTargetVisualIndex() {
        if (editor.wordWrap.isWordWrapEnabled) {
            return editor.getVisualIndexForLineAndChar(editor.cursor.cursorLine, 0);
        } else if (editor.codeFold.isCodeFoldingEnabled) {
            return editor.codeFold.getVisibleIndexForGlobalLine(editor.cursor.cursorLine);
        } else {
            return editor.cursor.cursorLine;
        }
    }

    private void checkAndStartAnimation() {
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
        lineAnimator.setDuration(duration); // Faster duration
        editor.cursorAnimation.setAnimationDurationMs(duration);
        lineAnimator.setInterpolator(smoothInterpolator);
        lineAnimator.addUpdateListener(animation -> {
            animatedVisualIndex = (float) animation.getAnimatedValue();
            editor.postInvalidateOnAnimation();
        });
        lineAnimator.start();
    }

    public float getAnimatedVisualIndex() {
        checkAndStartAnimation();
        return animatedVisualIndex;
    }

    public void drawCurrentLineHighlightInGutter(Canvas canvas, float top, float bottom) {
        if (!editor.lineNumber.showLineNumbers || !highlightCurrentLineInGutter || editor.selection.hasSelection) return;
        
        checkAndStartAnimation();
        
        float yOffset = animatedVisualIndex * editor.textRender.lineHeight - editor.scroll.scrollY;
        float aTop = yOffset;
        float aBottom = aTop + editor.textRender.lineHeight;

        float left = editor.lineNumber.getGutterStartX();
        float right = left + editor.lineNumber.lineNumbersGutterWidth;
        canvas.drawRect(left, aTop, right, aBottom, currentLinePaint);
    }

    public void drawCurrentLineHighlightUnwrapped(Canvas canvas, int firstVisibleIndex, int lastVisibleIndex, int firstVisibleLine, int lastVisibleLine) {
        if (!highlightCurrentLine || editor.selection.hasSelection) return;

        checkAndStartAnimation();
        
        float top = animatedVisualIndex * editor.textRender.lineHeight - editor.scroll.scrollY;
        float bottom = top + editor.textRender.lineHeight;

        float viewLeft = editor.textRender.isRtl ? 0f : editor.lineNumber.lineNumbersGutterWidth;
        float viewRight = editor.textRender.isRtl ? (editor.getWidth() - editor.lineNumber.lineNumbersGutterWidth) : editor.getWidth();

        canvas.drawRect(viewLeft, top, viewRight, bottom, currentLinePaint);
    }

    public void drawCurrentLineHighlightWrapped(Canvas canvas, int firstVisualIndex, int lastVisualIndex) {
        if (!highlightCurrentLine || editor.selection.hasSelection) return;

        checkAndStartAnimation();

        float top = animatedVisualIndex * editor.textRender.lineHeight - editor.scroll.scrollY;
        float bottom = top + editor.textRender.lineHeight;

        canvas.drawRect(-editor.textRender.paddingLeft, top, Math.max(editor.wordWrap.getWrapWidth(), editor.getWidth()), bottom, currentLinePaint);
    }

    public void drawCurrentLineHighlightSegment(Canvas canvas, float left, float top, float right, float bottom) {
        if (!highlightCurrentLine || editor.selection.hasSelection) return;
        canvas.drawRect(left, top, right, bottom, currentLinePaint);
    }
}
