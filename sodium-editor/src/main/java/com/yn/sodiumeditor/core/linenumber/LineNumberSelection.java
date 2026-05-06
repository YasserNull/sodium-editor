package com.yn.sodiumeditor.core.linenumber;

import android.util.Log;
import com.yn.sodiumeditor.SodiumEditor;
import com.yn.sodiumeditor.utils.FunctionLog;

/**
 * Handles line selection interactions through the gutter.
 */
public class LineNumberSelection {
    private final SodiumEditor editor;
    private final LineNumber lineNumber;

    public LineNumberSelection(SodiumEditor editor, LineNumber lineNumber) {
        FunctionLog.f("LineNumberSelection", "LineNumberSelection", editor, lineNumber);
        this.editor = editor;
        this.lineNumber = lineNumber;
    }

    public boolean isInLineNumberGutter(float x) {
        FunctionLog.f("LineNumberSelection", "isInLineNumberGutter", x);
        if (!lineNumber.showLineNumbers || lineNumber.lineNumbersGutterWidth <= 0f) return false;
        float start = lineNumber.getGutterStartX();
        return x >= start && x <= start + lineNumber.lineNumbersGutterWidth;
    }

    public void beginLineNumberSelection(int line) {
        FunctionLog.f("LineNumberSelection", "beginLineNumberSelection", line);
        int total = editor.view.getLinesCount();
        if (total <= 0) return;
        int clamped = Math.max(0, Math.min(line, total - 1));
        
        editor.selection.state.isLineNumberSelecting = true;
        editor.selection.state.lineNumberSelectAnchorLine = clamped;
        editor.selection.syncFromState();

        applyWholeLineSelection(clamped, clamped, total);
        editor.invalidate();
    }

    public void updateLineNumberSelection(int line) {
        FunctionLog.f("LineNumberSelection", "updateLineNumberSelection", line);
        if (!editor.selection.state.isLineNumberSelecting) return;
        int total = editor.view.getLinesCount();
        if (total <= 0) return;
        int clamped = Math.max(0, Math.min(line, total - 1));
        
        int anchor = editor.selection.state.lineNumberSelectAnchorLine;
        int startLine = Math.min(anchor, clamped);
        int endLine = Math.max(anchor, clamped);

        applyWholeLineSelection(startLine, endLine, total);
        editor.invalidate();
    }

    public void endLineNumberSelection() {
        FunctionLog.f("LineNumberSelection", "endLineNumberSelection");
        editor.selection.state.isLineNumberSelecting = false;
        editor.selection.state.lineNumberSelectAnchorLine = -1;
        editor.selection.syncFromState();
    }

    private void applyWholeLineSelection(int startLine, int endLine, int totalLines) {
        int safeStart = Math.max(0, Math.min(startLine, totalLines - 1));
        int safeEnd = Math.max(safeStart, Math.min(endLine, totalLines - 1));

        if (safeEnd < totalLines - 1) {
            editor.selection.setSelectionInternal(safeStart, 0, safeEnd + 1, 0);
            Log.i(
                    "LineNumber",
                    "applyWholeLineSelection forward startLine="
                            + safeStart
                            + " endLine="
                            + safeEnd
                            + " selectEndLine="
                            + (safeEnd + 1)
                            + " selectEndChar=0");
            return;
        }

        if (safeStart > 0) {
            String prevText = editor.windowRender.getLineTextForRender(safeStart - 1);
            int prevLen = (prevText != null) ? prevText.length() : 0;
            String endText = editor.windowRender.getLineTextForRender(safeEnd);
            int endLen = (endText != null) ? endText.length() : 0;
            editor.selection.setSelectionInternal(safeStart - 1, prevLen, safeEnd, endLen);
            Log.i(
                    "LineNumber",
                    "applyWholeLineSelection lastLine startLine="
                            + safeStart
                            + " endLine="
                            + safeEnd
                            + " selectStartLine="
                            + (safeStart - 1)
                            + " selectStartChar="
                            + prevLen
                            + " selectEndChar="
                            + endLen);
            return;
        }

        String endText = editor.windowRender.getLineTextForRender(safeEnd);
        editor.selection.setSelectionInternal(safeStart, 0, safeEnd, (endText != null ? endText.length() : 0));
        Log.i(
                "LineNumber",
                "applyWholeLineSelection singleLine startLine="
                        + safeStart
                        + " endLine="
                        + safeEnd);
    }
}
