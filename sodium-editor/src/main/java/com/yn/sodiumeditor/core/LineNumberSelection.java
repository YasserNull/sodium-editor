package com.yn.sodiumeditor.core;

import com.yn.sodiumeditor.SodiumEditor;

/**
 * Handles line selection interactions through the gutter.
 */
public class LineNumberSelection {
    private final SodiumEditor editor;
    private final LineNumber lineNumber;

    public LineNumberSelection(SodiumEditor editor, LineNumber lineNumber) {
        this.editor = editor;
        this.lineNumber = lineNumber;
    }

    public boolean isInLineNumberGutter(float x) {
        if (!lineNumber.showLineNumbers || lineNumber.lineNumbersGutterWidth <= 0f) return false;
        float start = lineNumber.getGutterStartX();
        return x >= start && x <= start + lineNumber.lineNumbersGutterWidth;
    }

    public void beginLineNumberSelection(int line) {
        int total = editor.getLinesCount();
        if (total <= 0) return;
        int clamped = Math.max(0, Math.min(line, total - 1));
        
        editor.selection.state.isLineNumberSelecting = true;
        editor.selection.state.lineNumberSelectAnchorLine = clamped;
        editor.selection.syncFromState();
        
        String text = editor.textRender.getLineTextForRender(clamped);
        if (text != null) editor.selection.setSelectionInternal(clamped, 0, clamped, text.length());
        editor.invalidate();
    }

    public void updateLineNumberSelection(int line) {
        if (!editor.selection.state.isLineNumberSelecting) return;
        int total = editor.getLinesCount();
        if (total <= 0) return;
        int clamped = Math.max(0, Math.min(line, total - 1));
        
        int anchor = editor.selection.state.lineNumberSelectAnchorLine;
        int startLine = Math.min(anchor, clamped);
        int endLine = Math.max(anchor, clamped);
        
        String endText = editor.textRender.getLineTextForRender(endLine);
        editor.selection.setSelectionInternal(startLine, 0, endLine, (endText != null ? endText.length() : 0));
        editor.invalidate();
    }

    public void endLineNumberSelection() {
        editor.selection.state.isLineNumberSelecting = false;
        editor.selection.state.lineNumberSelectAnchorLine = -1;
        editor.selection.syncFromState();
    }
}
