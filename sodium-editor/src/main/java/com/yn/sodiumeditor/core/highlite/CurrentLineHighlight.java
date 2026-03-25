package com.yn.sodiumeditor.core.highlite;
import com.yn.sodiumeditor.SodiumEditor;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import com.yn.sodiumeditor.core.WordWrap;

/**
 * Manages current line highlighting for the SodiumEditor.
 */
public class CurrentLineHighlight {

    // --- Current Line Highlight State ---
    public boolean highlightCurrentLine = true;
    public boolean highlightCurrentLineInGutter = true;
    public int currentLineHighlightColor = 0x302196F3; // Default: translucent blue
    public final Paint currentLinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final SodiumEditor editor;

    public CurrentLineHighlight(SodiumEditor editor) {
        this.editor = editor;
        currentLinePaint.setStyle(Paint.Style.FILL);
        currentLinePaint.setColor(currentLineHighlightColor);
    }

    /**
     * Enable or disable current line highlighting.
     */
    public void setHighlightCurrentLine(boolean enabled) {
        if (this.highlightCurrentLine == enabled) return;
        this.highlightCurrentLine = enabled;
        if (!enabled && highlightCurrentLineInGutter) {
            highlightCurrentLineInGutter = false;
        }
        editor.invalidate();
    }

    /**
     * Check if current line highlighting is enabled.
     */
    public boolean isHighlightCurrentLineEnabled() {
        return highlightCurrentLine;
    }

    /**
     * Set the highlight color for the current line.
     */
    public void setCurrentLineHighlightColor(int color) {
        if (this.currentLineHighlightColor == color) return;
        this.currentLineHighlightColor = color;
        currentLinePaint.setColor(color);
        if (highlightCurrentLine) editor.invalidate();
    }

    /**
     * Get the current line highlight color.
     */
    public int getCurrentLineHighlightColor() {
        return currentLineHighlightColor;
    }

    /**
     * Enable or disable current line highlight in the gutter.
     */
    public void setCurrentLineGutterHighlightEnabled(boolean enabled) {
        if (!highlightCurrentLine && enabled) {
            enabled = false;
        }
        if (highlightCurrentLineInGutter == enabled) return;
        highlightCurrentLineInGutter = enabled;
        if (editor.lineNumber.showLineNumbers) editor.invalidate();
    }

    /**
     * Check if current line gutter highlighting is enabled.
     */
    public boolean isCurrentLineGutterHighlightEnabled() {
        return highlightCurrentLineInGutter;
    }

    /**
     * Draw the current line highlight in the gutter area.
     */
    public void drawCurrentLineHighlightInGutter(Canvas canvas, float top, float bottom) {
        if (!editor.lineNumber.showLineNumbers || !highlightCurrentLineInGutter || editor.lineNumber.lineNumbersGutterWidth <= 0f) return;
        float left = editor.lineNumber.getGutterStartX();
        float right = left + editor.lineNumber.lineNumbersGutterWidth;
        float sep = editor.lineNumber.gutterSeparatorWidth;
        if (sep > 0f) {
            if (editor.textRender.isRtl) {
                left = Math.min(right, left + sep);
            } else {
                right = Math.max(left, right - sep);
            }
        }
        if (right <= left) return;
        canvas.drawRect(left, top, right, bottom, currentLinePaint);
    }

    /**
     * Draw the current line highlight for unwrapped text.
     */
    public void drawCurrentLineHighlightUnwrapped(Canvas canvas, int firstVisibleIndex, int lastVisibleIndex, int firstVisibleLine, int lastVisibleLine) {
        if (!highlightCurrentLine || editor.selection.hasSelection) return;

        int globalLine = editor.cursor.cursorLine;
        int visibleIndex = editor.codeFold.isCodeFoldingEnabled ? editor.codeFold.getVisibleIndexForGlobalLine(globalLine) : globalLine;

        if (visibleIndex < firstVisibleIndex || visibleIndex > lastVisibleIndex) return;
        if (editor.codeFold.isCodeFoldingEnabled && editor.codeFold.isLineHiddenByFold(globalLine)) return;

        float top = visibleIndex * editor.textRender.lineHeight - editor.scroll.scrollY;
        float bottom = top + editor.textRender.lineHeight;

        float viewLeft = editor.textRender.isRtl ? 0f : editor.lineNumber.lineNumbersGutterWidth;
        float viewRight = editor.textRender.isRtl ? (editor.getWidth() - editor.lineNumber.lineNumbersGutterWidth) : editor.getWidth();

        canvas.drawRect(viewLeft, top, viewRight, bottom, currentLinePaint);
    }

    /**
     * Draw the current line highlight for wrapped text.
     */
    public void drawCurrentLineHighlightWrapped(Canvas canvas, int firstVisualIndex, int lastVisualIndex) {
        if (!highlightCurrentLine || editor.selection.hasSelection) return;

        int cursorLine = editor.cursor.cursorLine;

        for (int v = firstVisualIndex; v <= lastVisualIndex; v++) {
            WordWrap.VisualLinePosition pos = editor.wordWrap.getVisualPositionForIndex(v);
            if (pos.line != cursorLine) continue;

            float top = v * editor.textRender.lineHeight - editor.scroll.scrollY;
            float bottom = top + editor.textRender.lineHeight;

            canvas.drawRect(-editor.textRender.paddingLeft, top, Math.max(editor.wordWrap.getWrapWidth(), editor.getWidth()), bottom, currentLinePaint);
        }
    }

    /**
     * Draw the current line highlight for a specific line segment.
     */
    public void drawCurrentLineHighlightSegment(Canvas canvas, float left, float top, float right, float bottom) {
        if (!highlightCurrentLine || editor.selection.hasSelection) return;
        canvas.drawRect(left, top, right, bottom, currentLinePaint);
    }
}
