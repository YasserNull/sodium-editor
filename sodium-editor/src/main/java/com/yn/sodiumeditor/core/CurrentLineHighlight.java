package com.yn.sodiumeditor.core;

import android.graphics.Canvas;
import android.graphics.Paint;
import com.yn.sodiumeditor.SodiumEditor;
import com.yn.sodiumeditor.core.WordWrap;
import com.yn.sodiumeditor.renderer.animation.CurrentLineHighlightAnimation;

/**
 * Manages current line highlighting for the SodiumEditor.
 * Enhanced with ultra-smooth sliding animation.
 */
public class CurrentLineHighlight {

    public boolean highlightCurrentLine = true;
    public boolean highlightCurrentLineInGutter = true;

    public int currentLineHighlightColor = 0x302196F3;
    public final Paint currentLinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    // Animation delegate
    public final CurrentLineHighlightAnimation animation;

    private final SodiumEditor editor;

    public CurrentLineHighlight(SodiumEditor editor) {
        this.editor = editor;
        this.animation = new CurrentLineHighlightAnimation(editor);
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
        animation.setAnimationEnabled(enabled);
    }

    public float getAnimatedVisualIndex() {
        return animation.getAnimatedVisualIndex();
    }

    public void drawCurrentLineHighlightInGutter(Canvas canvas, float top, float bottom) {
        if (!editor.lineNumber.showLineNumbers || !highlightCurrentLineInGutter || editor.selection.hasSelection) return;

        animation.checkAndStartAnimation();

        float yOffset = animation.getAnimatedVisualIndex() * editor.textRender.lineHeight - editor.scroll.scrollY;
        float aTop = yOffset;
        float aBottom = aTop + editor.textRender.lineHeight;

        float left = editor.lineNumber.getGutterStartX();
        float right = left + editor.lineNumber.lineNumbersGutterWidth;
        canvas.drawRect(left, aTop, right, aBottom, currentLinePaint);
    }

    public void drawCurrentLineHighlightUnwrapped(Canvas canvas, int firstVisibleIndex, int lastVisibleIndex, int firstVisibleLine, int lastVisibleLine) {
        if (!highlightCurrentLine || editor.selection.hasSelection) return;

        animation.checkAndStartAnimation();

        float top = animation.getAnimatedVisualIndex() * editor.textRender.lineHeight - editor.scroll.scrollY;
        float bottom = top + editor.textRender.lineHeight;

        float viewLeft = editor.textRender.isRtl ? 0f : editor.lineNumber.lineNumbersGutterWidth;
        float viewRight = editor.textRender.isRtl ? (editor.getWidth() - editor.lineNumber.lineNumbersGutterWidth) : editor.getWidth();

        canvas.drawRect(viewLeft, top, viewRight, bottom, currentLinePaint);
    }

    public void drawCurrentLineHighlightWrapped(Canvas canvas, int firstVisualIndex, int lastVisualIndex) {
        if (!highlightCurrentLine || editor.selection.hasSelection) return;

        animation.checkAndStartAnimation();

        float top = animation.getAnimatedVisualIndex() * editor.textRender.lineHeight - editor.scroll.scrollY;
        float bottom = top + editor.textRender.lineHeight;

        canvas.drawRect(-editor.textRender.paddingLeft, top, Math.max(editor.wordWrap.getWrapWidth(), editor.getWidth()), bottom, currentLinePaint);
    }

    public void drawCurrentLineHighlightSegment(Canvas canvas, float left, float top, float right, float bottom) {
        if (!highlightCurrentLine || editor.selection.hasSelection) return;
        canvas.drawRect(left, top, right, bottom, currentLinePaint);
    }
}
