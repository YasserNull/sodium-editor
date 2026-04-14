package com.yn.sodiumeditor.renderer;

import android.graphics.Canvas;
import android.graphics.Paint;
import com.yn.sodiumeditor.SodiumEditor;
import com.yn.sodiumeditor.core.LineNumber;
import com.yn.sodiumeditor.core.WordWrap;

/**
 * LineNumberRender handles all line number rendering logic for SodiumEditor.
 * This includes:
 * - Drawing line numbers (cached and direct)
 * - Drawing current line number highlight
 * - Writing integers to char buffers
 */
public class LineNumberRender {
    private final SodiumEditor editor;
    private final LineNumber lineNumber;

    public LineNumberRender(SodiumEditor editor, LineNumber lineNumber) {
        this.editor = editor;
        this.lineNumber = lineNumber;
    }

    public void drawLineNumbersDirectUnwrapped(Canvas canvas, int firstIdx, int lastIdx, int firstLine, int lastLine) {
        float lineNumX = getLineNumXUnwrapped();
        if (editor.codeFold.isCodeFoldingEnabled) {
            for (int v = firstIdx; v <= lastIdx; v++) {
                int i = editor.codeFold.mapVisibleIndexToGlobal(v);
                if (i == editor.cursor.cursorLine) continue;
                drawSingleLineNumber(canvas, i + 1, lineNumX, v * editor.textRender.lineHeight - editor.scroll.scrollY);
            }
        } else {
            for (int i = firstLine; i <= lastLine; i++) {
                if (i == editor.cursor.cursorLine) continue;
                drawSingleLineNumber(canvas, i + 1, lineNumX, i * editor.textRender.lineHeight - editor.scroll.scrollY);
            }
        }
        lineNumber.drawCurrentLineNumberUnwrapped(canvas, firstIdx, lastIdx);
    }

    public void drawLineNumbersDirectWrapped(Canvas canvas, int firstV, int lastV) {
        float lineNumX = getLineNumXWrapped();
        for (int v = firstV; v <= lastV; v++) {
            WordWrap.VisualLinePosition pos = editor.wordWrap.getVisualPositionForIndex(v);
            if (pos.segment != 0 || pos.line == editor.cursor.cursorLine) continue;
            drawSingleLineNumber(canvas, pos.line + 1, lineNumX, v * editor.textRender.lineHeight - editor.scroll.scrollY);
        }
        lineNumber.drawCurrentLineNumberWrapped(canvas, firstV, lastV);
    }

    public void drawCurrentLineHighlightInGutter(Canvas canvas, float top, float bottom) {
        if (!lineNumber.showLineNumbers || !lineNumber.highlightCurrentLineInGutter || lineNumber.lineNumbersGutterWidth <= 0f || editor.selection.hasSelection) return;
        float left = lineNumber.getGutterStartX();
        float right = left + lineNumber.lineNumbersGutterWidth;
        float sep = lineNumber.gutterSeparatorWidth;
        if (sep > 0f) {
            if (editor.textRender.isRtl) left = Math.min(right, left + sep);
            else right = Math.max(left, right - sep);
        }
        if (right > left) canvas.drawRect(left, top, right, bottom, editor.currentLineHighlight.currentLinePaint);
    }

    public void drawSingleLineNumber(Canvas canvas, int number, float x, float yOffset) {
        int start = lineNumber.utils.writeIntToChars(number, lineNumber.lineNumberChars);
        float y = Math.round(yOffset + editor.textRender.lineHeight - editor.textRender.paint.descent());
        canvas.drawText(lineNumber.lineNumberChars, start, lineNumber.lineNumberChars.length - start, x, y, lineNumber.lineNumbersPaint);
    }

    public float getLineNumXUnwrapped() {
        boolean rtl = editor.textRender.isRtl;
        float markerW = editor.codeFold.isCodeFoldingEnabled ? editor.codeFold.animation.foldMarkerGutterWidth : 0f;
        return rtl ? lineNumber.getGutterStartX() + LineNumber.GUTTER_TEXT_PADDING + markerW
                   : lineNumber.getGutterStartX() + lineNumber.lineNumbersGutterWidth - markerW - LineNumber.GUTTER_TEXT_PADDING;
    }

    public float getLineNumXWrapped() {
        return editor.textRender.isRtl ? lineNumber.getGutterStartX() + LineNumber.GUTTER_TEXT_PADDING
                                       : lineNumber.getGutterStartX() + lineNumber.lineNumbersGutterWidth - LineNumber.GUTTER_TEXT_PADDING;
    }
}
